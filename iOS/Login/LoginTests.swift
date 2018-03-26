//
//  LoginTest.swift
//  MobileSDKUITest
//
//  Created by Brandon Page on 2/2/18.
//

import XCTest

class LoginTests: XCTestCase {
    private var username = "circleci@mobilesdk.com"
    private var password = "test1234"
    private var appLoadError = "App did not load."
    private var timeout:double_t = 30
    
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }
    
    override func tearDown() {
        super.tearDown()
    }
    
    func testLogin() {
        let app = TestApplication()
        let loginPage = LoginPageObject(testApp: app)
        let authPage = AuthorizationPageObject(testApp: app)
        
        app.launch()
        loginPage.setUsername(name: username)
        loginPage.setPassword(password: password)
        loginPage.tapLogin()
        authPage.tapAllow()
        
        // Assert App loads
        switch app.appType {
        case .nativeObjC, .nativeSwift:
            XCTAssert(app.navigationBars["Mobile SDK Sample App"].waitForExistence(timeout: timeout), appLoadError)
        case .hybridLocal, .hyrbidRemote:
            let titleText = (app.appType == .hybridLocal) ? "Users" : "Salesforce Mobile SDK Test"
            let title = app.staticTexts[titleText]
            let exists = NSPredicate(format: "exists == 1")
            
            expectation(for: exists, evaluatedWith: title, handler: nil)
            waitForExpectations(timeout: timeout, handler: nil)
            XCTAssert(title.exists, appLoadError)
        case .reactNative:
            let titleElement = app.children(matching: .window).element(boundBy: 0).children(matching: .other).element.children(matching: .other)["Automated Process Brandon Page circleci Integration User Security User Chatter Expert Mobile SDK Sample App"].children(matching: .other)["Automated Process Brandon Page circleci Integration User Security User Chatter Expert Mobile SDK Sample App"].children(matching: .other)["Automated Process Brandon Page circleci Integration User Security User Chatter Expert Mobile SDK Sample App"].children(matching: .other)["Automated Process Brandon Page circleci Integration User Security User Chatter Expert Mobile SDK Sample App"].children(matching: .other)["Automated Process Brandon Page circleci Integration User Security User Chatter Expert Mobile SDK Sample App"].children(matching: .other)["Mobile SDK Sample App"].children(matching: .other)["Mobile SDK Sample App"].children(matching: .other)["Mobile SDK Sample App"]
            XCTAssert(titleElement.waitForExistence(timeout: timeout), appLoadError)
        }
    }
}
