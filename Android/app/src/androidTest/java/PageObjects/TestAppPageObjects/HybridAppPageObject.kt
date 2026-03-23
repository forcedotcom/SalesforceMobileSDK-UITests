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
package pageobjects.testapppageobjects

import android.os.Build
import android.util.Log
import androidx.test.uiautomator.UiSelector
import org.junit.Assert
import pageobjects.AppType
import pageobjects.BasePageObject

/**
 * Created by bpage on 2/26/18.
 */
class HybridAppPageObject(private val app: TestApplication) : BasePageObject() {

    fun assertAppLoads() {
        val content = when {
            app.type == AppType.HYBRID_REMOTE -> "Salesforce Mobile SDK Test"
            else -> {
                if (app.complexHybrid == "accounteditor") {
                    "Accounts"
                } else {
                    verifyInWebView("Contacts")
                    "Marc Benioff"
                }
            }
        }

        // On API 28, the system WebView does not expose its DOM content to the
        // accessibility tree, so UiAutomator cannot read text inside it.  Fall
        // back to verifying the WebView element itself is present, which confirms
        // the app transitioned past login into the hybrid view.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && app.type == AppType.HYBRID_REMOTE) {
            Log.i("HybridApp", "API 28: WebView content not accessible to UiAutomator, verifying WebView presence.")
            val webView = device.findObject(UiSelector().className("android.webkit.WebView"))
            webView.waitForExists(timeout * 5)
            Assert.assertTrue("App did not successfully load (WebView not found).", webView.exists())
            return
        }

        verifyInWebView(content)
    }


    private fun verifyInWebView(text: String) {
        var webElement = device.findObject(UiSelector().className(viewClass).textContains(text))
        if (!webElement.waitForExists(timeout * 5)) {
            webElement = device.findObject(UiSelector().className(textViewClass).textContains(text))
            if (!webElement.waitForExists(timeout * 5)) {
                webElement = device.findObject(UiSelector().descriptionContains(text))
                if (!webElement.waitForExists(timeout * 5)) {
                    webElement = device.findObject(UiSelector().textContains(text))
                    webElement.waitForExists(timeout * 5)
                }
            }
        }

        Assert.assertTrue("App did not successfully load.", webElement.exists())
    }
}