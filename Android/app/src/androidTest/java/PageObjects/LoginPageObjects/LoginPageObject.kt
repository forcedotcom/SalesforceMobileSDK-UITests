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
package pageobjects.loginpageobjects

import android.os.Build
import androidx.test.uiautomator.UiSelector
import android.util.Log
import pageobjects.BasePageObject

/**
 * Created by bpage on 2/21/18.
 */
class LoginPageObject : BasePageObject() {

    init {
        if (hasOldWebview) {
            device.findObject(UiSelector().className("android.widget.EditText").index(2)).waitForExists(120000)
        }
    }

    fun setUsername(name: String) {
        val usernameField = if (hasOldWebview) {
            // TODO: Update when min version increases to API 23
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
                device.findObject(UiSelector().className("android.widget.EditText").index(2))
            } else {
                device.findObject(UiSelector().className("android.widget.EditText").descriptionContains("Username"))
            }
        }
        else {
            device.findObject(UiSelector().resourceId("username"))
        }

        Log.i("uia", "Waiting for username filed to be present.")
        if (hasOldWebview) {
            assert(usernameField.waitForExists(timeout * 12))
            usernameField.legacySetText(name)
            Thread.sleep(timeout)
        }
        else {
            assert(usernameField.waitForExists(timeout * 3))
            usernameField.text = name
        }
    }

    fun setPassword(password: String) {
        // TODO: Update when min version increases to API 23
        val index = if (hasOldWebview or (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1)) 4 else 3
        val passwordField = if (hasOldWebview) {
            device.findObject(UiSelector().className("android.widget.EditText").index(index))
        }
        else {
            device.findObject(UiSelector().resourceId("password"))
        }

        Log.i("uia", "Waiting for password filed to be present.")
        assert(passwordField.waitForExists(timeout))
        passwordField.text = password
    }

    fun tapLogin() {
        val loginButton = if (hasOldWebview) {
            device.findObject(UiSelector().className("android.widget.Button").index(0))
        }
        else {
            device.findObject(UiSelector().resourceId("Login"))
        }

        assert(loginButton.waitForExists(timeout * 2))
        loginButton.click()
    }
}