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

import android.util.Log
import androidx.test.uiautomator.UiSelector
import pageobjects.BasePageObject

const val USERNAME_RESOURCE_ID = "username"
const val PASSWORD_RESOURCE_ID = "password"
const val LOGIN_RESOURCE_ID = "Login"

/**
 * Chrome Custom Tab close button. Under forced advanced authentication the login form is rendered
 * in a Chrome Custom Tab, so its presence is the marker every tab-facing helper keys off.
 */
const val CHROME_CLOSE_BUTTON_ID = "com.android.chrome:id/close_button"

/**
 * Short timeout for checking optional local Chrome UI elements that either appear immediately or
 * not at all (e.g. first-run dialogs). These are not dependent on server-side rendering.
 */
private const val QUICK_CHECK_TIMEOUT: Long = 500

/**
 * Ceiling for clearing Chrome's First Run Experience, which on a cold profile is a slow multi-page
 * sequence. Longer than [timeout]; only fully spent when no tab ever appears.
 */
private const val FRE_DISMISS_TIMEOUT: Long = 30_000

/**
 * Created by bpage on 2/21/18.
 *
 * Drives the Salesforce login form. Under forced advanced authentication the form is rendered in a
 * Chrome Custom Tab, which runs in a separate process (com.android.chrome) that Espresso cannot
 * reach; UiAutomator cross-process lookups are used instead. Fields are matched by their web
 * resource id first, then fall back to the generic EditText/Button classes when Chrome does not
 * surface the ids (QUERY_ALL_PACKAGES in the androidTest manifest grants the cross-process access).
 */
class LoginPageObject : BasePageObject() {

    fun setUsername(name: String) {
        skipGoogleSignIn()
        Log.i("uia", "Waiting for username field to be present.")
        var usernameField = device.findObject(UiSelector().resourceId(USERNAME_RESOURCE_ID))
        if (!usernameField.waitForExists(timeout * 5)) {
            usernameField = device.findObject(
                UiSelector().className(editTextClass).instance(0)
            )
            if (!usernameField.waitForExists(timeout * 5)) {
                throw AssertionError("Username field not found.")
            }
        }
        usernameField.click()
        usernameField.setText(name)
    }

    fun setPassword(password: String) {
        skipGoogleSignIn()
        Log.i("uia", "Waiting for password field to be present.")
        var passwordField = device.findObject(UiSelector().resourceId(PASSWORD_RESOURCE_ID))
        if (!passwordField.waitForExists(timeout * 5)) {
            passwordField = device.findObject(
                UiSelector().className(editTextClass).instance(0)
            )
            if (!passwordField.waitForExists(timeout * 5)) {
                throw AssertionError("Password field not found.")
            }
        }
        passwordField.click()
        passwordField.setText(password)
    }

    fun tapLogin() {
        var loginButton = device.findObject(UiSelector().resourceId(LOGIN_RESOURCE_ID))
        if (!loginButton.waitForExists(timeout)) {
            loginButton = device.findObject(
                UiSelector().className("android.widget.Button").textContains("Log In")
            )
            if (!loginButton.waitForExists(timeout)) {
                throw AssertionError("Log In button not found.")
            }
        }
        loginButton.click()
    }

    /**
     * Clears Chrome's First Run Experience so the Custom Tab can render. On a cold Chrome the tab's
     * first launch is covered by a multi-page FRE that renders slowly, hiding the tab contents every
     * tab-facing helper keys off. Loops until the tab is in front, dismissing whichever FRE control
     * shows each pass. Idempotent: a warm Chrome already showing the tab returns at once.
     */
    fun skipGoogleSignIn() {
        val deadline = System.currentTimeMillis() + FRE_DISMISS_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            if (isCustomTabDisplayed()) {
                return
            }
            if (!dismissOneFreControl()) {
                // Nothing to dismiss yet; the next FRE page may still be rendering, so re-check.
                device.waitForIdle(QUICK_CHECK_TIMEOUT)
            }
        }
    }

    /**
     * Dismisses a single FRE control if one is on screen, returning true if it clicked something.
     * Decline buttons are tried before accept buttons so the flow advances without a Google sign-in;
     * each is matched by resource id first, then by its en-locale label.
     */
    private fun dismissOneFreControl(): Boolean {
        val dismissByIdOrText = listOf(
            "com.android.chrome:id/signin_fre_dismiss_button",
            "com.android.chrome:id/negative_button",
        )
        for (resourceId in dismissByIdOrText) {
            val button = device.findObject(UiSelector().resourceId(resourceId))
            if (button.waitForExists(QUICK_CHECK_TIMEOUT)) {
                button.click()
                return true
            }
        }
        for (label in listOf("Use without an account", "No thanks", "No Thanks")) {
            val button = device.findObject(UiSelector().textContains(label))
            if (button.exists()) {
                button.click()
                return true
            }
        }

        // The initial UMA/ToS page has no decline option; accepting it is required to advance.
        val acceptByIdOrText = listOf(
            "com.android.chrome:id/terms_accept",
            "com.android.chrome:id/positive_button",
        )
        for (resourceId in acceptByIdOrText) {
            val button = device.findObject(UiSelector().resourceId(resourceId))
            if (button.waitForExists(QUICK_CHECK_TIMEOUT)) {
                button.click()
                return true
            }
        }
        for (label in listOf("Accept & continue", "Got it")) {
            val button = device.findObject(UiSelector().textContains(label))
            if (button.exists()) {
                button.click()
                return true
            }
        }

        return false
    }

    /**
     * True when a Chrome Custom Tab is currently in front, detected via its close button.
     * Used to confirm the advanced-auth login form is being driven in the tab rather than in-app.
     */
    fun isCustomTabDisplayed(): Boolean {
        val closeButton = device.findObject(
            UiSelector().resourceId(CHROME_CLOSE_BUTTON_ID)
        )
        return closeButton.waitForExists(QUICK_CHECK_TIMEOUT)
    }
}
