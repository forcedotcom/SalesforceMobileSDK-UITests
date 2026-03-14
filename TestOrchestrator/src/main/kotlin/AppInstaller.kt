package com.salesforce

import com.salesforce.Test.Companion.ADB
import com.salesforce.Util.progress
import com.salesforce.Util.runCommand
import com.salesforce.Util.verbosePrinter

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
