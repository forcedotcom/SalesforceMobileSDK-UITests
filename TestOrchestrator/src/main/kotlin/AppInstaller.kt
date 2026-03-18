package com.salesforce

import com.salesforce.Test.Companion.ADB
import com.salesforce.Test.Companion.SIM_NAME
import com.salesforce.util.progressBanner
import com.salesforce.util.runCommand
import com.salesforce.util.verbosePrinter
import java.io.File

fun installAndroidApp(appInfo: AppInfo) {
    // Push config to device so androidTestConfig can load it
    "$ADB push shared/test/android/ui_test_config.json /data/local/tmp/ui_test_config.json".runCommand()
    "$ADB uninstall ${appInfo.packageName}".runCommand()

    progressBanner?.update {
        context = context.advance("Install App")
        completed += 1
    }
    verbosePrinter?.invoke("Installing App")
    "$ADB install -r ${appInfo.apkPath}".runCommand()
}

fun installIosApp(appInfo: AppInfo, iOSVersion: String, iOSDevice: String) {
    // Clean up any existing test simulator
    do {
        val result = "xcrun simctl delete $SIM_NAME".runCommand(suppressErrors = true)
    } while (result == 0)

    progressBanner?.update {
        context = context.advance("Create Simulator")
        completed += 1
    }
    verbosePrinter?.invoke("Creating Simulator")
    val runtime = resolveIosRuntime(iOSVersion)
    verbosePrinter?.invoke("Using runtime: $runtime")
    val createProcess = ProcessBuilder(
        "xcrun", "simctl", "create", SIM_NAME,
        "com.apple.CoreSimulator.SimDeviceType.$iOSDevice",
        runtime
    ).redirectErrorStream(true).start()
    val simId = createProcess.inputStream.bufferedReader().readText().trim()
    val createExitCode = createProcess.waitFor()
    if (createExitCode != 0) {
        throw Exception("Failed to create simulator (exit $createExitCode): $simId")
    }

    progressBanner?.update {
        context = context.advance("Boot Simulator")
        completed += 1
    }
    verbosePrinter?.invoke("Booting Simulator")
    "xcrun simctl boot $simId".runCommand()
    Thread.sleep(3000)

    progressBanner?.update {
        context = context.advance("Install App")
        completed += 1
    }
    verbosePrinter?.invoke("Installing App")
    val buildPath = when {
        File("${appInfo.iosRoot}/DerivedData/Build/").exists() -> "${appInfo.iosRoot}/DerivedData/Build"
        File("${appInfo.iosRoot}/Build/").exists() -> "${appInfo.iosRoot}/Build"
        else -> throw Exception("${appInfo.appName}.app could not be found.")
    }
    val configuration = if (appInfo.debugBuild) "Debug" else "Release"
    "xcrun simctl install booted $buildPath/Products/$configuration-iphonesimulator/${appInfo.appName}.app".runCommand()
}

/**
 * Resolves the iOS simulator runtime identifier for the requested version.
 * Accepts major (e.g. "26") or major.minor (e.g. "26.2").
 * If only major is provided, picks the highest available minor version.
 * If major.minor doesn't exist, falls back to the highest minor for that major.
 */
private fun resolveIosRuntime(requestedVersion: String): String {
    val process = ProcessBuilder("xcrun", "simctl", "list", "runtimes", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    val requestedParts = requestedVersion.split(".")
    val requestedMajor = requestedParts.first()

    val allIdentifiers = Regex("""com\.apple\.CoreSimulator\.SimRuntime\.iOS-[\d-]+""")
        .findAll(output)
        .map { it.value }
        .distinct()
        .toList()

    verbosePrinter?.invoke("Available iOS runtimes: $allIdentifiers")

    // Sort numerically by extracting version components from the identifier
    fun runtimeSortKey(id: String): List<Int> =
        id.substringAfter("iOS-").split("-").mapNotNull { it.toIntOrNull() }

    // If major.minor provided, try exact match first
    if (requestedParts.size > 1) {
        val exactId = "com.apple.CoreSimulator.SimRuntime.iOS-${requestedVersion.replace(".", "-")}"
        if (exactId in allIdentifiers) return exactId
    }

    // Match by major version, pick the highest minor numerically
    val majorMatch = allIdentifiers
        .filter { it.startsWith("com.apple.CoreSimulator.SimRuntime.iOS-$requestedMajor-") }
        .maxByOrNull { runtimeSortKey(it).drop(1).firstOrNull() ?: 0 }

    if (majorMatch != null) return majorMatch

    // Fallback: highest available iOS runtime
    return allIdentifiers
        .sortedByDescending { runtimeSortKey(it).firstOrNull() ?: 0 }
        .firstOrNull()
        ?: throw Exception(
            "No iOS simulator runtimes found. Requested version: $requestedVersion\n" +
            "simctl output: ${output.take(500)}"
        )
}