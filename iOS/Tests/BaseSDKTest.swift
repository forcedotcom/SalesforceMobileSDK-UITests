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
    var nativeLoginUsername = UserUtility().nativeLoginUsername
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
        case .nativeObjC, .carthage:
            XCTAssert(app.navigationBars[sampleAppTitle].waitForExistence(timeout: timeout), appLoadError)
        case .nativeSwift:
            let title = app.navigationBars["Accounts"].staticTexts["Accounts"]
            XCTAssert(title.waitForExistence(timeout: timeout), appLoadError)
                
            // Accounts List
            let contacts = app.collectionViews.cells
             _ = contacts.firstMatch.waitForExistence(timeout: timeout)
             XCTAssertGreaterThan(contacts.count, 0, "Swift UI did not load Account List.")
            
            // Contacts List
            contacts.element(boundBy: 0).tap()
            XCTAssertGreaterThan(contacts.count, 0, "RestClient did not retrieve Contacts for Account.")
            
            // Contact Details
            contacts.element(boundBy: 0).tap()
            XCTAssertGreaterThan(contacts.count, 0, mobileSyncError)
        case .hybridRemote:
            verifyInWebView(app: app, text: "Salesforce Mobile SDK Test")
        case .hybridLocal:
            if app.complexHybrid == "accounteditor" {
                verifyInWebView(app: app, text: "Accounts")
            } else if app.complexHybrid == "mobilesyncexplorer" {
                verifyInWebView(app: app, text: "Contacts")
                verifyInWebView(app: app, text: "Tim Barr")
            } else {
                verifyInWebView(app: app, text: "Contacts")
                verifyInWebView(app: app, text: "Sean Forbes")
            }
        case .reactNative:
            let titleElement = app.otherElements[sampleAppTitle].firstMatch
            XCTAssert(titleElement.waitForExistence(timeout: timeout * 3), appLoadError)
        case .mobileSyncSwift:
            let title = app.navigationBars["Contacts"].staticTexts["Contacts"]
            XCTAssert(title.waitForExistence(timeout: timeout), appLoadError)
                
            // Check MobileSync Works
            let contact = app.collectionViews.staticTexts["VP, Facilities"]
            XCTAssert(contact.waitForExistence(timeout: timeout), mobileSyncError)
            contact.tap()
            XCTAssert(app.navigationBars["John Bond"].waitForExistence(timeout: timeout), mobileSyncError)
        case .mobileSyncReact:
            let title = app.otherElements["Contacts"].firstMatch
            XCTAssert(title.waitForExistence(timeout: timeout * 2), appLoadError)
        }
    }
    
    private func verifyInWebView(app: TestApplication, text: String) {
        let webElement = app.staticTexts[text]
        let exists = NSPredicate(format: "exists == 1")
        
        expectation(for: exists, evaluatedWith: webElement, handler: nil)
        waitForExpectations(timeout: timeout, handler: nil)
        XCTAssert(webElement.exists, appLoadError)
    }
}
