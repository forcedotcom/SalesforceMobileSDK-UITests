package com.salesforce

import com.salesforce.Test.Companion.ANDROID_TEST_DIR
import com.salesforce.Test.Companion.GCLOUD_RESULTS_DIR
import com.salesforce.Test.Companion.IOS_TEST_DIR
import com.salesforce.Test.Companion.SIM_NAME
import com.salesforce.util.progress
import com.salesforce.util.runCommand
import com.salesforce.util.runCommandCapture
import com.salesforce.util.verbosePrinter
import jdk.internal.platform.Container.metrics
import jdk.internal.vm.vector.VectorSupport.test
import sun.security.jgss.GSSUtil.login
import java.io.File

const val MIN_API_LEVEL = 28
const val MAX_API_LEVEL = 36
const val ANDROID_TEST_CLASS_DIR = "com.salesforce.mobilesdk.mobilesdkuitest.login"

fun runTests(appInfo: AppInfo, iOSVersion: String, useFirebase: Boolean) {
    when (appInfo.os) {
        OS.ANDROID if useFirebase -> runAndroidTestsFirebase(appInfo)
        OS.ANDROID if !useFirebase -> runAndroidTestsLocal(appInfo)
        OS.IOS -> runIosTestsLocally(appInfo, iOSVersion)
        else -> {}
    }

    progress?.update {
        context = context.pass()
        completed += 1
    }
    Thread.sleep(500)
    progress?.stop()
}

private fun runAndroidTestsLocal(appInfo: AppInfo) {
    installAndroidApp(appInfo)

    // Grant Push
    "adb shell pm grant ${appInfo.packageName} android.permission.POST_NOTIFICATIONS".runCommand()

    // Test params
    val testClass = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) "TestNativeLogin" else "TestLogin"
    val classParam =  "-Pandroid.testInstrumentationRunnerArguments.class=${ANDROID_TEST_CLASS_DIR}.$testClass"
    val packageParam = "-Pandroid.testInstrumentationRunnerArguments.packageName=${appInfo.packageName}"

    progress?.update {
        context = context.advance("Run Login Test")
        completed += 1
    }
    verbosePrinter?.invoke("Running Login Test")

    val result = "./gradlew $classParam $packageParam connectedAndroidTest"
        .split(" ").runCommandCapture(workingDir = ANDROID_TEST_DIR)

    if (result.exitCode != 0) {
        throw Exception(parseTestFailure(result.output))
    }
}




private fun runAndroidTestsFirebase(appInfo: AppInfo) {
    progress?.update {
        context = context.advance("Run Login Test")
        completed += 1
    }
    verbosePrinter?.invoke("Testing App with Firebase")

    val testClass = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) "TestNativeLogin" else "TestLogin"
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
            --environment-variables class=${ANDROID_TEST_CLASS_DIR}.$testClass,packageName=${appInfo.packageName}
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

private fun runIosTestsLocally(appInfo: AppInfo, iOSVersion: String) {
    installIosApp(appInfo, iOSVersion)

    // Determine test scheme
    val testScheme = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) "TestNativeLogin" else "TestLogin"

    // Clean up previous test results
    val resultBundlePath = File(IOS_TEST_DIR, "test_output/${appInfo.appName}")
    resultBundlePath.deleteRecursively()

    progress?.update {
        context = context.advance("Run Login Tests")
        completed += 1
    }
    verbosePrinter?.invoke("Running Login Tests")
    val user = iosTestConfig.getUser(KnownLoginHostConfig.REGULAR_AUTH, KnownUserConfig.FIRST)
    val result = listOf(
        "xcodebuild", "test",
        "-project", "SalesforceMobileSDK-UITest.xcodeproj",
        "-scheme", testScheme,
        "-destination", "platform=iOS Simulator,name=$SIM_NAME",
        "-resultBundlePath", "test_output/${appInfo.appName}",
        "TEST_APP_BUNDLE=${appInfo.packageName}",
        "USERNAME=${user.username}",
        "PASSWORD=${user.password}",
    ).runCommandCapture(workingDir = IOS_TEST_DIR)

    // Shutdown simulator
    "xcrun simctl shutdown $SIM_NAME".runCommand()

    if (result.exitCode != 0) {
        throw Exception(parseTestFailure(result.output))
    }
}

private fun parseTestFailure(output: String?): String {
    if (output.isNullOrBlank()) return "Test failed with no output."

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
            // Capture FAILED/error lines and their indented continuation
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

    return if (failureLines.isNotEmpty()) {
        failureLines.joinToString("\n")
    } else {
        val lastLines = lines.filter { it.isNotBlank() }.takeLast(5)
        if (lastLines.isNotEmpty()) {
            lastLines.joinToString("\n") { it.trim() }
        } else {
            "Test failed. Unable to parse failure details from output."
        }
    }
}
