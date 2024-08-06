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

import androidx.test.uiautomator.UiSelector
import org.junit.Assert
import pageobjects.AppType
import pageobjects.BasePageObject

/**
 * Created by bpage on 2/26/18.
 */
class HybridAppPageObject(private val app: TestApplication) : BasePageObject() {

    fun assertAppLoads() {
        val content: String
        if (app.type == AppType.HYBRID_REMOTE) {
            content = "Salesforce Mobile SDK Test"
        } else {
            val titleString = if (app.complexHybrid == "accounteditor") "Accounts" else "Contacts"
            verifyInWebView(titleString)

            // Search for account to assert it shows in list.
            if (app.complexHybrid == "accounteditor") {
                Thread.sleep(timeout)
                val search = device.findObject(UiSelector().className(editTextClass))
                search.setText("New")
            }

            content = when (app.complexHybrid) {
                "accounteditor" -> {
                    "Accounts"
                    // TODO: Uncomment and use the below account name when the test app is made more consistent.
                    // "New 0013u000017W4aIAAS Cached"
                }
                "mobilesyncexplorer" -> {
                    "JB John Bond VP, Facilities Facilities"
                }
                else -> {
                    "Sean Forbes"
                }
            }
        }

        verifyInWebView(content)
    }


    private fun verifyInWebView(text: String) {
        var webElement = device.findObject(UiSelector().className(viewClass).text(text))
        if (!webElement.waitForExists(timeout * 5)) {
            webElement = device.findObject(UiSelector().className(textViewClass).text(text))
            if (!webElement.waitForExists(timeout * 5)) {
                webElement = device.findObject(UiSelector().descriptionContains(text))
                webElement.waitForExists(timeout * 5)
            }
        }

        Assert.assertTrue("App did not successfully load.", webElement.exists())
    }
}