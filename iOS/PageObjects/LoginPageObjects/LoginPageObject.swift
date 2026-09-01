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
//
//  LoginPageObject.swift
//  MobileSDKUITest
//
//  Created by Brandon Page on 2/21/18.
//

import Foundation
import XCTest

class LoginPageObject {
    let app:XCUIApplication
    let timeout:double_t = 10
    private let expectAdvancedAuthentication: Bool

    init(testApp: XCUIApplication, expectAdvancedAuthentication: Bool = true) {
        app = testApp
        self.expectAdvancedAuthentication = expectAdvancedAuthentication
    }

    /// The web content that hosts the login form. Pre-14.0 upgrade tests use the app's top-level
    /// WebView. Under forced advanced auth the form is served in an external browser
    /// (ASWebAuthenticationSession). Some app types (notably hybrid) trigger authentication more
    /// than once on cold launch, which stacks multiple browser sheets — each an identical
    /// `webViews.webViews.webViews` subtree with its own username/password fields. Drive the
    /// frontmost (last-presented, top-of-stack) browser and scope every field/button query to it.
    private var loginWebView: XCUIElement {
        // LEGACY UPGRADE AUTOMATION (SDK 12.x AND 13.x): v12.2.0 and v13.2.1 use their
        // top-level WebView. Remove this branch after both 12.x and 13.x coverage are retired.
        if !expectAdvancedAuthentication {
            return app.webViews.firstMatch
        }
        let webViews = app.webViews.webViews.webViews
        let count = webViews.count
        return count > 1 ? webViews.element(boundBy: count - 1) : webViews.firstMatch
    }

    func setUsername(name: String) -> Void {
        // Wait for the configured login surface to finish rendering before touching fields.
        waitForLoginFormReady()
        hideKeyboard()
        let nameField = loginWebView.textFields.firstMatch
        _ = nameField.waitForExistence(timeout: timeout * 12)
        // The browser pre-populates the username from the OAuth login_hint. A pre-filled web field
        // does not reliably attach the software keyboard on tap, so typing into it fails with
        // "Neither element nor any descendant has keyboard focus". When the field already holds a
        // value, trust the prefill and skip typing — the caller will advance with the "Log In"
        // button. Only type when the field is genuinely empty.
        let existing = nameField.value as? String ?? ""
        if !existing.isEmpty && existing != "Username" {
            return
        }
        setFieldValue(nameField, value: name)
    }

    func setPassword(password: String) -> Void {
        hideKeyboard()
        // Tapping "Log In" on the username step navigates the browser to the password page; allow the
        // same generous browser-page-load window used for the initial form before touching the field.
        let passwordField = loginWebView.secureTextFields.firstMatch
        _ = passwordField.waitForExistence(timeout: timeout * 12)
        setFieldValue(passwordField, value: password)
    }

    /// Enters `value` into a browser login-form field. The external browser may pre-populate a
    /// field (e.g. a remembered username), and tapping an already-filled web field does not always
    /// attach the software keyboard — typing then fails with "no keyboard focus". So tap to focus,
    /// skip typing when the field already holds the desired value, and only type otherwise. Mirrors
    /// the SDK AuthFlowTester UITests' setTextField for the forced-advanced-auth browser flow.
    private func setFieldValue(_ field: XCUIElement, value: String) {
        field.tap()
        sleep(1)
        if (field.value as? String) == value {
            return
        }
        field.typeText(value)
    }

    /// Waits for the configured login form to be fully loaded and interactive. Browser-based
    /// advanced authentication and the legacy in-app WebView both render the fields asynchronously.
    func waitForLoginFormReady() -> Void {
        let webViewTextField = loginWebView.textFields.firstMatch
        _ = webViewTextField.waitForExistence(timeout: timeout * 12)
    }

    /// Submits the current login step. Under forced advanced authentication the login form is served
    /// in the external browser (ASWebAuthenticationSession), which renders an on-page "Log In" button.
    /// Tapping that button is how a user advances, and it does not require keyboard focus — synthesizing
    /// a Return keypress into a web field fails ("Neither element nor any descendant has keyboard
    /// focus") whenever the field was pre-filled and never attached the software keyboard. Falls back
    /// to a Return keypress only if no button is present. Callers drive the form the same way a user
    /// would: (accept prefilled or type) username, submit; type password, submit.
    func tapLogin() -> Void {
        hideKeyboard()
        let webView = loginWebView
        // Salesforce's identity-first browser form labels the button "Log In"; match loosely to also
        // catch "Log In to Sandbox" and locale/label variants.
        let loginButton = webView.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Log In'")).firstMatch
        if loginButton.waitForExistence(timeout: timeout) && loginButton.isHittable {
            loginButton.tap()
            return
        }
        // Fallback: submit the on-screen field with Return (only works if the keyboard is attached).
        let secureField = webView.secureTextFields.firstMatch
        let field = secureField.exists ? secureField : webView.textFields.firstMatch
        field.typeText(XCUIKeyboardKey.return.rawValue)
    }

    /// Dismisses the external browser (ASWebAuthenticationSession) without completing login. The
    /// browser chrome exposes a "Close" button on current iOS versions and "Cancel" on earlier ones.
    func tapBack() -> Void {
        let topBar = app.otherElements["TopBrowserBar"]
        var closeButton = topBar.buttons["Close"]
        if !closeButton.waitForExistence(timeout: timeout) {
            // Earlier iOS versions use "Cancel"; only that fallback needs its own wait.
            closeButton = topBar.buttons["Cancel"]
            _ = closeButton.waitForExistence(timeout: timeout)
        }
        closeButton.tap()
    }
    
    func hideKeyboard() -> Void {
        let continueButton = app.otherElements["UIContinuousPathIntroductionView"]
        if continueButton.exists {
            continueButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        
        let doneButton = app.toolbars.matching(identifier: "Toolbar").buttons["Done"]
        if doneButton.exists {
            doneButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }
}
