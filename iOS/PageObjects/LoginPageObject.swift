//
//  LoginPageObject.swift
//  MobileSDKUITest
//
//  Created by Brandon Page on 2/21/18.
//

import Foundation
import XCTest

class LoginPageObject: XCUIScreen {
    let app:XCUIApplication
    let timeout:double_t = 5
    
    init(testApp: XCUIApplication) {
        app = testApp
    }
    
    func setUsername(name: String) -> Void {
        let nameField = app.descendants(matching: .textField).element
        _ = nameField.waitForExistence(timeout: timeout)
        nameField.tap()
        sleep(1)
        nameField.typeText(name)
    }
    
    func setPassword(password: String) -> Void {
        let passwordField = app.descendants(matching: .secureTextField).element
        _ = passwordField.waitForExistence(timeout: timeout)
        passwordField.tap()
        sleep(1)
        passwordField.typeText(password)
    }
    
    func tapLogin() -> Void {
        let webViewPredicate = NSPredicate(format: "label BEGINSWITH[cd] 'Login'")
        let webElements = app.otherElements.webViews.otherElements.matching(webViewPredicate).element
        webElements.otherElements.children(matching: .button).element(boundBy: 0).tap()
    }
    
    func tapBack() -> Void {
        let backButton = app.navigationBars["Log In"].children(matching: .button).element(boundBy: 0)
        _ = backButton.waitForExistence(timeout: timeout)
        backButton.tap()
    }
}
