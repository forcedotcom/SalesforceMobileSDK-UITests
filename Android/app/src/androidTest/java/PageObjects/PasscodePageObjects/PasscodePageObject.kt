/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
package PageObjects.passcodepageobjects

import androidx.test.uiautomator.UiSelector
import pageobjects.BasePageObject
import pageobjects.testapppageobjects.TestApplication

/**
 * Created by bpage on 9/7/19.
 */
class PasscodePageObject(private val app: TestApplication) : BasePageObject() {
    private val packageName = app.packageName
    private val passcodeTitleId = "${packageName}:id/sf__passcode_title"
    private val passcodeInstructionsId = "${packageName}:id/sf__passcode_instructions"
    private val passcodeFieldId ="${packageName}:id/sf__passcode_text"
    private val logoutButtonId = "${packageName}:id/sf__passcode_logout_button"

    fun getTitleText(): String {
        return device.findObject(UiSelector().resourceId(passcodeTitleId)).text
    }

    fun getText(): String {
        return device.findObject(UiSelector().resourceId(passcodeInstructionsId)).text
    }

    fun enterPasscode(passcode: String) {
        val passcodeField = device.findObject(UiSelector().resourceId(passcodeFieldId))
        passcodeField.setText(passcode)
    }

    fun getPasscode(): String {
        return device.findObject(UiSelector().resourceId(passcodeFieldId)).text
    }

    // Due to accessibility passcode will report "Passcode is 5 alphanumeric characters long." instead of 0
    fun getTypedLength(): Int {
        return device.findObject(UiSelector().resourceId(passcodeFieldId)).text.length
    }

    fun isLogutButtonVisible(): Boolean {
        return device.findObject(UiSelector().resourceId(logoutButtonId)).exists()
    }

    fun tapLogoutButton() {
        device.findObject(UiSelector().resourceId(logoutButtonId)).click()
    }
}