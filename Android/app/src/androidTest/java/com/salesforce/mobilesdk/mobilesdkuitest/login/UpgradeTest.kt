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
package com.salesforce.mobilesdk.mobilesdkuitest.login

import pageobjects.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.Test
import pageobjects.testapppageobjects.*

/**
 * Validates that a user remains logged in after an app upgrade.
 * The app should have been installed with an older SDK version and logged in
 * before being upgraded to the current SDK version.  This test launches the
 * upgraded app and asserts that the main content loads without requiring login.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeTest {
    val app = TestApplication()

    @Before
    fun setupTestApp() {
        app.launch()
    }

    @Test
    fun testUpgradePreservesLogin() {
        // After upgrade the app should load directly without showing a login screen.
        when (app.type) {
            AppType.NATIVE, AppType.NATIVE_KOTLIN ->
                NativeAppPageObject(app).assertAppLoads()
            AppType.HYBRID_LOCAL, AppType.HYBRID_REMOTE ->
                HybridAppPageObject(app).assertAppLoads()
            AppType.REACT_NATIVE, AppType.MOBILE_SYNC_EXPLORER_REACT_NATIVE ->
                ReactNativeAppPageObject(app).assertAppLoads()
            AppType.MOBILE_SYNC_EXPLORER_KOTLIN ->
                MobileSyncKotlinAppPageObject(app).assertAppLoads()
            else -> Assert.fail("Unknown App Type")
        }
    }
}
