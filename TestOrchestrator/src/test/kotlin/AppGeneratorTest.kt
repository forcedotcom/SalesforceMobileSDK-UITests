/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *   following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *   the following disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *   promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce

import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class AppGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `inserts Android DPoP override immediately after hybrid SDK initialization`() {
        val appSource = AppSource.ByType(OS.ANDROID, AppType.HYBRID_REMOTE)
        val appInfo = hybridAppInfo(appSource)
        val sourceFile = androidSourceFile(appInfo).apply {
            parentFile.mkdirs()
            writeText(
                """
                class MainApplication {
                    fun onCreate() {
                        SalesforceHybridSDKManager.initHybrid(applicationContext)
                        startApp()
                    }
                }
                """.trimIndent()
            )
        }

        applyHybridRemoteDPoPWorkaround(appSource, appInfo)

        assertEquals(
            listOf(
                "SalesforceHybridSDKManager.initHybrid(applicationContext)",
                "SalesforceHybridSDKManager.getInstance().useDPoP = false",
                "startApp()",
            ),
            sourceFile.readLines().map(String::trim).filter { it.isNotEmpty() }.slice(2..4),
        )
    }

    @Test
    fun `inserts iOS DPoP override immediately after hybrid SDK initialization`() {
        val appSource = AppSource.ByType(OS.IOS, AppType.HYBRID_REMOTE)
        val appInfo = hybridAppInfo(appSource)
        val sourceFile = iosSourceFile(appInfo).apply {
            parentFile.mkdirs()
            writeText(
                """
                extension AppDelegate {
                    func application() {
                        SalesforceHybridSDKManager.initializeSDK()
                        startApp()
                    }
                }
                """.trimIndent()
            )
        }

        applyHybridRemoteDPoPWorkaround(appSource, appInfo)

        assertEquals(
            listOf(
                "SalesforceHybridSDKManager.initializeSDK()",
                "SalesforceManager.shared.usesDPoP = false",
                "startApp()",
            ),
            sourceFile.readLines().map(String::trim).filter { it.isNotEmpty() }.slice(2..4),
        )
    }

    @Test
    fun `does not modify apps other than exact hybrid remote by type`() {
        val sources = listOf(
            AppSource.ByType(OS.ANDROID, AppType.HYBRID_LOCAL),
            AppSource.ByType(OS.ANDROID, AppType.NATIVE_KOTLIN),
            AppSource.ByTemplate(OS.ANDROID, "HybridRemoteTemplate"),
        )

        sources.forEach { appSource ->
            val appInfo = hybridAppInfo(appSource)
            applyHybridRemoteDPoPWorkaround(appSource, appInfo)
        }
    }

    @Test
    fun `applies workaround only to SDK versions that support DPoP`() {
        assertEquals(false, supportsHybridRemoteDPoPWorkaround("v13.2.0"))
        assertEquals(false, supportsHybridRemoteDPoPWorkaround("release-13.2"))
        assertEquals(false, supportsHybridRemoteDPoPWorkaround("13_rc0_fixes"))
        assertEquals(true, supportsHybridRemoteDPoPWorkaround("v14.0.0"))
        assertEquals(true, supportsHybridRemoteDPoPWorkaround("release-14.0"))
        assertEquals(true, supportsHybridRemoteDPoPWorkaround("dev"))
        assertEquals(true, supportsHybridRemoteDPoPWorkaround("master"))
        assertEquals(false, supportsHybridRemoteDPoPWorkaround("feature-branch"))
        assertEquals(true, supportsHybridRemoteDPoPWorkaround(null))
    }

    @Test
    fun `is idempotent when one override is already inserted`() {
        val appSource = AppSource.ByType(OS.ANDROID, AppType.HYBRID_REMOTE)
        val appInfo = hybridAppInfo(appSource)
        val sourceFile = androidSourceFile(appInfo).apply {
            parentFile.mkdirs()
            writeText(
                """
                SalesforceHybridSDKManager.initHybrid(applicationContext)
                SalesforceHybridSDKManager.getInstance().useDPoP = false
                """.trimIndent()
            )
        }
        val originalSource = sourceFile.readText()

        applyHybridRemoteDPoPWorkaround(appSource, appInfo)

        assertEquals(originalSource, sourceFile.readText())
    }

    @Test
    fun `fails when override exists outside the expected location`() {
        val error = assertFailsWith<IllegalStateException> {
            transformSourceAfterAnchor(
                """
                SalesforceHybridSDKManager.getInstance().useDPoP = false
                SalesforceHybridSDKManager.initHybrid(applicationContext)
                """.trimIndent(),
                "SalesforceHybridSDKManager.initHybrid(applicationContext)",
                "SalesforceHybridSDKManager.getInstance().useDPoP = false",
                "MainApplication.kt",
            )
        }

        assertTrue(error.message.orEmpty().contains("outside the expected location"))
    }

    @Test
    fun `fails with actionable error when anchor is missing`() {
        val appSource = AppSource.ByType(OS.IOS, AppType.HYBRID_REMOTE)
        val appInfo = hybridAppInfo(appSource)
        iosSourceFile(appInfo).apply {
            parentFile.mkdirs()
            writeText("extension AppDelegate {}")
        }

        val error = assertFailsWith<IllegalStateException> {
            applyHybridRemoteDPoPWorkaround(appSource, appInfo)
        }

        assertTrue(error.message.orEmpty().contains("expected exactly one"))
        assertTrue(error.message.orEmpty().contains("SalesforceHybridSDKManager.initializeSDK()"))
    }

    @Test
    fun `fails with actionable error when anchor occurs multiple times`() {
        val appSource = AppSource.ByType(OS.ANDROID, AppType.HYBRID_REMOTE)
        val appInfo = hybridAppInfo(appSource)
        androidSourceFile(appInfo).apply {
            parentFile.mkdirs()
            writeText(
                """
                SalesforceHybridSDKManager.initHybrid(applicationContext)
                SalesforceHybridSDKManager.initHybrid(applicationContext)
                """.trimIndent()
            )
        }

        val error = assertFailsWith<IllegalStateException> {
            applyHybridRemoteDPoPWorkaround(appSource, appInfo)
        }

        assertTrue(error.message.orEmpty().contains("found 2"))
        assertTrue(error.message.orEmpty().contains("SalesforceHybridSDKManager.initHybrid(applicationContext)"))
    }

    private fun hybridAppInfo(appSource: AppSource): AppInfo = AppInfo(
        os = appSource.os,
        appName = appSource.appName,
        appPath = tempDir.resolve(appSource.appName).toString(),
        packageName = "com.salesforce.${appSource.appName}",
        isHybrid = true,
    )

    private fun androidSourceFile(appInfo: AppInfo) = File(
        appInfo.androidRoot,
        "app/src/main/java/${appInfo.packageName.replace('.', '/')}/MainApplication.kt",
    )

    private fun iosSourceFile(appInfo: AppInfo) =
        File(appInfo.iosRoot, "App/Plugins/com.salesforce/AppDelegate.swift")
}
