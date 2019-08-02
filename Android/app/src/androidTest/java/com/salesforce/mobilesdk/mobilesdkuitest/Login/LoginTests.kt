/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
import testutility.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.Test
import pageobjects.loginpageobjects.LoginPageObject
import pageobjects.loginpageobjects.AuthorizationPageObject
import pageobjects.testapppageobjects.*

/**
 * Created by bpage on 2/2/18.
 */
@RunWith(AndroidJUnit4::class)
class LoginTests {
    private var app = TestApplication()
    private var userUtil = UserUtility()
    private var username = userUtil.username
    private var password = userUtil.password

    @Before
    fun setupTestApp() {
        app.launch()
    }

    @Test
    fun testLogin() {
        val loginPage = LoginPageObject()
        loginPage.setUsername(username)
        loginPage.setPassword(password)
        loginPage.tapLogin()
        AuthorizationPageObject().tapAllow()

        when (app.type) {
            AppType.NATIVE_JAVA, AppType.NATIVE_KOTLIN ->
                NativeAppPageObject(app).assertAppLoads()
            AppType.HYBRID_LOCAL ->
                HybridLocalAppPageObject(app).assertAppLoads()
            AppType.HYBRID_REMOTE ->
                HybridRemoteAppPageObject(app).assertAppLoads()
            AppType.REACT_NATIVE ->
                ReactNativeAppPageObject().assertAppLoads()
        }
    }
}