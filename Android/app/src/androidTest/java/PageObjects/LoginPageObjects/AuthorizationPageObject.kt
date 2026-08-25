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

import androidx.test.uiautomator.*
import android.util.Log
import pageobjects.BasePageObject

private const val ALLOW_RESOURCE_ID = "oaapprove"
private const val AUTHORIZATION_HEADER_RESOURCE_ID = "header"
private const val AUTHORIZATION_HEADER_TEXT = "Allow Access"
private const val WEB_VIEW_CLASS = "android.webkit.WebView"
private const val SCROLL_PERCENT = 0.9f

/**
 * Created by bpage on 2/23/18.
 */

class AuthorizationPageObject : BasePageObject() {

    fun tapAllowIfPresent() {
        var allowButton = device.findObject(UiSelector().resourceId(ALLOW_RESOURCE_ID))
        Log.i("uia", "Waiting for allow button to be present.")
        if (!allowButton.waitForExists(timeout * 2)) {
            val authorizationHeader = device.findObject(
                UiSelector()
                    .resourceId(AUTHORIZATION_HEADER_RESOURCE_ID)
                    .textContains(AUTHORIZATION_HEADER_TEXT)
            )
            if (!authorizationHeader.exists()) {
                return
            }
            allowButton = device.findObject(
                UiSelector()
                    .className("android.widget.Button")
                    .textMatches("\\s*Allow\\s*")
            )
            if (!allowButton.waitForExists(timeout)) {
                return
            }
        }

        val webView = device.wait(
            Until.findObject(By.pkg(allowButton.packageName).clazz(WEB_VIEW_CLASS)),
            timeout
        )
            ?: throw AssertionError("Authorization WebView not found.")
        val buttonBounds = allowButton.visibleBounds
        val webViewBounds = webView.visibleBounds
        val buttonIsFullyVisible = buttonBounds.height() > 0 &&
                buttonBounds.top >= webViewBounds.top &&
                buttonBounds.bottom < webViewBounds.bottom

        if (!buttonIsFullyVisible) {
            Log.i("uia", "Scrolling authorization page to reveal Allow button.")
            webView.scroll(Direction.DOWN, SCROLL_PERCENT)
        }

        if (!allowButton.click()) {
            throw AssertionError("Could not tap the authorization Allow button.")
        }
        if (!allowButton.waitUntilGone(timeout * 2)) {
            throw AssertionError("Authorization page did not close after tapping Allow.")
        }
    }
}
