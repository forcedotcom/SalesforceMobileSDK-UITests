package com.salesforce

import com.salesforce.Test.Companion.ADB
import com.salesforce.Test.Companion.SIM_NAME
import com.salesforce.util.progress
import com.salesforce.util.runCommand
import com.salesforce.util.verbosePrinter
import java.io.File

fun installAndroidApp(appInfo: AppInfo) {
    // Push config to device so androidTestConfig can load it
    "$ADB push shared/test/android/ui_test_config.json /data/local/tmp/ui_test_config.json".runCommand()
    "$ADB uninstall ${appInfo.packageName}".runCommand()

    progress?.update {
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

    progress?.update {
        context = context.advance("Create Simulator")
        completed += 1
    }
    verbosePrinter?.invoke("Creating Simulator")
    val iosRuntime = iOSVersion.replace(".", "-")
    val createProcess = ProcessBuilder(
        "xcrun", "simctl", "create", SIM_NAME,
        "com.apple.CoreSimulator.SimDeviceType.$iOSDevice",
        "com.apple.CoreSimulator.SimRuntime.iOS-$iosRuntime"
    ).redirectErrorStream(true).start()
    val simId = createProcess.inputStream.bufferedReader().readText().trim()
    val createExitCode = createProcess.waitFor()
    if (createExitCode != 0) {
        throw Exception("Failed to create simulator (exit $createExitCode): $simId")
    }

    progress?.update {
        context = context.advance("Boot Simulator")
        completed += 1
    }
    verbosePrinter?.invoke("Booting Simulator")
    "xcrun simctl boot $simId".runCommand()
    Thread.sleep(3000)

    progress?.update {
        context = context.advance("Install App")
        completed += 1
    }
    verbosePrinter?.invoke("Installing App")
    val iosRoot = if (appInfo.isHybrid) "${appInfo.appPath}/platforms/ios" else appInfo.appPath
    val buildPath = when {
        File("$iosRoot/DerivedData/Build/").exists() -> "$iosRoot/DerivedData/Build"
        File("$iosRoot/Build/").exists() -> "$iosRoot/Build"
        else -> throw Exception("${appInfo.appName}.app could not be found.")
    }
    val configuration = if (appInfo.debugBuild) "Debug" else "Release"
    "xcrun simctl install booted $buildPath/Products/$configuration-iphonesimulator/${appInfo.appName}.app".runCommand()
}