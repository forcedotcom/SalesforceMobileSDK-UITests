package com.salesforce

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

const val MIN_API_LEVEL = 28
const val MAX_API_LEVEL = 36
const val ANDROID_TEST_CLASS_DIR = "com.salesforce.mobilesdk.mobilesdkuitest.login"

fun runTests(appInfo: AppInfo, iOSVersions: List<String>, iOSDevice: String, useFirebase: Boolean) {
    when (appInfo.os) {
        OS.ANDROID -> {
            if (useFirebase) {
                runAndroidTestsFirebase(appInfo)
            } else {
                runAndroidTestsLocal(appInfo)
            }
        }
        OS.IOS -> {
            copyIosTestConfig()
            val simulators = createAndInstallIosSimulators(appInfo, iOSVersions, iOSDevice)
            // Update banner title with resolved iOS versions
            PanelProgressBarMaker.title =
                "Testing ${appInfo.appName} (iOS ${simulators.joinToString(", ") { it.iOSVersion }})"
            runIosTests(appInfo, simulators)
        }
    }

    progressBanner?.update {
        context = context.pass()
        completed += 1
    }
    progressBanner?.finish()
}

private fun runAndroidTestsLocal(appInfo: AppInfo) {
    installAndroidApp(appInfo)

    // Grant Push
    "adb shell pm grant ${appInfo.packageName} android.permission.POST_NOTIFICATIONS".runCommand()

    // TestOrchestrator params
    val testClass = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) "NativeLoginTest" else "LoginTest"
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

    val result = "./gradlew $classParam $packageParam $complexHybridParam connectedAndroidTest"
        .split(" ").filter { it.isNotEmpty() }.runCommandCapture(workingDir = ANDROID_TEST_DIR)

    if (result.exitCode != 0) {
        throw Exception(parseTestFailure(result.output))
    }
}

private fun runAndroidTestsFirebase(appInfo: AppInfo) {
    progressBanner?.update {
        context = context.advance("Compile Tests")
        completed += 1
    }
    verbosePrinter?.invoke("Compiling TestOrchestrator APK")

    val buildResult = "./gradlew app:assembleAndroidTest"
        .split(" ").runCommandCapture(workingDir = ANDROID_TEST_DIR)
    if (buildResult.exitCode != 0) {
        throw Exception("TestOrchestrator APK failed to build.\n${buildResult.parseBuildFailure()}")
    }

    progressBanner?.update {
        context = context.advance("Run Login TestOrchestrator")
        completed += 1
    }
    verbosePrinter?.invoke("Testing App with Firebase")

    val testClass = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) "NativeLoginTest" else "LoginTest"
    var devices = ""
    for (level in MIN_API_LEVEL..MAX_API_LEVEL) {
        devices += "--device model=MediumPhone.arm,version=$level,locale=en,orientation=portrait "
    }

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
            --environment-variables class=${ANDROID_TEST_CLASS_DIR}.$testClass,packageName=${appInfo.packageName}${appInfo.complexHybridType?.let { ",complexHybrid=$it" } ?: ""}
            --no-performance-metrics 
            --no-auto-google-login 
            --num-flaky-test-attempts=1
    """.trimIndent().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        .runCommandCapture(workingDir = ANDROID_TEST_DIR).let { result ->
            if (result.exitCode != 0) {
                throw Exception(parseTestFailure(result.output))
            }
        }
}

private fun runIosTests(appInfo: AppInfo, simulators: List<SimulatorInfo>) {
    val testScheme = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) "NativeLoginTest" else "LoginTest"
    val versionsLabel = simulators.joinToString(", ") { it.iOSVersion }

    // Clean up previous test results
    val resultBundlePath = File(IOS_TEST_DIR, "test_output/${appInfo.appName}")
    resultBundlePath.deleteRecursively()

    progressBanner?.update {
        context = context.advance("Run Login Tests (iOS $versionsLabel)")
        completed += 1
    }
    verbosePrinter?.invoke("Running Login Tests (iOS $versionsLabel)")

    val testCommand = buildList {
        addAll(listOf(
            "xcodebuild", "test",
            "-project", "SalesforceMobileSDK-UITest.xcodeproj",
            "-scheme", testScheme,
        ))
        for (sim in simulators) {
            addAll(listOf("-destination", "platform=iOS Simulator,id=${sim.simId}"))
        }
        addAll(listOf(
            "-resultBundlePath", "test_output/${appInfo.appName}",
            "-retry-tests-on-failure",
            "-test-iterations", "2",
            "TEST_APP_BUNDLE=${appInfo.packageName}",
        ))
        appInfo.complexHybridType?.let { add("COMPLEX_HYBRID=$it") }
    }
    val result = testCommand.runCommandCapture(workingDir = IOS_TEST_DIR)

    // Shutdown all simulators
    for (sim in simulators) {
        "xcrun simctl shutdown ${sim.simId}".runCommand(suppressErrors = true)
    }

    if (result.exitCode != 0) {
        val resultBundleAbsPath = File(IOS_TEST_DIR, "test_output/${appInfo.appName}").absolutePath
        val xcresultSummary = parseXCResultFailures(resultBundleAbsPath)
        throw Exception(xcresultSummary ?: parseTestFailure(result.output))
    }
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

/**
 * Parses the xcresult bundle to extract per-device test failures.
 * Returns a formatted string like:
 *   iOS 17.5: FAILED - testLogin: App did not load.
 *   iOS 26.2: Passed
 * Returns null if the result bundle can't be parsed.
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
        // Device nodes are INSIDE Test Case nodes, identified by nodeIdentifier matching deviceId
        val deviceResults = mutableMapOf<String, MutableList<String>>()
        val devicesPassed = mutableSetOf<String>()

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
                        // Pass test case name down so Device children know which test they belong to
                        if (children != null) walkNodes(children, name)
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

        val allVersions = (deviceVersions.values + devicesPassed + deviceResults.keys).toSortedSet()
        if (allVersions.isEmpty()) {
            verbosePrinter?.invoke("xcresult: no device versions found in test nodes")
            return null
        }

        allVersions.joinToString("\n") { version ->
            val failures = deviceResults[version]
            if (failures != null) {
                "iOS $version: FAILED - ${failures.joinToString("; ")}"
            } else {
                "iOS $version: Passed"
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
