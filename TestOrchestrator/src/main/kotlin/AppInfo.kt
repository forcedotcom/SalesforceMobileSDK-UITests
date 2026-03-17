package com.salesforce

import java.nio.file.Path

data class AppInfo(
    val os: OS,
    val appName: String,
    val appPath: String,
    val packageName: String,
    val isHybrid: Boolean = false,
    val isReact: Boolean = false,
    val debugBuild: Boolean = false,
) {
    val androidRoot = if (isHybrid) "$appPath/platforms/android" else appPath
    val apkPath = "$androidRoot/app/build/outputs/apk/${
        if (debugBuild) {
            "debug/app-debug.apk"
        } else {
            "release/app-release-unsigned.apk"
        }
    }"
}

enum class OS {
    ANDROID,
    IOS,
}

enum class AppType(val scriptValue: String) {
    NATIVE("native"),
    NATIVE_KOTLIN("native_kotlin"),
    NATIVE_SWIFT("native_swift"),
    HYBRID_LOCAL("hybrid_local"),
    HYBRID_REMOTE("hybrid_remote"),
    COMPLEX_HYBRID_ACCOUNT_EDITOR("complex_hybrid_accounteditor"),
    COMPLEX_HYBRID_MOBILE_SYNC("complex_hybrid_mobilesyncexplorer"),
    REACT_NATIVE("react_native"),
    ;
}

abstract class AppSource(open val os: OS) {
    val osName by lazy { os.name.lowercase() }
    abstract val appName: String
    val isHybrid by lazy { appName.contains("hybrid") }
    val isComplexHybrid by lazy { appName.contains("complex") }
    val isReact by lazy { appName.contains("react") }

    data class ByType(override val os: OS, val type: AppType) : AppSource(os) {
        // <android/ios><native/hybridlocal/etc>
        override val appName = "${osName}${type.scriptValue.replace(oldValue = "_", newValue = "")}"
    }

    data class ByTemplate(override val os: OS, val template: String) : AppSource(os) {
        override val appName by lazy {
            val templateName = template.split("/").last()
                .split("#").first()
                .replace(Regex("(?<=[a-z])[A-Z]+"), "_$0")
                .lowercase()
                .replace(oldValue = "_", newValue = "")
                .removeSuffix("template")

            if (templateName == "mobilesyncexplorerreactnative"
                || templateName.startsWith("android")
                || templateName.startsWith("ios")) {

                templateName
            } else {
                "${osName}$templateName"
            }
        }
    }
}