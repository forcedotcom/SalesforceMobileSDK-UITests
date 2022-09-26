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

/**
 * Created by bpage on 2/23/18.
 */

class AuthorizationPageObject : BasePageObject() {

    fun tapAllow() {
        val chromePageObject = ChromePageObject()
        val advAuth = chromePageObject.isAdvAuth()
        if (advAuth) {
            chromePageObject.dismissSavePasswordDialog()
        }

        val allowButton = device.findObject(UiSelector().resourceId("oaapprove"))
        Log.i("uia", "Waiting for allow button to be present.")
        assert(allowButton.waitForExists(timeout * 5))
        allowButton.click()
        Thread.sleep(timeout)

        /*
         *  TODO: This is an issue we have no control over, remove this if fixed by Google.
         *  If chrome doesn't redirect to the app by itself, close it by tapping back.
         */
        if (advAuth and allowButton.waitForExists(timeout)) {
            Log.i("uia", "Chrome Custom Tab did not close properly, manually tapping back.")
            chromePageObject.tapCloseButton()
        }
    }
}