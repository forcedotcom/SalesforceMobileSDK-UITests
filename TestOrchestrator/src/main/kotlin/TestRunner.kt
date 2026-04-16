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
import com.salesforce.TestOrchestrator.Companion.ANDROID_TEST_CLASS_DIR
import com.salesforce.TestOrchestrator.Companion.ANDROID_TEST_DIR
import com.salesforce.TestOrchestrator.Companion.GCLOUD_RESULTS_DIR
import com.salesforce.TestOrchestrator.Companion.IOS_TEST_DIR
import com.salesforce.util.PanelProgressBarMaker
import com.salesforce.util.finish
import com.salesforce.util.progressBanner
import com.salesforce.util.runCommand
import com.salesforce.util.runCommandCapture
import com.salesforce.util.verbosePrinter
import kotlinx.serialization.json.*
import java.io.File

fun runTests(
    appInfo: AppInfo,
    iOSVersions: List<String>,
    iOSDevice: String,
    useFirebase: Boolean,
    finishProgress: Boolean = true,
    upgradeLogin: Boolean = false,
) {
    when (appInfo.os) {
        OS.ANDROID -> {
            if (upgradeLogin) {
                runAndroidUpgradeLogin(appInfo)
            } else if (useFirebase) {
                runAndroidTestsFirebase(appInfo)
            } else {
                runAndroidTestsLocal(appInfo)
            }
        }
        OS.IOS -> {
            copyIosTestConfig()
            val simulators = createAndInstallIosSimulators(appInfo, iOSVersions, iOSDevice)
            // Update banner title with resolved iOS versions
            PanelProgressBarMaker.title = "Testing ${appInfo.appName} (iOS " +
                    "${simulators.joinToString(", ") { it.iOSVersion }})"
            runIosTests(appInfo, simulators, upgradeLogin)
        }
    }

    if (finishProgress) {
        progressBanner?.update {
            context = context.pass()
            completed += 1
        }
        progressBanner?.finish()
    }
}

fun runUpgradeTests(appInfo: AppInfo, simulators: List<SimulatorInfo>) {
    when (appInfo.os) {
        OS.ANDROID -> runAndroidUpgradeTest(appInfo)
        OS.IOS -> runIosUpgradeTests(appInfo, simulators)
    }

    progressBanner?.update {
        context = context.pass()
        completed += 1
    }
    progressBanner?.finish()
}

private fun runAndroidUpgradeTest(appInfo: AppInfo) {
    upgradeAndroidApp(appInfo)

    val classParam = "-Pandroid.testInstrumentationRunnerArguments.class=${ANDROID_TEST_CLASS_DIR}.UpgradeTest#testUpgradePreservesLogin"
    val packageParam = "-Pandroid.testInstrumentationRunnerArguments.packageName=${appInfo.packageName}"
    val complexHybridParam = appInfo.complexHybridType?.let {
        "-Pandroid.testInstrumentationRunnerArguments.complexHybrid=$it"
    } ?: ""

    val testCommand = "./gradlew $classParam $packageParam $complexHybridParam connectedAndroidTest"
        .split(" ").filter { it.isNotEmpty() }

    progressBanner?.update {
        context = context.advance("Run Upgrade Test")
        completed += 1
    }
    verbosePrinter?.invoke("Running Upgrade Test")

    val result = testCommand.runCommandCapture(workingDir = ANDROID_TEST_DIR)

    if (result.exitCode != 0 && TestOrchestrator.IS_CI) {
        val failureDetail = parseTestFailure(result.output)
        // Only retry for load/timeout failures, not for login-session-lost (which is a real SDK bug)
        if (!failureDetail.contains("login session", ignoreCase = true)) {
            verbosePrinter?.invoke("Upgrade test failed, retrying once after force-stop: $failureDetail")
            "$ADB shell am force-stop ${appInfo.packageName}".split(" ").runCommandCapture()
            Thread.sleep(5_000)

            val retryResult = testCommand.runCommandCapture(workingDir = ANDROID_TEST_DIR)
            retryResult.throwIfFailed(appInfo.appPath, "android_upgrade_test_retry", parseTestFailure(retryResult.output))
            return
        }
    }

    result.throwIfFailed(appInfo.appPath, "android_upgrade_test", parseTestFailure(result.output))
}

private fun runIosUpgradeTests(appInfo: AppInfo, simulators: List<SimulatorInfo>) {
    copyIosTestConfig()
    upgradeIosApp(appInfo, simulators)

    val versionsLabel = simulators.joinToString(", ") { it.iOSVersion }

    progressBanner?.update {
        context = context.advance("Run Upgrade Test (iOS $versionsLabel)")
        completed += 1
    }
    verbosePrinter?.invoke("Running Upgrade Test on iOS $versionsLabel")

    val resultBundlePath = "test_output/${appInfo.appName}_upgrade"
    File(IOS_TEST_DIR, resultBundlePath).deleteRecursively()

    val result = runXcodebuildTest(
        testScheme = "UpgradeTest",
        simulators,
        resultBundlePath,
        appInfo,
        onlyTesting = "SalesforceMobileSDK-UITest/UpgradeTest/testUpgradePreservesLogin",
    )

    if (result.exitCode != 0) {
        val resultBundleAbsPath = File(IOS_TEST_DIR, resultBundlePath).absolutePath
        val logPath = result.saveFullOutput(appInfo.appPath, "ios_upgrade_test")
        val logMsg = logPath?.let { "\nFull output: $it" } ?: ""
        val failureMsg = parseXCResultFailures(resultBundleAbsPath)
            ?: parseTestFailure(result.output)
        throw Exception("$failureMsg$logMsg")
    }
}

private fun runAndroidUpgradeLogin(appInfo: AppInfo) {
    installAndroidApp(appInfo)

    "adb shell pm grant ${appInfo.packageName} android.permission.POST_NOTIFICATIONS"
        .runCommand(suppressErrors = true)

    val classParam = "-Pandroid.testInstrumentationRunnerArguments.class=${ANDROID_TEST_CLASS_DIR}.UpgradeTest#testInitialLogin"
    val packageParam = "-Pandroid.testInstrumentationRunnerArguments.packageName=${appInfo.packageName}"
    val complexHybridParam = appInfo.complexHybridType?.let {
        "-Pandroid.testInstrumentationRunnerArguments.complexHybrid=$it"
    } ?: ""

    progressBanner?.update {
        context = context.advance("Run Initial Login")
        completed += 1
    }
    verbosePrinter?.invoke("Running Initial Login (upgrade Phase 1)")

    val result = "./gradlew $classParam $packageParam $complexHybridParam connectedAndroidTest"
        .split(" ").filter { it.isNotEmpty() }
        .runCommandCapture(workingDir = ANDROID_TEST_DIR)

    result.throwIfFailed(appInfo.appPath, "android_upgrade_login", parseTestFailure(result.output))
}

private fun runAndroidTestsLocal(appInfo: AppInfo) {
    installAndroidApp(appInfo)

    // Grant Push
    "adb shell pm grant ${appInfo.packageName} android.permission.POST_NOTIFICATIONS"
        .runCommand(suppressErrors = true)

    // TestOrchestrator params
    val testClass = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) {
        "NativeLoginTest"
    } else {
        "LoginTest"
    }
    val classParam =  "-Pandroid.testInstrumentationRunnerArguments.class=${ANDROID_TEST_CLASS_DIR}.$testClass"
    val packageParam = "-Pandroid.testInstrumentationRunnerArguments.packageName=${appInfo.packageName}"
    val complexHybridParam = appInfo.complexHybridType?.let {
        "-Pandroid.testInstrumentationRunnerArguments.complexHybrid=$it"
    } ?: ""

    progressBanner?.update {
        context = context.advance("Run Login TestOrchestrator")
        completed += 1
    }
    verbosePrinter?.invoke("Running Login TestOrchestrator")

    // Run Test
    val result = "./gradlew $classParam $packageParam $complexHybridParam connectedAndroidTest"
        .split(" ").filter { it.isNotEmpty() }
        .runCommandCapture(workingDir = ANDROID_TEST_DIR)

    result.throwIfFailed(appInfo.appPath, "android_test", parseTestFailure(result.output))
}

private fun runAndroidTestsFirebase(appInfo: AppInfo) {
    progressBanner?.update {
        context = context.advance("Compile Tests")
        completed += 1
    }
    verbosePrinter?.invoke("Compiling TestOrchestrator APK")

    val buildResult = "./gradlew app:assembleAndroidTest"
        .split(" ").runCommandCapture(workingDir = ANDROID_TEST_DIR)
    buildResult.throwIfFailed(
        appInfo.appPath,
        label = "test_apk_build",
        message = "TestOrchestrator APK failed to build.\n${buildResult.parseBuildFailure()}",
    )

    progressBanner?.update {
        context = context.advance("Run Login TestOrchestrator")
        completed += 1
    }
    verbosePrinter?.invoke("Testing App with Firebase")

    val testClass = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) {
        "NativeLoginTest"
    } else {
        "LoginTest"
    }
    var devices = ""
    for (level in ANDROID_MIN_API_LEVEL..ANDROID_MAX_API_LEVEL) {
        devices += "--device model=MediumPhone.arm,version=$level,locale=en,orientation=portrait "
    }
    val envVars = "class=${ANDROID_TEST_CLASS_DIR}.$testClass" +
        ",packageName=${appInfo.packageName}" +
        (appInfo.complexHybridType?.let { ",complexHybrid=$it" } ?: "")

    """
        gcloud firebase test android run
            --project mobile-apps-firebase-test
            --type instrumentation
            --app=../${appInfo.apkPath}
            --test=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            --other-files /data/local/tmp/ui_test_config.json=../shared/test/android/ui_test_config.json
            $devices
            --results-history-name=UITest-${appInfo.appName}
            --results-dir=$GCLOUD_RESULTS_DIR
            --environment-variables $envVars
            --no-performance-metrics 
            --no-auto-google-login 
            --num-flaky-test-attempts=2
    """.trimIndent().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        .runCommandCapture(workingDir = ANDROID_TEST_DIR).let { result ->
            result.throwIfFailed(
                appInfo.appPath,
                label = "firebase_test",
                message =  parseTestFailure(result.output),
            )
        }
}

private fun runIosTests(
    appInfo: AppInfo,
    simulators: List<SimulatorInfo>,
    upgradeLogin: Boolean = false,
) {
    val testScheme = when {
        upgradeLogin -> "UpgradeTest"
        appInfo.appName.contains("nativelogin", ignoreCase = true) -> "NativeLoginTest"
        else -> "LoginTest"
    }
    val onlyTesting = if (upgradeLogin) "SalesforceMobileSDK-UITest/UpgradeTest/testInitialLogin" else null
    val versionsLabel = simulators.joinToString(", ") { it.iOSVersion }

    progressBanner?.update {
        context = context.advance("Run Login Tests (iOS $versionsLabel)")
        completed += 1
    }
    verbosePrinter?.invoke("Running Login Tests on iOS $versionsLabel")

    val resultBundlePath = "test_output/${appInfo.appName}"
    File(IOS_TEST_DIR, resultBundlePath).deleteRecursively()

    // Run Test
    val result = runXcodebuildTest(testScheme, simulators, resultBundlePath, appInfo, onlyTesting)

    // Retry on Failure if running in CI
    if (result.exitCode != 0) {
        val resultBundleAbsPath = File(IOS_TEST_DIR, resultBundlePath).absolutePath

        if (!TestOrchestrator.IS_CI) {
            val logPath = result.saveFullOutput(appInfo.appPath, "ios_test")
            val logMsg = logPath?.let { "\nFull output: $it" } ?: ""
            val failureMsg = parseXCResultFailures(resultBundleAbsPath)
                ?: parseTestFailure(result.output)
            throw Exception("$failureMsg$logMsg")
        }

        // Determine which simulators failed so we only reset/retry those
        val deviceResults = parsePerDeviceResults(resultBundleAbsPath)
        val failedSims = if (deviceResults != null && deviceResults.failedDeviceIds.isNotEmpty()) {
            simulators.filter { it.simId in deviceResults.failedDeviceIds }
        } else {
            simulators
        }

        val retryVersionsLabel = failedSims.joinToString(", ") { it.iOSVersion }
        verbosePrinter?.invoke("Retrying failed simulators: iOS $retryVersionsLabel")

        for (sim in failedSims) {
            // Reset simulator to clear stale state that causes "Application info provider returned nil"
            "xcrun simctl shutdown ${sim.simId}".runCommand(suppressErrors = true)
            "xcrun simctl erase ${sim.simId}".runCommand(suppressErrors = true)
            "xcrun simctl boot ${sim.simId}".runCommand(suppressErrors = true)
            val bootStatus = "xcrun simctl bootstatus ${sim.simId} -b".runCommand(suppressErrors = true)
            if (bootStatus != 0) {
                verbosePrinter?.invoke("Warning: Simulator boot status check failed for iOS ${sim.iOSVersion}")
            }

            // Reinstall the app since erase removed it
            try {
                installIosApp(appInfo, sim)
            } catch (e: Exception) {
                verbosePrinter?.invoke("Warning: Failed to reinstall app for retry: ${e.message}")
            }
        }

        val retryBundlePath = "test_output/${appInfo.appName}_retry"
        File(IOS_TEST_DIR, retryBundlePath).deleteRecursively()

        val retryResult = runXcodebuildTest(testScheme, failedSims, retryBundlePath, appInfo, onlyTesting)

        if (retryResult.exitCode != 0) {
            val retryBundleAbsPath = File(IOS_TEST_DIR, retryBundlePath).absolutePath
            val logPath = retryResult.saveFullOutput(appInfo.appPath, "ios_test_retry")
            val logMsg = logPath?.let { "\nFull output: $it" } ?: ""
            val failureMsg = parseXCResultFailures(retryBundleAbsPath)
                ?: parseTestFailure(retryResult.output)
            throw Exception("$failureMsg$logMsg")
        }
    }
}

private fun runXcodebuildTest(
    testScheme: String,
    simulators: List<SimulatorInfo>,
    resultBundlePath: String,
    appInfo: AppInfo,
    onlyTesting: String? = null,
): com.salesforce.util.CommandResult {
    val testCommand = buildList {
        addAll(listOf(
            "xcodebuild", "test",
            "-project", "SalesforceMobileSDK-UITest.xcodeproj",
            "-scheme", testScheme,
        ))
        onlyTesting?.let { addAll(listOf("-only-testing", it)) }
        for (sim in simulators) {
            addAll(listOf("-destination", "platform=iOS Simulator,id=${sim.simId}"))
        }
        addAll(listOf("-resultBundlePath", resultBundlePath))
        add("TEST_APP_BUNDLE=${appInfo.packageName}")
        appInfo.complexHybridType?.let { add("COMPLEX_HYBRID=$it") }
    }
    return testCommand.runCommandCapture(workingDir = IOS_TEST_DIR)
}

private fun copyIosTestConfig() {
    val src = File("shared/test/ios/ui_test_config.json")
    val dst = File("$IOS_TEST_DIR/ui_test_config.json")
    if (src.exists()) {
        src.copyTo(dst, overwrite = true)
        verbosePrinter?.invoke("Copied ui_test_config.json to iOS test project")
    } else {
        throw Exception("iOS ui_test_config.json not found at ${src.absolutePath}")
    }
}

private fun parseTestFailure(output: String?): String {
    if (output.isNullOrBlank()) return "TestOrchestrator failed with no output."

    val lines = output.lines()
    val failureLines = mutableListOf<String>()
    var capturing = false

    // Box-drawing characters used in Firebase result tables
    val tableChars = setOf('┌', '├', '└', '│', '─', '┬', '┼', '┴', '┐', '┤', '┘', '╔', '║', '═')

    for (line in lines) {
        val trimmed = line.trim()
        when {
            // Capture Firebase result table
            trimmed.isNotEmpty() && trimmed.first() in tableChars -> {
                failureLines.add(line)
            }
            // Capture "More details are available at" URL line
            trimmed.startsWith("More details are available at") -> {
                failureLines.add(trimmed)
            }
            // xcbeautify: individual test failure lines (✖ testName, reason)
            trimmed.startsWith("✖") -> {
                failureLines.add(trimmed)
            }
            // GitHub Actions error annotations from xcbeautify
            trimmed.startsWith("##[error]") -> {
                failureLines.add(trimmed.removePrefix("##[error]").trim())
            }
            // XCTest assertion / snapshot failures (e.g. "Failed to get matching snapshot")
            trimmed.contains("Failed to get matching snapshot") || trimmed.contains("Asynchronous wait failed") -> {
                failureLines.add(trimmed)
            }
            // xcbeautify: "Failing Tests:" section header
            trimmed == "Failing Tests:" -> {
                capturing = true
                failureLines.add(trimmed)
            }
            // xcbeautify: XCTest assertion failures
            trimmed.contains("XCTAssert") || trimmed.contains("XCTFail") -> {
                failureLines.add(trimmed)
            }
            // FAILED/error lines and their indented continuation
            trimmed.contains("FAILED") || trimmed.contains("failed:") || trimmed.startsWith("ERROR:") -> {
                if (failureLines.isNotEmpty() && failureLines.last().contains("failed:")) failureLines.add("")
                capturing = true
                failureLines.add(trimmed)
            }
            capturing && trimmed.isNotEmpty() && (line.startsWith(" ") || line.startsWith("\t")) -> {
                failureLines.add(trimmed)
            }
            capturing -> {
                capturing = false
            }
        }
    }

    // Filter out the generic "** TEST FAILED **" if we have more specific details
    val detailed = failureLines.filter { it.trim() != "** TEST FAILED **" }
    val result = detailed.ifEmpty { failureLines }

    return if (result.isNotEmpty()) {
        result.joinToString("\n")
    } else {
        val lastLines = lines.filter { it.isNotBlank() }.takeLast(5)
        if (lastLines.isNotEmpty()) {
            lastLines.joinToString("\n") { it.trim() }
        } else {
            "TestOrchestrator failed. Unable to parse failure details from output."
        }
    }
}

private data class DeviceTestResults(
    val failedDeviceIds: Set<String>,
    val passedVersions: Set<String>,
    val failureMessages: Map<String, List<String>>, // osVersion -> failure details
)

/**
 * Parses the xcresult bundle and returns structured per-device results.
 * Returns null if the result bundle can't be parsed.
 */
private fun parsePerDeviceResults(resultBundlePath: String): DeviceTestResults? {
    if (!File(resultBundlePath).exists()) {
        verbosePrinter?.invoke("xcresult bundle not found at: $resultBundlePath")
        return null
    }

    val process = ProcessBuilder(
        "xcrun", "xcresulttool", "get", "test-results", "tests",
        "--path", resultBundlePath, "--compact"
    ).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        verbosePrinter?.invoke("xcresulttool failed (exit $exitCode): ${output.take(500)}")
        return null
    }

    return try {
        val json = Json.parseToJsonElement(output).jsonObject
        val devices = json["devices"]?.jsonArray
        val testNodes = json["testNodes"]?.jsonArray

        if (devices == null || testNodes == null) {
            verbosePrinter?.invoke("xcresult JSON missing devices or testNodes. Keys: ${json.keys}")
            return null
        }

        val deviceVersions = devices.associate { device ->
            val obj = device.jsonObject
            val id = obj["deviceId"]?.jsonPrimitive?.content ?: ""
            val osVersion = obj["osVersion"]?.jsonPrimitive?.content ?: "unknown"
            id to osVersion
        }

        val failedDeviceIds = mutableSetOf<String>()
        val passedVersions = mutableSetOf<String>()
        val failureMessages = mutableMapOf<String, MutableList<String>>()

        // For single-device runs, the xcresult tree has no Device nodes
        val singleVersion = if (deviceVersions.size == 1) deviceVersions.values.first() else null

        fun walkNodes(nodes: JsonArray, testCaseName: String?) {
            for (node in nodes) {
                val obj = node.jsonObject
                val nodeType = obj["nodeType"]?.jsonPrimitive?.content
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val result = obj["result"]?.jsonPrimitive?.content
                val children = obj["children"]?.jsonArray
                val nodeId = obj["nodeIdentifier"]?.jsonPrimitive?.content

                when (nodeType) {
                    "Test Case" -> {
                        if (children != null) {
                            val hasDeviceChild = children.any {
                                it.jsonObject["nodeType"]?.jsonPrimitive?.content == "Device"
                            }
                            if (hasDeviceChild) {
                                walkNodes(children, name)
                            } else if (result == "Failed" && singleVersion != null) {
                                val failureMsg = collectFailureMessages(children)
                                val detail = if (failureMsg.isNotBlank()) "$name: $failureMsg" else name
                                failureMessages.getOrPut(singleVersion) { mutableListOf() }.add(detail)
                            } else {
                                walkNodes(children, name)
                            }
                        }
                    }
                    "Device" -> {
                        val version = deviceVersions[nodeId] ?: "unknown"
                        when (result) {
                            "Failed" -> {
                                if (nodeId != null) failedDeviceIds.add(nodeId)
                                val failureMsg = collectFailureMessages(children)
                                val caseName = testCaseName ?: "unknown test"
                                val detail = if (failureMsg.isNotBlank()) "$caseName: $failureMsg" else caseName
                                failureMessages.getOrPut(version) { mutableListOf() }.add(detail)
                            }
                            "Passed" -> passedVersions.add(version)
                        }
                    }
                    else -> {
                        if (children != null) walkNodes(children, testCaseName)
                    }
                }
            }
        }

        walkNodes(testNodes, null)

        DeviceTestResults(
            failedDeviceIds = failedDeviceIds,
            passedVersions = passedVersions,
            failureMessages = failureMessages,
        )
    } catch (e: Exception) {
        verbosePrinter?.invoke("xcresult parse error: ${e.message}")
        null
    }
}

/**
 * Parses the xcresult bundle to extract per-device test failures.
 * Returns a formatted string like:
 *   iOS 17.5: FAILED - testLogin: App did not load.
 *   iOS 26.2: Passed
 * Returns null if the result bundle can't be parsed or no failures were found.
 */
private fun parseXCResultFailures(resultBundlePath: String): String? {
    if (!File(resultBundlePath).exists()) {
        verbosePrinter?.invoke("xcresult bundle not found at: $resultBundlePath")
        return null
    }

    val process = ProcessBuilder(
        "xcrun", "xcresulttool", "get", "test-results", "tests",
        "--path", resultBundlePath, "--compact"
    ).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        verbosePrinter?.invoke("xcresulttool failed (exit $exitCode): ${output.take(500)}")
        return null
    }

    return try {
        val json = Json.parseToJsonElement(output).jsonObject
        val devices = json["devices"]?.jsonArray
        val testNodes = json["testNodes"]?.jsonArray

        if (devices == null || testNodes == null) {
            verbosePrinter?.invoke("xcresult JSON missing devices or testNodes. Keys: ${json.keys}")
            return null
        }

        // Build deviceId -> osVersion map
        val deviceVersions = devices.associate { device ->
            val obj = device.jsonObject
            val id = obj["deviceId"]?.jsonPrimitive?.content ?: ""
            val osVersion = obj["osVersion"]?.jsonPrimitive?.content ?: "unknown"
            id to osVersion
        }

        // Tree structure: Test Plan → UI test bundle → Test Suite → Test Case → Device → Repetition → Failure Message
        // For single-device runs, Device nodes may be absent; failures appear directly under Test Case.
        val deviceResults = mutableMapOf<String, MutableList<String>>()
        val devicesPassed = mutableSetOf<String>()
        val singleVersion = if (deviceVersions.size == 1) deviceVersions.values.first() else null

        fun walkNodes(nodes: JsonArray, testCaseName: String?) {
            for (node in nodes) {
                val obj = node.jsonObject
                val nodeType = obj["nodeType"]?.jsonPrimitive?.content
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val result = obj["result"]?.jsonPrimitive?.content
                val children = obj["children"]?.jsonArray
                val nodeId = obj["nodeIdentifier"]?.jsonPrimitive?.content

                when (nodeType) {
                    "Test Case" -> {
                        if (children != null) {
                            val hasDeviceChild = children.any {
                                it.jsonObject["nodeType"]?.jsonPrimitive?.content == "Device"
                            }
                            if (hasDeviceChild) {
                                walkNodes(children, name)
                            } else if (result == "Failed" && singleVersion != null) {
                                val failureMsg = collectFailureMessages(children)
                                val detail = if (failureMsg.isNotBlank()) "$name: $failureMsg" else name
                                deviceResults.getOrPut(singleVersion) { mutableListOf() }.add(detail)
                            } else {
                                walkNodes(children, name)
                            }
                        }
                    }
                    "Device" -> {
                        // nodeIdentifier is the device UUID, match to deviceVersions
                        val version = deviceVersions[nodeId] ?: "unknown"
                        when (result) {
                            "Failed" -> {
                                val failureMsg = collectFailureMessages(children)
                                val caseName = testCaseName ?: "unknown test"
                                val detail = if (failureMsg.isNotBlank()) "$caseName: $failureMsg" else caseName
                                deviceResults.getOrPut(version) { mutableListOf() }.add(detail)
                            }
                            "Passed" -> devicesPassed.add(version)
                        }
                    }
                    else -> {
                        if (children != null) walkNodes(children, testCaseName)
                    }
                }
            }
        }

        walkNodes(testNodes, null)

        if (deviceResults.isEmpty()) {
            verbosePrinter?.invoke("xcresult: no failure details found despite test failure")
            return null
        }

        val allVersions = (deviceVersions.values + devicesPassed + deviceResults.keys).toSortedSet()
        if (allVersions.isEmpty()) {
            verbosePrinter?.invoke("xcresult: no device versions found in test nodes")
            return null
        }

        allVersions.joinToString("\n") { version ->
            val failures = deviceResults[version]
            when {
                failures != null -> "iOS $version: FAILED - ${failures.joinToString("; ")}"
                version in devicesPassed -> "iOS $version: Passed"
                else -> "iOS $version: FAILED (no details captured)"
            }
        }
    } catch (e: Exception) {
        verbosePrinter?.invoke("xcresult parse error: ${e.message}")
        null
    }
}

private fun collectFailureMessages(children: JsonArray?): String {
    if (children == null) return ""
    return children.mapNotNull { child ->
        val obj = child.jsonObject
        val nodeType = obj["nodeType"]?.jsonPrimitive?.content
        val name = obj["name"]?.jsonPrimitive?.content
        when (nodeType) {
            "Failure Message" -> name
            "Repetition", "Test Case Run" -> {
                val nested = collectFailureMessages(obj["children"]?.jsonArray)
                nested.ifBlank { null }
            }
            else -> null
        }
    }.distinct().joinToString("; ")
}
