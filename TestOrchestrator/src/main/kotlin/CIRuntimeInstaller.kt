package com.salesforce

import com.salesforce.util.verbosePrinter
import java.util.concurrent.ConcurrentHashMap

private val installThreads = ConcurrentHashMap<String, Thread>()
private val installErrors = ConcurrentHashMap<String, Exception>()

/**
 * Starts background threads to download/install iOS simulator runtimes that aren't already present.
 * Call early (before app generation) so downloads happen in parallel with other work.
 */
fun startBackgroundRuntimeInstalls(versions: List<String>) {
    // Fetch installed runtimes and available versions once (fast before any sims are booted)
    val runtimesOutput = fetchSimctlRuntimes()
    val xcodesOutput = fetchXcodesRuntimes()

    for (version in versions) {
        val major = version.split(".").first()
        if (installThreads.containsKey(major)) continue

        val alreadyInstalled = isRuntimeInstalled(major, runtimesOutput)
        if (alreadyInstalled) {
            verbosePrinter?.invoke("iOS $major runtime already installed locally.")
            continue
        }

        val latestVersion = findLatestXcodesVersion(major, xcodesOutput)
        if (latestVersion == null) {
            installErrors[major] = Exception("No iOS $major runtimes found via xcodes.")
            continue
        }

        verbosePrinter?.invoke("Starting background install of iOS $latestVersion runtime...")
        val thread = Thread {
            try {
                installRuntime(major, latestVersion)
            } catch (e: Exception) {
                installErrors[major] = e
            }
        }.apply {
            name = "runtime-install-$major"
            isDaemon = true
            start()
        }
        installThreads[major] = thread
    }
}

/**
 * Waits for all background runtime installs to complete.
 * Throws if any install failed.
 */
fun awaitBackgroundRuntimeInstalls(versions: List<String>) {
    for (version in versions) {
        val major = version.split(".").first()
        installThreads[major]?.let { thread ->
            verbosePrinter?.invoke("Waiting for iOS $major runtime install to complete...")
            thread.join()
        }
        installErrors[major]?.let { throw it }
    }
}

private fun installRuntime(major: String, latestVersion: String) {
    // Try xcodes first
    val xcodesProcess = ProcessBuilder("xcodes", "runtimes", "install", "iOS $latestVersion")
        .redirectErrorStream(true).start()
    val xcodesOutput = StringBuilder()
    xcodesProcess.inputStream.bufferedReader().useLines { lines ->
        for (line in lines) {
            verbosePrinter?.invoke(line)
            xcodesOutput.appendLine(line)
        }
    }
    xcodesProcess.waitFor()

    if (isRuntimeInstalled(major, fetchSimctlRuntimes())) {
        verbosePrinter?.invoke("iOS $latestVersion runtime installed successfully via xcodes.")
        return
    }

    // Fallback to xcodebuild -downloadPlatform
    verbosePrinter?.invoke("xcodes did not install the runtime. Falling back to xcodebuild...")
    val xcodebuildProcess = ProcessBuilder(
        "xcodebuild", "-downloadPlatform", "iOS", "-buildVersion", latestVersion
    ).redirectErrorStream(true).start()
    val xcodebuildOutput = StringBuilder()
    xcodebuildProcess.inputStream.bufferedReader().useLines { lines ->
        for (line in lines) {
            verbosePrinter?.invoke(line)
            xcodebuildOutput.appendLine(line)
        }
    }
    val xcodebuildExitCode = xcodebuildProcess.waitFor()

    if (xcodebuildExitCode != 0 || !isRuntimeInstalled(major, fetchSimctlRuntimes())) {
        throw Exception(
            "Failed to install iOS $latestVersion runtime.\n" +
            "xcodes output: $xcodesOutput\n" +
            "xcodebuild output: $xcodebuildOutput"
        )
    }

    verbosePrinter?.invoke("iOS $latestVersion runtime installed successfully via xcodebuild.")
}

fun fetchSimctlRuntimes(): String {
    val process = ProcessBuilder("xcrun", "simctl", "list", "runtimes", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output
}

private fun fetchXcodesRuntimes(): String {
    val process = ProcessBuilder("xcodes", "runtimes")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    verbosePrinter?.invoke("xcodes runtimes output:\n$output")
    return output
}

fun isRuntimeInstalled(majorVersion: String, runtimesOutput: String): Boolean {
    return Regex("""com\.apple\.CoreSimulator\.SimRuntime\.iOS-$majorVersion-""")
        .containsMatchIn(runtimesOutput)
}

private fun findLatestXcodesVersion(requestedMajor: String, xcodesOutput: String): String? {
    val runtimePattern = Regex("""^iOS (\d+[\d.]*)\b""", RegexOption.MULTILINE)
    return runtimePattern.findAll(xcodesOutput)
        .map { it.groupValues[1] }
        .filter { it.split(".").first() == requestedMajor }
        .distinct()
        .sortedWith(compareBy<String>(
            { it.split(".").getOrElse(0) { "0" }.toIntOrNull() ?: 0 },
            { it.split(".").getOrElse(1) { "0" }.toIntOrNull() ?: 0 },
            { it.split(".").getOrElse(2) { "0" }.toIntOrNull() ?: 0 },
        ).reversed())
        .firstOrNull()
}
