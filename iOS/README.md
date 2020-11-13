#  Mobile SDK UI Tests for iOS

These tests are designed to validate the functionality of iOS apps created using the MobileSDK CLI tools.  Unless otherwise specified each test should run against apps generated using native Objective-C/Swift, Cordova, or React Native code.

Basic usage:
1.  Build the app(s) to be tested on a specific device.
2.  Run the tests here against the same device and supply the test app's bundle id as a parameter to the arg `TEST_APP_BUNDLE`.

ex:  `xcodebuild -scheme MobileSDKUITest -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone Xʀ,OS=14.1' TEST_APP_BUNDLE='com.salesforce.App-native-ios' test`


For Development purposes tests can be run in XCode by hardcoding the `bundleString` variable in `TestApplication.swift` and running against a device that has the app associated with that bundle ID installed. 
