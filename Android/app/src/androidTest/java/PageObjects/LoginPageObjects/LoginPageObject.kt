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
import android.util.Log
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import pageobjects.BasePageObject

const val USERNAME_RESOURCE_ID = "username"
const val PASSWORD_RESOURCE_ID = "password"
const val LOGIN_RESOURCE_ID = "Login"

/**
 * Created by bpage on 2/21/18.
 */
class LoginPageObject : BasePageObject() {

    fun setUsername(name: String) {
        val usernameField = getObject(
            resourceId = USERNAME_RESOURCE_ID,
            backup = UiSelector().className(editTextClass).index(0),
        )

        Log.i("uia", "Waiting for username filed to be present.")
        assert(usernameField.waitForExists(timeout * 10))
        usernameField.setText(name)
    }

    fun setPassword(password: String) {
        Log.i("uia", "Waiting for password filed to be present.")
        val passwordField = getObject(
            resourceId = PASSWORD_RESOURCE_ID,
            backup = UiSelector().className(editTextClass).index(2),
        )

        assert(passwordField.waitForExists(timeout * 5))
        passwordField.setText(password)
    }

    fun tapLogin() {
        Thread.sleep(timeout / 2)
        val loginButton = getObject(
            resourceId = LOGIN_RESOURCE_ID,
            backup = UiSelector().textMatches("Log In"),
        )

        assert(loginButton.waitForExists(timeout * 5))
        loginButton.click()
    }


    // TODO: Remove this when the Android 15 resource-id issue is resolved.
    private fun getObject(resourceId: String, backup: UiSelector): UiObject {
        return device.findObject(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                UiSelector().resourceId(resourceId)
            } else {
                backup
            }
        )
    }
}