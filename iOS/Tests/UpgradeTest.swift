/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
//
//  UpgradeTest.swift
//  SalesforceMobileSDK-UITest
//
//  Validates that a user remains logged in after an app upgrade.
//

import XCTest

class UpgradeTest: BaseSDKTest {

    /// Phase 1 of upgrade testing: logs into the old app version and
    /// asserts the login screen is no longer visible.  Intentionally
    /// skips assertAppLoads so we don't need to maintain assertions
    /// for older template UIs.
    func testInitialLogin() {
        let app = TestApplication()
        let loginPage = LoginPageObject(testApp: app)
        let authPage = AuthorizationPageObject(testApp: app)
        app.launch()

        loginPage.setUsername(name: username)
        loginPage.tapLogin()
        loginPage.setPassword(password: password)
        loginPage.tapLogin()
        authPage.tapAllowIfPresent()

        // Assert login screen is no longer showing
        let loginField = app.webViews.textFields["Username"]
        XCTAssertFalse(loginField.waitForExistence(timeout: 5), "Login screen is still showing after login.")
    }

    /// Launches the upgraded app and asserts that the main content loads
    /// without requiring login.  The orchestrator is responsible for
    /// installing the old version, logging in, and then installing the
    /// new version before this test runs.
    func testUpgradePreservesLogin() {
        let app = TestApplication()
        app.launch()

        // After upgrade the app should load directly — no login required.
        assertAppLoads(app: app)
    }
}
