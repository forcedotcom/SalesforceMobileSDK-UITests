package com.salesforce

import com.salesforce.Test.Companion.ADB

fun installAndroidApp(appPath: String, isDebug: Boolean = false, log: Printer) {
    val apkPath = if (isDebug) "debug/app-debug.apk" else "release/app-release-unsigned.apk"

    log("Installing App")
    "$ADB install -r app/build/outputs/apk/$apkPath"
        .runCommand(workingDir = appPath)
}
