//
//  AuthorizationPageObject.swift
//  MobileSDKUITest
//
//  Created by Brandon Page on 2/21/18.
//

import Foundation
import XCTest

class AuthorizationPageObject: XCUIScreen {
    let app:XCUIApplication
    let timeout:double_t = 5
    
    init(testApp: XCUIApplication) {
        app = testApp
    }
    
    func tapAllow() {
        pressButton(lable: "Allow")
    }
    
    func tapDeny() {
        pressButton(lable: "Deny")
    }
    
    private func pressButton(lable: String) {
        let button = app.buttons.element(matching: NSPredicate(format: "label CONTAINS '" + lable + "'"))
        _ = assert(button.waitForExistence(timeout: timeout * 5))
        sleep(2)
        button.tap()
    }
}
