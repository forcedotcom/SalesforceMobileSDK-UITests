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
//  LoginTest.swift
//  MobileSDKUITest
//
//  Created by Brandon Page on 2/2/18.
//

import XCTest

class LoginTests: XCTestCase {
    private var username = UserUtility().username
    private var password = UserUtility().password
    private var appLoadError = "App did not load."
    private var smartSyncError = "MobileSync did not pull data."
    private var timeout:double_t = 30
    private let reactNativeUsers = "Automated Process Brandon Page circleci Integration User Security User Chatter Expert Mobile SDK Sample App"
    private let sampleAppTitle = "Mobile SDK Sample App"
    
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
        let isAdvAuth = app.advAuth
        app.launch()
        
        if (isAdvAuth) {
            let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let alertMessage = springboard.alerts["“\(app.name)” Wants to Use “salesforce.com” to Sign In"]
            _ = alertMessage.waitForExistence(timeout: timeout)
            springboard.buttons["Continue"].tap()
        }
        
        loginPage.setUsername(name: username)
        loginPage.setPassword(password: password)
        loginPage.tapLogin()
        
        if !isAdvAuth {
            authPage.tapAllow()
        }
        
        // Assert App loads
        switch app.type {
        case .nativeObjC, .nativeSwift:
            XCTAssert(app.navigationBars[sampleAppTitle].waitForExistence(timeout: timeout), appLoadError)
        case .hybridLocal, .hyrbidRemote:
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
        case .smartSyncSwift:
            let title = app.navigationBars["MobileSync Explorer"].otherElements["MobileSync Explorer"]
            XCTAssert(title.waitForExistence(timeout: timeout), appLoadError)
            
            // Check MobileSync Works
            _ = app.tables.cells.firstMatch.waitForExistence(timeout: 3)
            XCTAssertGreaterThan(app.tables.cells.count, 0, smartSyncError)
        case .smartSyncReact:
            sleep(30)
            let title = app/*@START_MENU_TOKEN@*/.staticTexts["Contacts"]/*[[".otherElements.matching(identifier: \"Search a contact...  TB Tim Barr SVP, Administration and Finance  JB John Bond VP, Facilities  LB Lauren Boyle SVP, Technology  LD Lizzz D'Cruz VP, Production  JD Josh Davis Director, Warehouse Mgmt  SF Sean Forbes CFO  EF Edna Frank VP, Technology  TG Testandroid Gchkvfd  RG Rose Gonzalez SVP, Procurement  AG Avi Green CFO  JG Jane Grey Dean of Administration  TH Testios Hshaha  AJ Ashley James VP, Finance  FL First928848 Last928848  BL Babara Levy SVP, Operations  SP Stella Pavlova SVP, Production  TR Tom Ripley Regional General Manager  JR Jack Rogers VP, Facilities  AS Arthur Song CEO  PS Pat Stumuller SVP, Administration and Finance  S SyncManagerTestCase_Contact_05984046  S SyncManagerTestCase_Contact_06777687  S SyncManagerTestCase_Contact_20980716  S SyncManagerTestCase_Contact_21600929  S SyncManagerTestCase_Contact_39435484  S SyncManagerTestCase_Contact_39883565  S SyncManagerTestCase_Contact_42527071  S SyncManagerTestCase_Contact_43536868  S SyncManagerTestCase_Contact_44546449  S SyncManagerTestCase_Contact_51592337  S SyncManagerTestCase_Contact_54696463  S SyncManagerTestCase_Contact_55085218  S SyncManagerTestCase_Contact_56513612  S SyncManagerTestCase_Contact_61167456  S SyncManagerTestCase_Contact_71855613  S SyncManagerTestCase_Contact_73858564  S SyncManagerTestCase_Contact_76118170  S SyncManagerTestCase_Contact_98648664  TT TestTargetiOS12 TestXCode10  IT Ios12 Testnewos Test stuff  XX Xcode10test Xcode10works  AY Andy Young SVP, Operations  wj web-test justatest  Contacts    Dismiss All Warning: componentWillReceiveProps is deprecated and will be removed in the next major version. Use static getDerivedStateFromProps instead.\\n\\nPlease update the following components: SafeView, Transitioner\\n\\nLearn more about this warning here:\\nhttps:\/\/fb.me\/react-async-component-lifecycle-hooks\")",".otherElements.matching(identifier: \"Search a contact...  TB Tim Barr SVP, Administration and Finance  JB John Bond VP, Facilities  LB Lauren Boyle SVP, Technology  LD Lizzz D'Cruz VP, Production  JD Josh Davis Director, Warehouse Mgmt  SF Sean Forbes CFO  EF Edna Frank VP, Technology  TG Testandroid Gchkvfd  RG Rose Gonzalez SVP, Procurement  AG Avi Green CFO  JG Jane Grey Dean of Administration  TH Testios Hshaha  AJ Ashley James VP, Finance  FL First928848 Last928848  BL Babara Levy SVP, Operations  SP Stella Pavlova SVP, Production  TR Tom Ripley Regional General Manager  JR Jack Rogers VP, Facilities  AS Arthur Song CEO  PS Pat Stumuller SVP, Administration and Finance  S SyncManagerTestCase_Contact_05984046  S SyncManagerTestCase_Contact_06777687  S SyncManagerTestCase_Contact_20980716  S SyncManagerTestCase_Contact_21600929  S SyncManagerTestCase_Contact_39435484  S SyncManagerTestCase_Contact_39883565  S SyncManagerTestCase_Contact_42527071  S SyncManagerTestCase_Contact_43536868  S SyncManagerTestCase_Contact_44546449  S SyncManagerTestCase_Contact_51592337  S SyncManagerTestCase_Contact_54696463  S SyncManagerTestCase_Contact_55085218  S SyncManagerTestCase_Contact_56513612  S SyncManagerTestCase_Contact_61167456  S SyncManagerTestCase_Contact_71855613  S SyncManagerTestCase_Contact_73858564  S SyncManagerTestCase_Contact_76118170  S SyncManagerTestCase_Contact_98648664  TT TestTargetiOS12 TestXCode10  IT Ios12 Testnewos Test stuff  XX Xcode10test Xcode10works  AY Andy Young SVP, Operations  wj web-test justatest  Contacts   \")",".otherElements.matching(identifier: \"Contacts   \")",".otherElements[\"Contacts\"].staticTexts[\"Contacts\"]",".staticTexts[\"Contacts\"]"],[[[-1,4],[-1,3],[-1,2,3],[-1,1,2],[-1,0,1]],[[-1,4],[-1,3],[-1,2,3],[-1,1,2]],[[-1,4],[-1,3],[-1,2,3]],[[-1,4],[-1,3]]],[0]]@END_MENU_TOKEN@*/
            XCTAssert(title.waitForExistence(timeout: timeout), appLoadError)
        }
    }
}
