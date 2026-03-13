package com.salesforce

import com.salesforce.Test.Companion.ADB
import com.salesforce.Test.Companion.ANDROID_TEST_DIR
import com.salesforce.Test.Companion.GCLOUD_RESULTS_DIR

fun runTests(
    appInfo: AppInfo,
    iOSVersion: String,
    useFirebase: Boolean,
    print: Printer,
) {
    when (appInfo.os) {
        OS.ANDROID if useFirebase -> runAndroidTestsFirebase(appInfo, print)
        OS.ANDROID if !useFirebase -> runAndroidTestsLocal(appInfo, print)
        OS.IOS -> runIosTestsLocally(appInfo, iOSVersion, print)
        else -> {}
    }
}

private fun runAndroidTestsLocal(appInfo: AppInfo, print: Printer) {
    // Push config to device so androidTestConfig can load it
    "$ADB push shared/test/android/ui_test_config.json /data/local/tmp/ui_test_config.json".runCommand()

    "$ADB uninstall ${appInfo.packageName}".runCommand()

    print("Installing App")
    print("apk path: ${appInfo.apkPath}")

    "$ADB install -r ${appInfo.apkPath}".runCommand()

    print("Running Tests Locally")

    print("package name: ${appInfo.packageName}")

    // Grant Push
    "adb shell pm grant ${appInfo.packageName} android.permission.POST_NOTIFICATIONS".runCommand()

    // Test params
    val classParam =  "-Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.mobilesdk.mobilesdkuitest.login.LoginTests"
    val packageParam = "-Pandroid.testInstrumentationRunnerArguments.packageName=${appInfo.packageName}"

    val testResult = "./gradlew $classParam $packageParam connectedAndroidTest"
        .runCommand(workingDir = ANDROID_TEST_DIR)
}




private fun runAndroidTestsFirebase(appInfo: AppInfo, log: Printer) {
    val password = ""



    """
        gcloud firebase test android run \
            --project mobile-apps-firebase-test \
            --type instrumentation \
            --app=../${appInfo.appPath}app/build/outputs/apk/${appInfo.apkPath} \
            --test=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
            #{devices}
            --results-history-name=UITest-${appInfo.appName} \
            --results-dir=$GCLOUD_RESULTS_DIR \
            --environment-variables class=#{test_class},packageName=${appInfo.packageName},password=$password,firebase=true#{optional_vars} \
            --no-performance-metrics --no-auto-google-login --num-flaky-test-attempts=1"
    """.trimIndent().runCommand(workingDir = ANDROID_TEST_DIR)
}

private const val SIM_NAME = "testsim"
private const val IOS_TEST_DIR = "./iOS/"

private fun runIosTestsLocally(appInfo: AppInfo, iOSVersion: String, print: Printer) {
    // Clean up any existing test simulator
    print("Cleaning up existing simulators")
    do {
        val result = "xcrun simctl delete $SIM_NAME".runCommand()
    } while (result == 0)

    // Create simulator
    print("Creating test simulator (iOS $iOSVersion)")
    val iosRuntime = iOSVersion.replace(".", "-")
    print("iosRuntime: $iosRuntime")

    val createProcess = ProcessBuilder(
        "xcrun", "simctl", "create", SIM_NAME,
        "com.apple.CoreSimulator.SimDeviceType.iPhone-SE-3rd-generation",
        "com.apple.CoreSimulator.SimRuntime.iOS-$iosRuntime"
    ).redirectErrorStream(true).start()
    val simId = createProcess.inputStream.bufferedReader().readText().trim()
    val createExitCode = createProcess.waitFor()
    if (createExitCode != 0) {
        throw Exception("Failed to create simulator (exit $createExitCode): $simId")
    }
    print("Simulator created: $simId")

    // Boot simulator
    print("Booting simulator: $simId")
    "xcrun simctl boot $simId".runCommand()
    Thread.sleep(3000)

    // Install app on simulator
    print("Installing app on simulator")
    val buildPath = when {
        java.io.File("${appInfo.appPath}/DerivedData/Build/").exists() -> "${appInfo.appPath}/DerivedData/Build"
        java.io.File("${appInfo.appPath}/Build/").exists() -> "${appInfo.appPath}/Build"
        else -> throw Exception("${appInfo.appName}.app could not be found.")
    }
    val configuration = if (appInfo.debugBuild) "Debug" else "Release"
    "xcrun simctl install booted $buildPath/Products/$configuration-iphonesimulator/${appInfo.appName}.app".runCommand()

    // Determine test scheme
    val testScheme = if (appInfo.appName.contains("nativelogin", ignoreCase = true)) "TestNativeLogin" else "TestLogin"

    // Clean up previous test results
    val resultBundlePath = java.io.File(IOS_TEST_DIR, "test_output/${appInfo.appName}")
    resultBundlePath.deleteRecursively()

    print("Running iOS Tests")
    val user = iosTestConfig.getUser(KnownLoginHostConfig.REGULAR_AUTH, KnownUserConfig.FIRST)
    val testResult = listOf(
        "xcodebuild", "test",
        "-project", "SalesforceMobileSDK-UITest.xcodeproj",
        "-scheme", testScheme,
        "-destination", "platform=iOS Simulator,name=$SIM_NAME",
        "-resultBundlePath", "test_output/${appInfo.appName}",
        "TEST_APP_BUNDLE=${appInfo.packageName}",
        "USERNAME=${user.username}",
        "PASSWORD=${user.password}",
    ).runCommand(workingDir = IOS_TEST_DIR)

    // Shutdown simulator
    "xcrun simctl shutdown $SIM_NAME".runCommand()

    if (testResult != 0) {
        throw Exception("iOS tests failed with exit code: $testResult")
    }
}
