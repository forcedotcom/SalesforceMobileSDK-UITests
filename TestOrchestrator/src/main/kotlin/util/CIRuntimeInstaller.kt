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
package com.salesforce.util

import com.salesforce.DEFAULT_IOS_DEVICE
import com.salesforce.ResolvedRuntime
import com.salesforce.SimulatorInfo
import com.salesforce.TestOrchestrator.Companion.SIM_NAME
import com.salesforce.fetchSimctlDeviceTypes
import com.salesforce.resolveCompatibleDevice
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.iterator

private var installThread: Thread? = null
private var installError: Exception? = null

/** Simulators created in the background, ready for reuse by [com.salesforce.createAndInstallIosSimulators]. */
val createdSimulators = ConcurrentLinkedQueue<SimulatorInfo>()

/**
 * Starts a single background thread to download/install iOS simulator runtimes that aren't
 * already present, installing them sequentially to avoid concurrent rsync failures.
 * After each runtime is ready, a simulator is immediately created and booted so it is
 * warm by the time the build finishes.
 * Call early so downloads happen in parallel with other work.
 */
fun startBackgroundRuntimeInstalls(versions: List<String>, iOSDevice: String = DEFAULT_IOS_DEVICE) {
    // Fetch installed runtimes and available versions once (fast before any sims are booted)
    val runtimesOutput = fetchSimctlRuntimes()
    val xcodesOutput = fetchXcodesRuntimes()

    data class VersionInfo(val major: String, val alreadyInstalled: Boolean, val latestVersion: String?)

    val versionInfos = mutableListOf<VersionInfo>()
    for (version in versions) {
        val major = version.split(".").first()
        if (versionInfos.any { it.major == major }) continue

        if (isRuntimeInstalled(major, runtimesOutput)) {
            verbosePrinter?.invoke("iOS $major runtime already installed locally.")
            versionInfos.add(VersionInfo(major, alreadyInstalled = true, latestVersion = null))
            continue
        }

        val latestVersion = findLatestXcodesVersion(major, xcodesOutput)
        if (latestVersion == null) {
            installError = Exception("No iOS $major runtimes found via xcodes.")
            return
        }

        versionInfos.add(VersionInfo(major, alreadyInstalled = false, latestVersion = latestVersion))
    }

    // Clean up old test simulators before creating new ones
    cleanupTestSimulatorsBg()

    installThread = Thread {
        try {
            val deviceTypesOutput = fetchSimctlDeviceTypes()

            // Create sims for already-installed runtimes immediately
            for (info in versionInfos.filter { it.alreadyInstalled }) {
                createAndBootSimInBackground(info.major, iOSDevice, deviceTypesOutput)
            }

            // Install missing runtimes, creating a sim after each one
            for (info in versionInfos.filter { !it.alreadyInstalled }) {
                verbosePrinter?.invoke("Installing iOS ${info.latestVersion} runtime (sequential)...")
                installRuntime(info.major, info.latestVersion!!)
                createAndBootSimInBackground(info.major, iOSDevice, deviceTypesOutput)
            }
        } catch (e: Exception) {
            installError = e
        }
    }.apply {
        name = "runtime-install-sequential"
        isDaemon = true
        start()
    }
}

/**
 * Creates and boots a simulator for the given major iOS version in the background thread.
 * The resulting [SimulatorInfo] is added to [createdSimulators].
 */
private fun createAndBootSimInBackground(major: String, iOSDevice: String, deviceTypesOutput: String) {
    val runtimesNow = fetchSimctlRuntimes()
    val resolved = resolveIosRuntimeByMajor(major, runtimesNow) ?: return
    val device = resolveCompatibleDevice(iOSDevice, resolved.version, deviceTypesOutput)
    val simName = "${SIM_NAME}_$major"

    verbosePrinter?.invoke("Creating Simulator for iOS ${resolved.version}")
    val createProcess = ProcessBuilder(
        "xcrun", "simctl", "create", simName,
        "com.apple.CoreSimulator.SimDeviceType.$device",
        resolved.identifier
    ).redirectErrorStream(true).start()
    val simId = createProcess.inputStream.bufferedReader().readText().trim()
    val createExitCode = createProcess.waitFor()
    if (createExitCode != 0) {
        verbosePrinter?.invoke("Failed to pre-create simulator for iOS ${resolved.version}: $simId")
        return
    }

    verbosePrinter?.invoke("Booting Simulator for iOS ${resolved.version}")
    val bootProcess = ProcessBuilder("xcrun", "simctl", "boot", simId)
        .redirectErrorStream(true).start()
    bootProcess.inputStream.bufferedReader().readText()
    val bootExitCode = bootProcess.waitFor()
    if (bootExitCode != 0) {
        verbosePrinter?.invoke("Failed to boot pre-created simulator for iOS ${resolved.version}")
        return
    }

    createdSimulators.add(SimulatorInfo(simId, resolved.version))
    verbosePrinter?.invoke("Background: iOS ${resolved.version} simulator $simId created and booting")
}

/**
 * Resolves the highest minor version runtime for the given major version.
 */
private fun resolveIosRuntimeByMajor(major: String, runtimesOutput: String): ResolvedRuntime? {
    val allIdentifiers = Regex("""com\.apple\.CoreSimulator\.SimRuntime\.iOS-[\d-]+""")
        .findAll(runtimesOutput).map { it.value }.distinct().toList()
    fun runtimeSortKey(id: String): List<Int> =
        id.substringAfter("iOS-").split("-").mapNotNull { it.toIntOrNull() }
    fun runtimeToVersion(id: String): String =
        id.substringAfter("iOS-").replace("-", ".")
    val match = allIdentifiers
        .filter { it.startsWith("com.apple.CoreSimulator.SimRuntime.iOS-$major-") }
        .maxByOrNull { runtimeSortKey(it).drop(1).firstOrNull() ?: 0 }
    return match?.let { ResolvedRuntime(it, runtimeToVersion(it)) }
}

/**
 * Cleans up test simulators from previous runs (background-safe version).
 */
private fun cleanupTestSimulatorsBg() {
    val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    try {
        val json = Json.parseToJsonElement(output).jsonObject
        val devices = json["devices"]?.jsonObject ?: return
        for ((_, deviceList) in devices) {
            for (device in deviceList.jsonArray) {
                val obj = device.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: continue
                val udid = obj["udid"]?.jsonPrimitive?.content ?: continue
                if (name.startsWith(SIM_NAME)) {
                    ProcessBuilder("xcrun", "simctl", "delete", udid)
                        .redirectErrorStream(true).start().waitFor()
                }
            }
        }
    } catch (_: Exception) { }
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
