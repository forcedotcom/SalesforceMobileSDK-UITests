package com.salesforce

import com.salesforce.TestOrchestrator.Companion.ADB
import com.salesforce.TestOrchestrator.Companion.IS_CI
import com.salesforce.TestOrchestrator.Companion.SIM_NAME
import com.salesforce.util.progressBanner
import com.salesforce.util.runCommand
import com.salesforce.util.verbosePrinter
import kotlinx.serialization.json.*
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

data class ResolvedRuntime(val identifier: String, val version: String)

data class SimulatorInfo(val simId: String, val iOSVersion: String)

fun createAndInstallIosSimulators(
    appInfo: AppInfo,
    iOSVersions: List<String>,
    iOSDevice: String
): List<SimulatorInfo> {
    // Clean up all test simulators from previous runs
    cleanupTestSimulators()

    val simulators = mutableListOf<SimulatorInfo>()

    for (version in iOSVersions) {
        val simName = "${SIM_NAME}_$version"
        val resolved = resolveIosRuntime(version)
        verbosePrinter?.invoke("Using runtime: ${resolved.identifier}")
        val device = resolveCompatibleDevice(iOSDevice, resolved.version)

        progressBanner?.update {
            context = context.advance("Create Simulator (iOS ${resolved.version})")
            completed += 1
        }
        verbosePrinter?.invoke("Creating Simulator for iOS ${resolved.version}")
        val createProcess = ProcessBuilder(
            "xcrun", "simctl", "create", simName,
            "com.apple.CoreSimulator.SimDeviceType.$device",
            resolved.identifier
        ).redirectErrorStream(true).start()
        val simId = createProcess.inputStream.bufferedReader().readText().trim()
        val createExitCode = createProcess.waitFor()
        if (createExitCode != 0) {
            throw Exception("Failed to create simulator for iOS ${resolved.version} (exit $createExitCode): $simId")
        }

        progressBanner?.update {
            context = context.advance("Boot Simulator (iOS ${resolved.version})")
            completed += 1
        }
        verbosePrinter?.invoke("Booting Simulator for iOS ${resolved.version}")
        "xcrun simctl boot $simId".runCommand()

        simulators.add(SimulatorInfo(simId, resolved.version))
    }

    // Wait for all simulators to finish booting
    Thread.sleep(3000)

    // Install app on all simulators
    val buildPath = when {
        File("${appInfo.iosRoot}/DerivedData/Build/").exists() -> "${appInfo.iosRoot}/DerivedData/Build"
        File("${appInfo.iosRoot}/Build/").exists() -> "${appInfo.iosRoot}/Build"
        else -> throw Exception("${appInfo.appName}.app could not be found.")
    }
    val configuration = if (appInfo.debugBuild) "Debug" else "Release"

    for (sim in simulators) {
        progressBanner?.update {
            context = context.advance("Install App (iOS ${sim.iOSVersion})")
            completed += 1
        }
        verbosePrinter?.invoke("Installing App on iOS ${sim.iOSVersion}")
        "xcrun simctl install ${sim.simId} $buildPath/Products/$configuration-iphonesimulator/${appInfo.appName}.app".runCommand()
    }

    return simulators
}

/**
 * Deletes all simulators whose name starts with the test simulator prefix.
 */
private fun cleanupTestSimulators() {
    val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    val json = Json.parseToJsonElement(output).jsonObject
    val devices = json["devices"]?.jsonObject ?: return

    for ((_, deviceList) in devices) {
        for (device in deviceList.jsonArray) {
            val obj = device.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: continue
            val udid = obj["udid"]?.jsonPrimitive?.content ?: continue
            if (name.startsWith(SIM_NAME)) {
                "xcrun simctl delete $udid".runCommand(suppressErrors = true)
            }
        }
    }
}

/**
 * Resolves the device type identifier compatible with the given iOS runtime version.
 * If the requested device is compatible, returns it as-is.
 * If not, picks the closest compatible device from the same product family (iPhone).
 */
fun resolveCompatibleDevice(requestedDevice: String, runtimeVersion: String): String {
    val process = ProcessBuilder("xcrun", "simctl", "list", "devicetypes", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    val json = Json.parseToJsonElement(output).jsonObject
    val deviceTypes = json["devicetypes"]?.jsonArray ?: throw Exception("Failed to parse device types")

    val deviceId = "com.apple.CoreSimulator.SimDeviceType.$requestedDevice"

    // Encode runtime version the same way simctl does: major << 16 | minor << 8 | patch
    val versionParts = runtimeVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val encodedVersion = versionParts.getOrElse(0) { 0 }.shl(16) or
            versionParts.getOrElse(1) { 0 }.shl(8) or
            versionParts.getOrElse(2) { 0 }

    fun isCompatible(deviceType: JsonObject): Boolean {
        val min = deviceType["minRuntimeVersion"]?.jsonPrimitive?.long ?: return false
        val max = deviceType["maxRuntimeVersion"]?.jsonPrimitive?.long ?: return false
        return encodedVersion.toLong() in min..max
    }

    // Check if the requested device is compatible
    val requestedDeviceType = deviceTypes.firstOrNull {
        it.jsonObject["identifier"]?.jsonPrimitive?.content == deviceId
    }?.jsonObject

    if (requestedDeviceType != null && isCompatible(requestedDeviceType)) {
        return requestedDevice
    }

    // Find a compatible iPhone device, preferring newer models
    val compatibleDevice = deviceTypes
        .map { it.jsonObject }
        .filter { dt ->
            dt["productFamily"]?.jsonPrimitive?.content == "iPhone" && isCompatible(dt)
        }
        .maxByOrNull { dt ->
            // Prefer higher minRuntimeVersion (newer devices)
            dt["minRuntimeVersion"]?.jsonPrimitive?.long ?: 0
        }
        ?: throw Exception(
            "No compatible iPhone device type found for iOS $runtimeVersion. " +
            "Requested device '$requestedDevice' requires a different iOS version."
        )

    val fallbackId = compatibleDevice["identifier"]?.jsonPrimitive?.content
        ?: throw Exception("Failed to parse device identifier")
    val fallbackName = compatibleDevice["name"]?.jsonPrimitive?.content ?: fallbackId
    val fallbackDevice = fallbackId.removePrefix("com.apple.CoreSimulator.SimDeviceType.")

    verbosePrinter?.invoke("Device '$requestedDevice' is not compatible with iOS $runtimeVersion. Using '$fallbackName' instead.")
    return fallbackDevice
}

private fun isRuntimeInstalled(majorVersion: String): Boolean {
    val process = ProcessBuilder("xcrun", "simctl", "list", "runtimes", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return Regex("""com\.apple\.CoreSimulator\.SimRuntime\.iOS-$majorVersion-""")
        .containsMatchIn(output)
}

/**
 * On CI, ensures a simulator runtime is available for the requested iOS version.
 * Checks locally installed runtimes first via simctl, then falls back to xcodes
 * to find and install the latest release for the requested major version.
 */
private fun ensureIosRuntimeAvailableForCI(requestedVersion: String) {
    if (!IS_CI) return

    val requestedMajor = requestedVersion.split(".").first()

    if (isRuntimeInstalled(requestedMajor)) {
        verbosePrinter?.invoke("iOS $requestedMajor runtime already installed locally.")
        return
    }

    // Not installed locally — use xcodes to find the latest available version
    val latestVersion = findLatestXcodesVersion(requestedMajor)
        ?: throw Exception("No iOS $requestedMajor runtimes found via xcodes.")

    verbosePrinter?.invoke("Installing iOS $latestVersion runtime...")
    progressBanner?.update {
        context = context.advance("Install iOS $latestVersion Runtime")
    }

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

    if (isRuntimeInstalled(requestedMajor)) {
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

    if (xcodebuildExitCode != 0 || !isRuntimeInstalled(requestedMajor)) {
        throw Exception(
            "Failed to install iOS $latestVersion runtime.\n" +
            "xcodes output: $xcodesOutput\n" +
            "xcodebuild output: $xcodebuildOutput"
        )
    }

    verbosePrinter?.invoke("iOS $latestVersion runtime installed successfully via xcodebuild.")
}

/**
 * Queries xcodes for the latest available iOS version matching the requested major version.
 */
private fun findLatestXcodesVersion(requestedMajor: String): String? {
    val listProcess = ProcessBuilder("xcodes", "runtimes")
        .redirectErrorStream(true).start()
    val listOutput = listProcess.inputStream.bufferedReader().readText()
    listProcess.waitFor()

    verbosePrinter?.invoke("xcodes runtimes output:\n$listOutput")

    val runtimePattern = Regex("""^iOS (\d+[\d.]*)\b""", RegexOption.MULTILINE)
    return runtimePattern.findAll(listOutput)
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

/**
 * Resolves the iOS simulator runtime identifier for the requested version.
 * Accepts major (e.g. "26") or major.minor (e.g. "26.2").
 * If only major is provided, picks the highest available minor version.
 * If major.minor doesn't exist, falls back to the highest minor for that major.
 */
private fun resolveIosRuntime(requestedVersion: String): ResolvedRuntime {
    ensureIosRuntimeAvailableForCI(requestedVersion)

    val requestedParts = requestedVersion.split(".")
    val requestedMajor = requestedParts.first()
    val process = ProcessBuilder("xcrun", "simctl", "list", "runtimes", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    val allIdentifiers = Regex("""com\.apple\.CoreSimulator\.SimRuntime\.iOS-[\d-]+""")
        .findAll(output)
        .map { it.value }
        .distinct()
        .toList()
    val availableVersions = allIdentifiers.joinToString(", ") {
        it.removePrefix("com.apple.CoreSimulator.SimRuntime.iOS-").replace("-", ".")
    }

    verbosePrinter?.invoke("Available iOS versions: $availableVersions")

    // Sort numerically by extracting version components from the identifier
    fun runtimeSortKey(id: String): List<Int> =
        id.substringAfter("iOS-").split("-").mapNotNull { it.toIntOrNull() }

    fun runtimeToVersion(id: String): String =
        id.substringAfter("iOS-").replace("-", ".")

    return when {
        requestedParts.size > 1 -> {
            // If major.minor provided, try exact match first
            val exactId = "com.apple.CoreSimulator.SimRuntime.iOS-${requestedVersion.replace(".", "-")}"
            if (exactId in allIdentifiers) {
                ResolvedRuntime(exactId, runtimeToVersion(exactId))
            } else {
                throw Exception("Exact iOS version ($requestedVersion) not found.  Available options: $availableVersions")
            }
        }
        else -> {
            // Match by major version, pick the highest minor numerically
            val majorMatch = allIdentifiers
                .filter { it.startsWith("com.apple.CoreSimulator.SimRuntime.iOS-$requestedMajor-") }
                .maxByOrNull { runtimeSortKey(it).drop(1).firstOrNull() ?: 0 }

            if (majorMatch != null) {
                ResolvedRuntime(majorMatch, runtimeToVersion(majorMatch))
            } else {
                throw Exception("No iOS ${requestedVersion}.x versions found.  Available options: $availableVersions")
            }
        }
    }
}