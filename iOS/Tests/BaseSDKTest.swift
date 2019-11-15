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
//
//  BaseSDKTest.swift
//  SalesforceMobileSDK-UITest
//
//  Created by Brandon Page on 11/14/19.
//


import XCTest

class BaseSDKTest: XCTestCase {
    var username = UserUtility().username
    var password = UserUtility().password
    var timeout:double_t = 60
    private var appLoadError = "App did not load."
    private var mobileSyncError = "MobileSync did not pull data."
    private let reactNativeUsers = "Automated Process Brandon Page circleci Integration User Security User Chatter Expert Mobile SDK Sample App"
    private let sampleAppTitle = "Mobile SDK Sample App"
    
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }
    
    override func tearDown() {
        super.tearDown()
    }
    
    func assertAppLoads(app: TestApplication) {
        switch app.type {
        case .nativeObjC, .nativeSwift:
            XCTAssert(app.navigationBars[sampleAppTitle].waitForExistence(timeout: timeout), appLoadError)
        case .hybridLocal, .hyrbidRemote:
            sleep(10)
            let titleText = (app.type == .hybridLocal) ? "Contacts" : "Salesforce Mobile SDK Test"
            let title = app.staticTexts[titleText]
            let exists = NSPredicate(format: "exists == 1")
            
            expectation(for: exists, evaluatedWith: title, handler: nil)
            waitForExpectations(timeout: timeout, handler: nil)
            XCTAssert(title.exists, appLoadError)
        case .reactNative:
            sleep(30)
            let titleElement = app.otherElements.matching(identifier: sampleAppTitle).staticTexts[sampleAppTitle]
            XCTAssert(titleElement.waitForExistence(timeout: timeout), appLoadError)
        case .mobileSyncSwift:
            // TODO: Remove this when min iOS version is 13
            let title = ((UIDevice.current.systemVersion as NSString).floatValue >= 13.0) ?
                app.navigationBars["MobileSync Explorer"].staticTexts["MobileSync Explorer"] :
                app.navigationBars["MobileSync Explorer"].otherElements["MobileSync Explorer"]
            XCTAssert(title.waitForExistence(timeout: timeout), appLoadError)
            
            // Check MobileSync Works
            _ = app.tables.cells.firstMatch.waitForExistence(timeout: timeout)
            XCTAssertGreaterThan(app.tables.cells.count, 0, mobileSyncError)
        case .mobileSyncReact:
            sleep(30)
            let title = app.otherElements.matching(identifier: "Contacts").staticTexts["Contacts"]
            XCTAssert(title.waitForExistence(timeout: timeout), appLoadError)
        case .iOS13Swift:
            let title = app.navigationBars["Accounts"].staticTexts["Accounts"]
            XCTAssert(title.waitForExistence(timeout: timeout), appLoadError)
            
            // Accounts List
             _ = app.tables.cells.firstMatch.waitForExistence(timeout: timeout)
             XCTAssertGreaterThan(app.tables.cells.count, 0, "Swift UI did not load Account List.")
            
            // Contacts List
            app.tables.cells.element(boundBy: 0).tap()
            XCTAssertGreaterThan(app.tables.cells.count, 0, "RestClient did not retrieve Contacts for Account.")
            
            // Contact Details
            app.tables.cells.element(boundBy: 0).tap()
            XCTAssertGreaterThan(app.tables.cells.count, 0, mobileSyncError)
        }
    }
}
