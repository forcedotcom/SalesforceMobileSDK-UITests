[![CircleCI](https://circleci.com/gh/forcedotcom/SalesforceMobileSDK-UITests/tree/master.svg?style=svg)](https://circleci.com/gh/forcedotcom/SalesforceMobileSDK-UITests/tree/master)

# Salesforce MobileSDK UI Tests

This repo contains tests designed to validate the functionality of apps created using the MobileSDK CLI tools [forcedroid](https://www.npmjs.com/package/forcedroid), [forceios](https://www.npmjs.com/package/forceios), and [forcehybrid](https://www.npmjs.com/package/forcehybrid).  Android and iOS test frameworks exist in their own directories and use separate technologies (UIAutomator and XCUITest, respectively).  However, they share a common fastlane file for end-to-end execution:
1.  `install.sh`
2.  From the `.circleci` directory execute: `fastlane <os> (type:<AppType> || template:<TemplateName>) [options]` 
3.  
Additional options: `passcode:<true/false>`, `adv_auth:<true/false>`, `sfdx:<true/false>`, `rerun:<true/false>`.

examples: 

       fastlane ios type:hybrid_local

       fastlane android type:native_kotlin passcode:true

       fastlane ios template:https://github.com/forcedotcom/SalesforceMobileSDK-Templates/MobileSyncExplorerSwift\#dev

----------

#### Platform Differences

iOS exclusive options: `device:<"iPhone X"/iPhone-8/etc>`, `ios:<12.4/12-4/etc>`.

Android exclusive options: `firebase:<true/false>`.

##### Local Testing
For testing iOS the options above (or the defaults if not supplied) will determine what simulator gets created for the test run.  Due to the overhead of downloading/installing/booting different Android emulator configurations, local builds simply run against which ever emulator is currently open.  

##### CI
Individual iOS test runs work exactly the same in CI as they do locally.  Due to the setup time required for cocoapods, all app type tests are grouped together into two parallel runs. One run for the minimum supported version of iOS, and one for the latest release.  

Android uses Firebase Test Lab for executing the tests in CI.  Because of this, it is much more efficient to do the exact opposite of iOS and split test runs up by app type, since we can test all supported API versions at the same time per app.
