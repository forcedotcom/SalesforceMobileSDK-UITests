//
//  TestApplication.swift
//  MobileSDKUITest
//
//  Created by Brandon Page on 2/21/18.
//

import Foundation
import XCTest

class TestApplication: XCUIApplication {
    var bundleString = ""
    var appType: AppType.AppType = .nativeObjC
    
    override init() {
        // Get the Test App Bundle from command line arg
        bundleString = ProcessInfo.processInfo.environment["TEST_APP_BUNDLE"]!
        
        switch bundleString {
        case "com.salesforce.native-iosApp":
            appType = .nativeObjC
        case "com.salesforce.native-swift-iosApp":
            appType = .nativeSwift
        case "com.salesforce.hybrid_local":
            appType = .hybridLocal
        case "com.salesforce.hybrid_remote":
            appType = .hyrbidRemote
        case "com.salesforce.react-native-iosApp":
            appType = .reactNative
        default:
            assert(false, "Unknown AppType.")
        }
        
        super.init(bundleIdentifier: bundleString)
    }
    
    override func launch() {
        super.launch()
        
        if(appType == .reactNative) {
            sleep(30)
        }
    }
}
