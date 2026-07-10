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

import com.salesforce.TestOrchestrator.Companion.ANDROID_BUILD_DIR
import com.salesforce.util.progressBanner
import com.salesforce.util.runCommandCapture
import com.salesforce.util.verbosePrinter
import java.io.File

fun compileApp(
    appInfo: AppInfo,
    debug: Boolean = false,
) {
    val configuration = if (debug) "Debug" else "Release"

    with(appInfo) {
        progressBanner?.update {
            context = context.advance("Compile App")
            completed += 1
        }
        verbosePrinter?.invoke("Compiling App")

        when (os) {
            OS.ANDROID -> {
                val buildCommand = buildList {
                    add("./gradlew")
                    add(if (isReact) "app:assemble$configuration" else "assemble$configuration")
                    if (isReact) {
                        add("-PreactNativeDevServerPort=8081")
                        add("--no-daemon")
                        if (!debug) {
                            // Skip lint vital checks — older RN templates can have
                            // Gradle task-dependency issues with newer AGP versions.
                            add("-x")
                            add("lintVitalRelease")
                        }
                    }
                }
                val buildResult = buildCommand.runCommandCapture(androidRoot)
                buildResult.throwIfFailed(
                    appPath,
                    label = "android_build",
                    message = "Android build failed.\n${buildResult.parseBuildFailure()}",
                )

                if (!debug) {
                    signReleaseApk(apkPath)
                }
            }
            OS.IOS -> {
                val workspaceOrProject = if (File(iosRoot, "$iosXcodeName.xcworkspace").exists()) {
                    listOf("-workspace", "$iosXcodeName.xcworkspace")
                } else {
                    listOf("-project", "$iosXcodeName.xcodeproj")
                }
                // Older RN templates bundle the fmt library (via Flipper)
                // which fails to compile with newer Xcode/Clang due to
                // consteval changes. Patch the header to force it off.
                if (isReact) patchFmtConsteval(iosRoot)

                val buildResult = (listOf("xcodebuild", "build") + workspaceOrProject + listOf(
                    "-scheme", iosXcodeName,
                    "-sdk", "iphonesimulator",
                    "-destination", "generic/platform=iOS Simulator",
                    "-configuration", configuration,
                    "-derivedDataPath", "./DerivedData",
                    "GENERATE_ASSET_SYMBOLS=NO",
                    "ASSETCATALOG_COMPILER_GENERATE_ASSET_SYMBOLS=NO",
                )).runCommandCapture(iosRoot)
                buildResult.throwIfFailed(
                    appPath,
                    label = "ios_build",
                    message = "iOS build failed.\n${buildResult.parseBuildFailure()}",
                )
            }
        }
    }
}

private fun signReleaseApk(apkPath: String) {
    val keystoreFile = File("uitest.keystore")
    val keystorePass = "test12"

    progressBanner?.update {
        context = context.advance("Sign Release APK")
        completed += 1
    }
    verbosePrinter?.invoke("Sign Release APK")

    // Create Keystore
    if (!keystoreFile.exists()) {
        val keystoreResult = listOf(
            "keytool", "-genkey", "-v",
            "-keystore", keystoreFile.path,
            "-alias", "react",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "1",
            "-storepass", keystorePass,
            "-keypass", keystorePass,
            "-dname", "CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown"
        ).runCommandCapture()
        if (keystoreResult.exitCode != 0) {
            throw Exception("Keystore creation failed.\n${keystoreResult.output?.trim()}")
        }
    }

    // Sign
    val signResult = listOf(
        "$ANDROID_BUILD_DIR/apksigner", "sign",
        "--ks", "uitest.keystore",
        "--ks-pass", "pass:$keystorePass",
        apkPath
    ).runCommandCapture()
    if (signResult.exitCode != 0) {
        throw Exception("APK signing failed.\n${signResult.output?.trim()}")
    }
}

private fun patchFmtConsteval(iosRoot: String) {
    val fmtBase = File(iosRoot, "Pods/fmt/include/fmt/base.h")
    if (!fmtBase.exists()) return
    verbosePrinter?.invoke("Patching fmt base.h to disable consteval")
    listOf(
        "sed", "-i", "",
        "s/#  define FMT_USE_CONSTEVAL 1/#  define FMT_USE_CONSTEVAL 0/g",
        fmtBase.absolutePath
    ).runCommandCapture()
}
