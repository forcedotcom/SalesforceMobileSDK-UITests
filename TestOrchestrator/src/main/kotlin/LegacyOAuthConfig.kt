/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

import java.io.File

/**
 * LEGACY UPGRADE AUTOMATION (SDK 12.x AND 13.x): v12.2.0 can ignore the requested login host, and
 * v13.2.1 iOS can leave bootconfig placeholders. Delete this file after both 12.x and 13.x
 * upgrade/version coverage are retired.
 */
internal fun applyLegacyOAuthConfig(
    appInfo: AppInfo,
    loginUrl: String,
    appConfig: AppConfig,
) {
    // SDK 12.x ONLY (verified with v12.2.0): --loginserver can be ignored. Remove this call and
    // setLegacyLoginUrl when 12.x coverage is dropped; the v13.2.1-specific fix is below.
    setLegacyLoginUrl(appInfo, loginUrl)

    // SDK 13.x REQUIRED (verified with v13.2.1 iOS): generated bootconfig can retain placeholder
    // OAuth values. These writes are also safe for v12.2.0; retain them while 13.x is supported.
    with(appInfo) {
        when {
            isHybrid && os == OS.ANDROID -> updateJsonBootConfig(
                File(androidRoot, "app/src/main/assets/www/bootconfig.json"),
                appConfig,
            )
            isHybrid && os == OS.IOS -> updateJsonBootConfig(
                File(iosRoot, "www/bootconfig.json"),
                appConfig,
            )
            os == OS.ANDROID -> updateXmlBootConfig(
                File(androidRoot, "app/src/main/res/values/bootconfig.xml"),
                appConfig,
            )
            else -> {
                val resourcesBootConfig = File(iosRoot, "$appName/Resources/bootconfig.plist")
                val bootConfig = if (resourcesBootConfig.exists()) {
                    resourcesBootConfig
                } else {
                    File(iosRoot, "$appName/bootconfig.plist")
                }
                updatePlistBootConfig(bootConfig, appConfig)
            }
        }
    }
}

private fun setLegacyLoginUrl(appInfo: AppInfo, loginUrl: String) {
    // SDK 12.x-specific failure: v12.2.0 can leave login.salesforce.com configured even when the
    // packager receives --loginserver. This shared fallback is also harmless for v13.2.1.
    when (appInfo.os) {
        OS.ANDROID -> {
            val serversFile = File(appInfo.androidRoot, "app/src/main/res/xml/servers.xml")
            serversFile.parentFile.mkdirs()
            serversFile.writeText(
                """<?xml version="1.0" encoding="utf-8"?>
                |<servers>
                |    <server name="Default" url="$loginUrl" />
                |</servers>
                |""".trimMargin(),
            )
        }
        OS.IOS -> {
            val infoPlistCandidates = listOf(
                File(appInfo.iosRoot, "${appInfo.iosXcodeName}/Info.plist"),
                File(
                    appInfo.iosRoot,
                    "${appInfo.iosXcodeName}/${appInfo.iosXcodeName}-Info.plist",
                ),
            )
            val infoPlist = infoPlistCandidates.firstOrNull(File::exists)
                ?: throw IllegalStateException(
                    "Cannot set login server: no app Info.plist found at " +
                        infoPlistCandidates.joinToString { it.path },
                )
            val loginHost = loginUrl.removePrefix("https://").removePrefix("http://")
            val content = infoPlist.readText()
            val loginHostKey = "<key>SFDCOAuthLoginHost</key>"
            val updatedContent = if (content.contains(loginHostKey)) {
                content.replaceFirst(
                    Regex("""(<key>SFDCOAuthLoginHost</key>\s*<string>)[^<]*(</string>)"""),
                    "$1$loginHost$2",
                )
            } else {
                val rootDictionaryEnd = content.lastIndexOf("</dict>")
                check(rootDictionaryEnd >= 0) {
                    "Cannot set login server: no root dictionary found in ${infoPlist.path}."
                }
                content.substring(0, rootDictionaryEnd) +
                    "\t<key>SFDCOAuthLoginHost</key>\n\t<string>$loginHost</string>\n" +
                    content.substring(rootDictionaryEnd)
            }
            infoPlist.writeText(updatedContent)
        }
    }
}

private fun updateXmlBootConfig(file: File, appConfig: AppConfig) {
    var content = file.readText()
    content = content.replace(
        Regex("""(<string name="remoteAccessConsumerKey">)[^<]*(</string>)"""),
        "$1${appConfig.consumerKey}$2",
    )
    content = content.replace(
        Regex("""(<string name="oauthRedirectURI">)[^<]*(</string>)"""),
        "$1${appConfig.redirectUri}$2",
    )
    file.writeText(content)
}

private fun updatePlistBootConfig(file: File, appConfig: AppConfig) {
    var content = file.readText()
    content = content.replace(
        Regex("""(<key>remoteAccessConsumerKey</key>\s*<string>)[^<]*(</string>)"""),
        "$1${appConfig.consumerKey}$2",
    )
    content = content.replace(
        Regex("""(<key>oauthRedirectURI</key>\s*<string>)[^<]*(</string>)"""),
        "$1${appConfig.redirectUri}$2",
    )
    file.writeText(content)
}

private fun updateJsonBootConfig(file: File, appConfig: AppConfig) {
    var content = file.readText()
    content = content.replace(
        Regex("""("remoteAccessConsumerKey"\s*:\s*")[^"]*(")"""),
        "$1${appConfig.consumerKey}$2",
    )
    content = content.replace(
        Regex("""("oauthRedirectURI"\s*:\s*")[^"]*(")"""),
        "$1${appConfig.redirectUri}$2",
    )
    file.writeText(content)
}
