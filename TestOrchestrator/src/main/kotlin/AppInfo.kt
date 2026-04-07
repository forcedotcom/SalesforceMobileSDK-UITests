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

data class AppInfo(
    val os: OS,
    val appName: String,
    val appPath: String,
    val packageName: String,
    val isHybrid: Boolean = false,
    val isReact: Boolean = false,
    val debugBuild: Boolean = false,
    val complexHybridType: String? = null,
) {
    val androidRoot = when {
        isHybrid -> "$appPath/platforms/android"
        isReact -> "$appPath/android"
        else -> appPath
    }
    val iosRoot = when {
        isHybrid -> "$appPath/platforms/ios"
        isReact -> "$appPath/ios"
        else -> appPath
    }
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
    COMPLEX_HYBRID_ACCOUNT_EDITOR("accounteditor"),
    COMPLEX_HYBRID_MOBILE_SYNC("mobilesyncexplorer"),
    REACT_NATIVE("react_native"),
    ;
}

abstract class AppSource(open val os: OS) {
    val osName by lazy { os.name.lowercase() }
    abstract val appName: String
    val isHybrid by lazy { appName.contains("hybrid") }
    val isComplexHybrid by lazy {
        when(this) {
            is ByType -> {
                type == AppType.COMPLEX_HYBRID_ACCOUNT_EDITOR
                        || type == AppType.COMPLEX_HYBRID_MOBILE_SYNC
            }
            else -> false
        }
    }
    val complexHybridName: String? by lazy {
        when(this) {
            is ByType -> if (isComplexHybrid) type.scriptValue else null
            else -> null
        }
    }
    val isReact by lazy { appName.contains("react") }

    data class ByType(override val os: OS, val type: AppType) : AppSource(os) {
        // <android/ios><native/hybridlocal/etc>
        override val appName = when(type) {
            AppType.COMPLEX_HYBRID_ACCOUNT_EDITOR, AppType.COMPLEX_HYBRID_MOBILE_SYNC -> "${osName}hybridlocal"
            else -> "${osName}${type.scriptValue.replace(oldValue = "_", newValue = "")}"
        }
    }

    data class ByTemplate(override val os: OS, val template: String) : AppSource(os) {
        override val appName by lazy {
            val templateName = template.split("/").last()
                .split("#").first()
                .replace(Regex("(?<=[a-z])[A-Z]+"), "_$0")
                .lowercase()
                .replace(oldValue = "_", newValue = "")
                .removeSuffix("template")

            if (templateName.startsWith("android")
                || templateName.startsWith("ios")) {

                templateName
            } else {
                "${osName}$templateName"
            }
        }
    }
}