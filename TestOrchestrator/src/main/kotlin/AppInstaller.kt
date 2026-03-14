package com.salesforce

import com.salesforce.Test.Companion.ADB
import com.salesforce.util.progress
import com.salesforce.util.runCommand
import com.salesforce.util.verbosePrinter

fun installAndroidApp(appPath: String, isDebug: Boolean = false) {
    val apkPath = if (isDebug) "debug/app-debug.apk" else "release/app-release-unsigned.apk"

    progress?.update {
        context = context.advance("Install App")
        completed += 1
    }
    verbosePrinter?.invoke("Install App")
    "$ADB install -r app/build/outputs/apk/$apkPath"
        .runCommand(workingDir = appPath)
}
