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
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import androidx.test.platform.app.InstrumentationRegistry
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
private const val KEYBOARD_DISMISS_TIMEOUT: Long = 2_000

private const val LOCAL_NETWORK_PERMISSION_MESSAGE =
    "wants to access other devices on your local network"
private const val CHROME_PERMISSION_MESSAGE_ID = "com.android.chrome:id/text"
private const val CHROME_NEGATIVE_BUTTON_ID = "com.android.chrome:id/negative_button"

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
 *
 * LEGACY UPGRADE AUTOMATION (SDK 12.x AND 13.x): [expectAdvancedAuthentication] is false while
 * logging into v12.2.0 and v13.2.1 apps, whose login form is still in the in-app WebView. Remove
 * the parameter and conditional branch after both 12.x and 13.x upgrade coverage are retired.
 */
class LoginPageObject(
    private val expectAdvancedAuthentication: Boolean = true,
) : BasePageObject() {

    fun setUsername(name: String) {
        prepareLoginSurface()
        Log.i("uia", "Waiting for username field to be present.")
        val usernameField = waitForLoginControl(
            UiSelector().resourceId(USERNAME_RESOURCE_ID),
            UiSelector().className(editTextClass).instance(0),
            "Username field not found."
        )
        usernameField.click()
        usernameField.setText(name)
    }

    fun setPassword(password: String) {
        prepareLoginSurface()
        Log.i("uia", "Waiting for password field to be present.")
        val passwordField = waitForLoginControl(
            UiSelector().resourceId(PASSWORD_RESOURCE_ID),
            UiSelector().className(editTextClass).instance(0),
            "Password field not found."
        )
        passwordField.click()
        passwordField.setText(password)
    }

    fun tapLogin() {
        dismissKeyboardIfPresent()
        val loginButton = waitForLoginControl(
            UiSelector().resourceId(LOGIN_RESOURCE_ID),
            UiSelector().className("android.widget.Button").textContains("Log In"),
            "Log In button not found.",
            timeout
        )
        loginButton.click()
    }

    private fun dismissKeyboardIfPresent() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        if (uiAutomation.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            return
        }

        device.pressBack()
        val deadline = System.currentTimeMillis() + KEYBOARD_DISMISS_TIMEOUT
        while (System.currentTimeMillis() < deadline &&
            uiAutomation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            device.waitForIdle(QUICK_CHECK_TIMEOUT)
        }
        if (uiAutomation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            throw AssertionError("Keyboard did not close before tapping Log In.")
        }
    }

    /**
     * Advanced authentication opens the login form in Chrome, whose first-run UI may need clearing.
     * LEGACY UPGRADE AUTOMATION (SDK 12.x AND 13.x): v12.2.0 and v13.2.1 keep the login form in the
     * app's WebView, so their initial-login phase skips Chrome preparation. Remove this branch only
     * after both 12.x and 13.x upgrade coverage are retired.
     */
    private fun prepareLoginSurface() {
        if (expectAdvancedAuthentication) {
            skipGoogleSignIn()
        }
    }

    private fun waitForLoginControl(
        primarySelector: UiSelector,
        fallbackSelector: UiSelector,
        errorMessage: String,
        waitTimeout: Long = timeout * 5
    ): UiObject {
        val controls = listOf(primarySelector, fallbackSelector).map(device::findObject)
        val deadline = System.currentTimeMillis() + waitTimeout
        while (System.currentTimeMillis() < deadline) {
            blockLocalNetworkAccessIfPresent()
            for (control in controls) {
                if (control.waitForExists(QUICK_CHECK_TIMEOUT)) {
                    blockLocalNetworkAccessIfPresent()
                    return control
                }
            }
        }
        throw AssertionError(errorMessage)
    }

    /**
     * Blocks Chrome's optional local-network request, which is unrelated to remote OAuth login.
     * The message check prevents Chrome's shared negative-button id from dismissing other dialogs.
     */
    private fun blockLocalNetworkAccessIfPresent() {
        val permissionMessage = device.findObject(
            UiSelector()
                .resourceId(CHROME_PERMISSION_MESSAGE_ID)
                .textContains(LOCAL_NETWORK_PERMISSION_MESSAGE)
        )
        if (!permissionMessage.exists()) {
            return
        }

        Log.i("uia", "Blocking Chrome local-network access prompt.")
        val blockButton = device.findObject(UiSelector().resourceId(CHROME_NEGATIVE_BUTTON_ID))
        if (!blockButton.waitForExists(QUICK_CHECK_TIMEOUT)) {
            throw AssertionError("Chrome local-network Block button not found.")
        }
        blockButton.click()
        if (!permissionMessage.waitUntilGone(timeout)) {
            throw AssertionError("Chrome local-network access prompt did not close.")
        }
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
        throw AssertionError("Chrome Custom Tab never appeared after ${FRE_DISMISS_TIMEOUT}ms.")
    }

    /**
     * Dismisses a single FRE control if one is on screen, returning true if it clicked something.
     * Decline buttons are tried before accept buttons so the flow advances without a Google sign-in;
     * each is matched by resource id first, then by its en-locale label.
     */
    private fun dismissOneFreControl(): Boolean {
        val dismissByIdOrText = listOf(
            "com.android.chrome:id/signin_fre_dismiss_button",
            CHROME_NEGATIVE_BUTTON_ID,
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
