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
    ///
    /// This phase runs against the *pre-upgrade* (pre-14.0) app the orchestrator installs, where
    /// advanced authentication is not forced on, so interactive login still happens in the legacy
    /// in-app WebView with an on-page "Log In" button. `LoginPageObject` has since been re-pointed
    /// at the external browser (ASWebAuthenticationSession) that 14.0 uses by default, so its
    /// `tapLogin()` submits by pressing return rather than tapping the WebView button. To avoid
    /// breaking this phase, the legacy WebView login is driven inline here instead.
    // TODO: Once the pre-upgrade version under test also forces advanced authentication (i.e. the
    // from-version is 14.0+), replace this inline legacy WebView flow with `loginPage.tapLogin()`
    // (keyboard-return submit) to match the other login page objects.
    func testInitialLogin() {
        let app = TestApplication()
        let loginPage = LoginPageObject(testApp: app)
        let authPage = AuthorizationPageObject(testApp: app)
        app.launch()

        loginPage.setUsername(name: username)
        tapLegacyWebViewLoginButton(app: app)
        loginPage.setPassword(password: password)
        tapLegacyWebViewLoginButton(app: app)
        authPage.tapAllowIfPresent()

        // Assert login is complete — the login surface is no longer showing. Match on the login
        // username field regardless of the surface it is hosted in (the legacy in-app WebView on the
        // pre-upgrade app, or the external browser used from 14.0 on), so this marker survives a
        // future move of the from-version onto the browser flow.
        let webViewLoginField = app.webViews.textFields["Username"]
        let browserLoginField = app.webViews.webViews.webViews.textFields.firstMatch
        XCTAssertFalse(webViewLoginField.waitForExistence(timeout: 5), "Login screen is still showing after login.")
        XCTAssertFalse(browserLoginField.exists, "Login screen is still showing after login.")
    }

    /// Taps the on-page "Log In" button of the legacy in-app WebView login form. Used only by the
    /// pre-upgrade (pre-14.0) app in phase 1, which authenticates in the in-app WebView rather than
    /// the external browser. Mirrors the button-tap `LoginPageObject.tapLogin()` performed before it
    /// was re-pointed at the browser's keyboard-return submit.
    private func tapLegacyWebViewLoginButton(app: TestApplication) {
        let loginButton = app.webViews.buttons["Log In"].firstMatch
        _ = loginButton.waitForExistence(timeout: timeout)
        loginButton.tap()
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
