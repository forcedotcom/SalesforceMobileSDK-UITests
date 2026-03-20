package com.salesforce

import com.salesforce.util.verbosePrinter

private var installThread: Thread? = null
private var installError: Exception? = null

/**
 * Starts a single background thread to download/install iOS simulator runtimes that aren't
 * already present, installing them sequentially to avoid concurrent rsync failures.
 * Call early (before app generation) so downloads happen in parallel with other work.
 */
fun startBackgroundRuntimeInstalls(versions: List<String>) {
    // Fetch installed runtimes and available versions once (fast before any sims are booted)
    val runtimesOutput = fetchSimctlRuntimes()
    val xcodesOutput = fetchXcodesRuntimes()

    data class PendingInstall(val major: String, val latestVersion: String)

    val pending = mutableListOf<PendingInstall>()
    for (version in versions) {
        val major = version.split(".").first()
        if (pending.any { it.major == major }) continue

        if (isRuntimeInstalled(major, runtimesOutput)) {
            verbosePrinter?.invoke("iOS $major runtime already installed locally.")
            continue
        }

        val latestVersion = findLatestXcodesVersion(major, xcodesOutput)
        if (latestVersion == null) {
            installError = Exception("No iOS $major runtimes found via xcodes.")
            return
        }

        pending.add(PendingInstall(major, latestVersion))
    }

    if (pending.isEmpty()) return

    installThread = Thread {
        for ((major, latestVersion) in pending) {
            try {
                verbosePrinter?.invoke("Installing iOS $latestVersion runtime (sequential)...")
                installRuntime(major, latestVersion)
            } catch (e: Exception) {
                installError = e
                return@Thread
            }
        }
    }.apply {
        name = "runtime-install-sequential"
        isDaemon = true
        start()
    }
}

/**
 * Waits for the background runtime install thread to complete.
 * Throws if any install failed.
 */
fun awaitBackgroundRuntimeInstalls(versions: List<String>) {
    installThread?.let { thread ->
        val majors = versions.map { it.split(".").first() }.distinct()
        verbosePrinter?.invoke("Waiting for iOS runtime installs (${majors.joinToString(", ")}) to complete...")
        thread.join()
    }
    installError?.let { throw it }
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
