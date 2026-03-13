package com.salesforce

import com.salesforce.Test.Companion.ANDROID_BUILD_DIR
import java.io.File

fun compileApp(
    appInfo: AppInfo,
    debug: Boolean = false,
    knownAppConfig: KnownAppConfig = KnownAppConfig.ECA_OPAQUE,
    log: Printer,
) {
    val testConfig = if (appInfo.os == OS.ANDROID) androidTestConfig else iosTestConfig
    val loginUrl = testConfig.loginHosts.first().url
    val appConfig = testConfig.getApp(knownAppConfig)
    val configuration = if (debug) "Debug" else "Release"

    log("Setting Login URL")
    setLoginUrl(appInfo, loginUrl)

    log("Setting OAuth Config")
    with(appInfo) {
        when {
            isHybrid -> {
                val bootConfig = File(appPath, "www/bootconfig.json")
                updateJsonBootConfig(bootConfig, appConfig)
            }

            os == OS.ANDROID -> {
                val bootConfig = File(appPath, "app/src/main/res/values/bootconfig.xml")
                updateXmlBootConfig(bootConfig, appConfig)
            }

            else -> {
                val bootConfig = File(appPath, "$appName/bootconfig.plist")
                updatePlistBootConfig(bootConfig, appConfig)
            }
        }

        log("Compiling App")
        when (os) {
            OS.ANDROID -> {
                // TODO: Does RN need  -PreactNativeDevServerPort=8081 --no-daemon ???
                "./gradlew assemble$configuration".runCommand(appPath)

                if (!debug) {
                    log("Signing Release APK")
                    signReleaseApk(apkPath)
                }
            }
            OS.IOS -> {
                listOf(
                    "xcodebuild", "build",
                    "-workspace", "$appName.xcworkspace",
                    "-scheme", appName,
                    "-sdk", "iphonesimulator",
                    "-configuration", configuration,
                    "-derivedDataPath", "./DerivedData"
                ).runCommand(appPath)
            }
        }
    }
}

private fun setLoginUrl(appInfo: AppInfo, loginUrl: String) {
    when (appInfo.os) {
        OS.ANDROID -> {
            val serversFile = File(appInfo.appPath, "app/src/main/res/xml/servers.xml")
            serversFile.writeText(
                """<?xml version="1.0" encoding="utf-8"?>
                |<servers>
                |    <server name="Default" url="$loginUrl" />
                |</servers>
                |""".trimMargin()
            )
        }
        OS.IOS -> {
            val plistPath = File(appInfo.appPath, "${appInfo.appName}/Info.plist")
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
        ).runCommand()

        println("Keystore result: $keystoreResult")
    }

    // Sign
    val signApk = listOf(
        "$ANDROID_BUILD_DIR/apksigner", "sign",
        "--ks", "uitest.keystore",
        "--ks-pass", "pass:$keystorePass",
        apkPath
    ).runCommand()
    println("Sign APK result: $signApk")
}
