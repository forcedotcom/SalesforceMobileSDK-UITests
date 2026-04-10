/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce

import com.salesforce.TestOrchestrator.Companion.ADB
import com.salesforce.TestOrchestrator.Companion.SIM_NAME
import com.salesforce.util.awaitBackgroundRuntimeInstalls
import com.salesforce.util.fetchSimctlRuntimes
import com.salesforce.util.createdSimulators
import com.salesforce.util.progressBanner
import com.salesforce.util.runCommand
import com.salesforce.util.runCommandCapture
import com.salesforce.util.verbosePrinter
import kotlinx.serialization.json.*
import java.io.File

fun installAndroidApp(appInfo: AppInfo) {
    // Push config to device so androidTestConfig can load it
    val pushResult = "$ADB push shared/test/android/ui_test_config.json /data/local/tmp/ui_test_config.json".runCommand()
    if (pushResult != 0) {
        throw Exception("Failed to push test config to device (exit $pushResult).")
    }
    "$ADB uninstall ${appInfo.packageName}".runCommand(suppressErrors = true)

    progressBanner?.update {
        context = context.advance("Install App")
        completed += 1
    }
    verbosePrinter?.invoke("Installing App")
    val installResult = "$ADB install -r ${appInfo.apkPath}".split(" ").runCommandCapture()
    if (installResult.exitCode != 0) {
        throw Exception("APK install failed.\n${installResult.output?.trim()}")
    }
}

/**
 * Installs the upgraded app over the existing one without uninstalling first.
 * This preserves app data so the login session survives the upgrade.
 */
fun upgradeAndroidApp(appInfo: AppInfo) {
    progressBanner?.update {
        context = context.advance("Install Upgraded App")
        completed += 1
    }
    verbosePrinter?.invoke("Force-stopping old app before upgrade")
    "$ADB shell am force-stop ${appInfo.packageName}".split(" ").runCommandCapture()

    verbosePrinter?.invoke("Installing Upgraded App (preserving data)")
    val installResult = "$ADB install -r ${appInfo.apkPath}".split(" ").runCommandCapture()
    if (installResult.exitCode != 0) {
        throw Exception("Upgrade APK install failed.\n${installResult.output?.trim()}")
    }
}

data class ResolvedRuntime(val identifier: String, val version: String)

data class SimulatorInfo(val simId: String, val iOSVersion: String)

fun createAndInstallIosSimulators(
    appInfo: AppInfo,
    iOSVersions: List<String>,
    iOSDevice: String
): List<SimulatorInfo> {
    // Wait for background runtime installs (and sim creation) to finish
    awaitBackgroundRuntimeInstalls(iOSVersions)

    // Collect any simulators pre-created in the background
    val preCreated = mutableListOf<SimulatorInfo>()
    while (true) {
        val sim = createdSimulators.poll() ?: break
        preCreated.add(sim)
    }
    val preCreatedByMajor = preCreated.associateBy { it.iOSVersion.split(".").first() }

    // If no sims were pre-created (non-CI or background failed), clean up old sims
    if (preCreated.isEmpty()) {
        cleanupTestSimulators()
    }

    // Fetch simctl data once
    val runtimesOutput = fetchSimctlRuntimes()
    val deviceTypesOutput = fetchSimctlDeviceTypes()

    val simulators = mutableListOf<SimulatorInfo>()

    for (version in iOSVersions) {
        val major = version.split(".").first()

        // Reuse pre-created simulator if available
        val existing = preCreatedByMajor[major]
        if (existing != null) {
            verbosePrinter?.invoke("Reusing pre-created simulator for iOS ${existing.iOSVersion} (${existing.simId})")
            progressBanner?.update {
                context = context.advance("Create Simulator (iOS ${existing.iOSVersion})")
                completed += 1
            }
            progressBanner?.update {
                context = context.advance("Boot Simulator (iOS ${existing.iOSVersion})")
                completed += 1
            }
            simulators.add(existing)
            continue
        }

        // Create a new simulator (fallback for non-CI or if background didn't cover this version)
        val simName = "${SIM_NAME}_$version"
        val resolved = resolveIosRuntime(version, runtimesOutput)
        verbosePrinter?.invoke("Using runtime: ${resolved.identifier}")
        val device = resolveCompatibleDevice(iOSDevice, resolved.version, deviceTypesOutput)

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
        val bootResult = "xcrun simctl boot $simId".runCommand()
        if (bootResult != 0) {
            throw Exception("Failed to boot simulator for iOS ${resolved.version} (exit $bootResult).")
        }

        simulators.add(SimulatorInfo(simId, resolved.version))
    }

    // Wait for all simulators to finish booting
    for (sim in simulators) {
        verbosePrinter?.invoke("Waiting for iOS ${sim.iOSVersion} simulator to boot...")
        val bootStatusResult = "xcrun simctl bootstatus ${sim.simId} -b".runCommand()
        if (bootStatusResult != 0) {
            throw Exception("Simulator for iOS ${sim.iOSVersion} failed to finish booting (exit $bootStatusResult).")
        }
    }

    // Disable keyboard slide typing introduction to prevent it from blocking Login
    for (sim in simulators) {
        "xcrun simctl spawn ${sim.simId} defaults write -g DidShowContinuousPathIntroduction -bool true".runCommand(suppressErrors = true)
        "xcrun simctl spawn ${sim.simId} defaults write -g DidShowGestureKeyboardIntroduction -bool true".runCommand(suppressErrors = true)
    }

    // Install app on all simulators
    for (sim in simulators) {
        progressBanner?.update {
            context = context.advance("Install App (iOS ${sim.iOSVersion})")
            completed += 1
        }
        installIosApp(appInfo, sim)
    }

    return simulators
}

/**
 * Installs the upgraded app over the existing one on all simulators.
 */
fun upgradeIosApp(appInfo: AppInfo, simulators: List<SimulatorInfo>) {
    for (sim in simulators) {
        progressBanner?.update {
            context = context.advance("Install Upgraded App (iOS ${sim.iOSVersion})")
            completed += 1
        }
        verbosePrinter?.invoke("Terminating old app on simulator ${sim.simId} before upgrade")
        listOf("xcrun", "simctl", "terminate", sim.simId, appInfo.packageName)
            .runCommandCapture()

        verbosePrinter?.invoke("Installing Upgraded App on iOS ${sim.iOSVersion} (preserving data)")
        installIosApp(appInfo, sim)
    }
}

/**
 * Installs the app on a single iOS simulator.
 * Reusable for both initial install and retry reinstall.
 */
fun installIosApp(appInfo: AppInfo, sim: SimulatorInfo) {
    val buildPath = when {
        File("${appInfo.iosRoot}/DerivedData/Build/").exists() -> "${appInfo.iosRoot}/DerivedData/Build"
        File("${appInfo.iosRoot}/Build/").exists() -> "${appInfo.iosRoot}/Build"
        else -> throw Exception("${appInfo.appName}.app could not be found.")
    }
    val configuration = if (appInfo.debugBuild) "Debug" else "Release"

    verbosePrinter?.invoke("Installing App on iOS ${sim.iOSVersion}")
    val simInstallResult = "xcrun simctl install ${sim.simId} $buildPath/Products/$configuration-iphonesimulator/${appInfo.appName}.app".runCommand()
    if (simInstallResult != 0) {
        throw Exception("Failed to install ${appInfo.appName} on iOS ${sim.iOSVersion} simulator (exit $simInstallResult).")
    }
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

fun fetchSimctlDeviceTypes(): String {
    val process = ProcessBuilder("xcrun", "simctl", "list", "devicetypes", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output
}

/**
 * Resolves the device type identifier compatible with the given iOS runtime version.
 * Uses pre-fetched devicetypes JSON output to avoid redundant simctl calls.
 */
fun resolveCompatibleDevice(requestedDevice: String, runtimeVersion: String, deviceTypesOutput: String): String {
    val json = Json.parseToJsonElement(deviceTypesOutput).jsonObject
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

/**
 * Resolves the iOS simulator runtime identifier for the requested version.
 * Uses pre-fetched runtimes output to avoid redundant simctl calls.
 */
private fun resolveIosRuntime(requestedVersion: String, runtimesOutput: String): ResolvedRuntime {
    val requestedParts = requestedVersion.split(".")
    val requestedMajor = requestedParts.first()
    val allIdentifiers = Regex("""com\.apple\.CoreSimulator\.SimRuntime\.iOS-[\d-]+""")
        .findAll(runtimesOutput)
        .map { it.value }
        .distinct()
        .toList()
    val availableVersions = allIdentifiers.joinToString(", ") {
        it.removePrefix("com.apple.CoreSimulator.SimRuntime.iOS-").replace("-", ".")
    }

    verbosePrinter?.invoke("Available iOS versions: $availableVersions")

    fun runtimeSortKey(id: String): List<Int> =
        id.substringAfter("iOS-").split("-").mapNotNull { it.toIntOrNull() }

    fun runtimeToVersion(id: String): String =
        id.substringAfter("iOS-").replace("-", ".")

    return when {
        requestedParts.size > 1 -> {
            val exactId = "com.apple.CoreSimulator.SimRuntime.iOS-${requestedVersion.replace(".", "-")}"
            if (exactId in allIdentifiers) {
                ResolvedRuntime(exactId, runtimeToVersion(exactId))
            } else {
                throw Exception("Exact iOS version ($requestedVersion) not found.  Available options: $availableVersions")
            }
        }
        else -> {
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
