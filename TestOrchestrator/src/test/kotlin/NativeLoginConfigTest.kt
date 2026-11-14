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

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativeLoginConfigTest {
    private val appConfig = AppConfig(
        name = "native_login",
        consumerKey = "KEY.with-chars_1",
        redirectUri = "https://community.example.com/headless/services/oauth2/echo",
        scopes = "api web",
    )
    private val communityUrl = "https://community.example.com/headless"

    private fun appDir(os: OS, fileName: String, content: String): AppInfo {
        val dir = createTempDirectory("nativelogin").toFile().apply { deleteOnExit() }
        val nested = File(dir, "app/src/main").apply { mkdirs() }
        File(nested, fileName).writeText(content)
        val name = if (os == OS.IOS) "iosnativelogin" else "androidnativelogin"
        return AppInfo(os = os, appName = name, appPath = dir.path, packageName = "com.salesforce.$name")
    }

    private val swiftSource = """
        let clientId = "your-client-id"
        let redirectUri = "your-redirect-uri"
        let loginUrl = "your-community-url"
        assert(clientId != "your-client-id", "Please add your Native Login client id.")
        assert(loginUrl != "your-community-url", "Please add your Native Login community url.")
    """.trimIndent()

    private val kotlinSource = """
        val clientId = "your-client-id"
        val redirectUri = "your-redirect-uri"
        val loginUrl = "your-community-url"
        check(clientId != "your-client-id") { "Please add your Native Login client id." }
        check(loginUrl != "your-community-url") { "Please add your Native Login community url." }
    """.trimIndent()

    private fun source(info: AppInfo, fileName: String) =
        File(info.appPath).walkTopDown().first { it.name == fileName }.readText()

    @Test
    fun rewritesDeclarationsAndKeepsStartupChecksOnIos() {
        val info = appDir(OS.IOS, "SceneDelegate.swift", swiftSource)
        applyNativeLoginConfig(info, appConfig, communityUrl)

        val result = source(info, "SceneDelegate.swift")
        assertTrue(result.contains("""let clientId = "KEY.with-chars_1""""))
        assertTrue(result.contains("""let redirectUri = "${appConfig.redirectUri}""""))
        assertTrue(result.contains("""let loginUrl = "$communityUrl""""))
        assertTrue(result.contains("""assert(clientId != "your-client-id""""))
        assertTrue(result.contains("""assert(loginUrl != "your-community-url""""))
    }

    @Test
    fun rewritesDeclarationsAndKeepsStartupChecksOnAndroid() {
        val info = appDir(OS.ANDROID, "MainApplication.kt", kotlinSource)
        applyNativeLoginConfig(info, appConfig, communityUrl)

        val result = source(info, "MainApplication.kt")
        assertTrue(result.contains("""val clientId = "KEY.with-chars_1""""))
        assertTrue(result.contains("""val loginUrl = "$communityUrl""""))
        assertTrue(result.contains("""check(clientId != "your-client-id")"""))
        assertTrue(result.contains("""check(loginUrl != "your-community-url")"""))
    }

    @Test
    fun throwsWhenADeclarationIsMissing() {
        val info = appDir(OS.IOS, "SceneDelegate.swift", swiftSource.replace("let loginUrl", "let communityUrl"))
        val error = assertFailsWith<Exception> { applyNativeLoginConfig(info, appConfig, communityUrl) }
        assertTrue(error.message!!.contains("loginUrl"))
    }

    @Test
    fun throwsWhenADeclarationHasATypeAnnotation() {
        val info = appDir(OS.ANDROID, "MainApplication.kt", kotlinSource.replace("val clientId =", "val clientId: String ="))
        assertFailsWith<Exception> { applyNativeLoginConfig(info, appConfig, communityUrl) }
    }

    @Test
    fun throwsWhenTheSourceFileIsMissing() {
        val info = appDir(OS.IOS, "Other.swift", swiftSource)
        assertFailsWith<Exception> { applyNativeLoginConfig(info, appConfig, communityUrl) }
    }

    @Test
    fun escapesQuotesBackslashesAndKotlinTemplates() {
        assertEquals("""a\"b\\c""", escapeForSourceLiteral("""a"b\c""", OS.IOS))
        assertEquals("""a${'$'}b""", escapeForSourceLiteral("a${'$'}b", OS.IOS))
        assertEquals("""a\${'$'}b""", escapeForSourceLiteral("a${'$'}b", OS.ANDROID))
    }

    @Test
    fun valuesWithSpecialCharactersProduceValidLiterals() {
        val special = appConfig.copy(consumerKey = "k\"ey${'$'}x")
        val info = appDir(OS.ANDROID, "MainApplication.kt", kotlinSource)
        applyNativeLoginConfig(info, special, communityUrl)
        assertTrue(source(info, "MainApplication.kt").contains("""val clientId = "k\"ey\${'$'}x""""))
    }

    @Test
    fun onlyAppNamesEndingInNativeLoginAreNativeLoginApps() {
        assertTrue(isNativeLoginAppName("iosnativelogin"))
        assertTrue(isNativeLoginAppName("androidnativelogin"))
        assertEquals(false, isNativeLoginAppName("androidnative"))
        assertEquals(false, isNativeLoginAppName("iosnativeloginextras"))
        assertEquals(false, isNativeLoginAppName("androidmobilesyncexplorerkotlin"))
    }
}
