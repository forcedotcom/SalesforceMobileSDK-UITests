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
    knownAppConfig: KnownAppConfig = KnownAppConfig.ECA_OPAQUE,
) {
    val testConfig = if (appInfo.os == OS.ANDROID) androidTestConfig else iosTestConfig
    val loginUrl = testConfig.loginHosts.first().url
    val appConfig = testConfig.getApp(knownAppConfig)
    val configuration = if (debug) "Debug" else "Release"

    setLoginUrl(appInfo, loginUrl)

    progressBanner?.update {
        context = context.advance("Set OAuth Config")
        completed += 1
    }
    verbosePrinter?.invoke("Setting OAuth Config")

    with(appInfo) {
        when {
            isHybrid && os == OS.ANDROID -> {
                val bootConfig = File(androidRoot, "app/src/main/assets/www/bootconfig.json")
                updateJsonBootConfig(bootConfig, appConfig)
            }

            isHybrid && os == OS.IOS -> {
                val bootConfig = File(iosRoot, "www/bootconfig.json")
                updateJsonBootConfig(bootConfig, appConfig)
            }

            os == OS.ANDROID -> {
                val bootConfig = File(androidRoot, "app/src/main/res/values/bootconfig.xml")
                updateXmlBootConfig(bootConfig, appConfig)
            }

            else -> {
                val resourcesBootConfig = File(iosRoot, "$appName/Resources/bootconfig.plist")
                val bootConfig = if (resourcesBootConfig.exists()) resourcesBootConfig
                                 else File(iosRoot, "$appName/bootconfig.plist")
                updatePlistBootConfig(bootConfig, appConfig)
            }
        }

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
                val workspaceOrProject = if (File(iosRoot, "$appName.xcworkspace").exists()) {
                    listOf("-workspace", "$appName.xcworkspace")
                } else {
                    listOf("-project", "$appName.xcodeproj")
                }
                val buildResult = (listOf("xcodebuild", "build") + workspaceOrProject + listOf(
                    "-scheme", appName,
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

private fun setLoginUrl(appInfo: AppInfo, loginUrl: String) {
    progressBanner?.update {
        context = context.advance("Set Login URL")
        completed += 1
    }
    verbosePrinter?.invoke("Setting Login URL")

    when (appInfo.os) {
        OS.ANDROID -> {
            val serversFile = File(appInfo.androidRoot, "app/src/main/res/xml/servers.xml")
            serversFile.parentFile.mkdirs()
            serversFile.writeText(
                """<?xml version="1.0" encoding="utf-8"?>
                |<servers>
                |    <server name="Default" url="$loginUrl" />
                |</servers>
                |""".trimMargin()
            )
        }
        OS.IOS -> {
            val plistName = if (appInfo.isHybrid) "${appInfo.appName}-Info.plist" else "Info.plist"
            val plistPath = File(appInfo.iosRoot, "${appInfo.appName}/$plistName")
            val loginHost = loginUrl.removePrefix("https://").removePrefix("http://")
            val content = plistPath.readText()
            val key = "<key>SFDCOAuthLoginHost</key>"

            if (content.contains(key)) {
                plistPath.writeText(
                    content.replace(
                        Regex("""(<key>SFDCOAuthLoginHost</key>\s*<string>)[^<]*(</string>)"""),
                        "$1$loginHost$2"
                    )
                )
            } else {
                // Insert before closing </dict>
                plistPath.writeText(
                    content.replace(
                        "</dict>",
                        "\t<key>SFDCOAuthLoginHost</key>\n\t<string>$loginHost</string>\n</dict>"
                    )
                )
            }
        }
    }
}

private fun updateXmlBootConfig(file: File, appConfig: AppConfig) {
    var content = file.readText()
    content = content.replace(
        Regex("""(<string name="remoteAccessConsumerKey">)[^<]*(</string>)"""),
        "$1${appConfig.consumerKey}$2"
    )
    content = content.replace(
        Regex("""(<string name="oauthRedirectURI">)[^<]*(</string>)"""),
        "$1${appConfig.redirectUri}$2"
    )
    file.writeText(content)
}

private fun updatePlistBootConfig(file: File, appConfig: AppConfig) {
    var content = file.readText()
    content = content.replace(
        Regex("""(<key>remoteAccessConsumerKey</key>\s*<string>)[^<]*(</string>)"""),
        "$1${appConfig.consumerKey}$2"
    )
    content = content.replace(
        Regex("""(<key>oauthRedirectURI</key>\s*<string>)[^<]*(</string>)"""),
        "$1${appConfig.redirectUri}$2"
    )
    file.writeText(content)
}

private fun updateJsonBootConfig(file: File, appConfig: AppConfig) {
    var content = file.readText()
    content = content.replace(
        Regex(""""remoteAccessConsumerKey"\s*:\s*"[^"]*""""),
        """"remoteAccessConsumerKey": "${appConfig.consumerKey}""""
    )
    content = content.replace(
        Regex(""""oauthRedirectURI"\s*:\s*"[^"]*""""),
        """"oauthRedirectURI": "${appConfig.redirectUri}""""
    )
    file.writeText(content)
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
