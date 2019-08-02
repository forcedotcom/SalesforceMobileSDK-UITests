[![CircleCI](https://circleci.com/gh/forcedotcom/SalesforceMobileSDK-UITests/tree/master.svg?style=svg)](https://circleci.com/gh/forcedotcom/SalesforceMobileSDK-UITests/tree/master)

# Salesforce MobileSDK UI Tests

This repo contains tests designed to validate the functionality of apps created using the MobileSDK CLI tools [forcedroid](https://www.npmjs.com/package/forcedroid), [forceios](https://www.npmjs.com/package/forceios), and [forcehybrid](https://www.npmjs.com/package/forcehybrid).  Android and iOS test frameworks exist in their own directories and use separate technologies (UIAutomator and XCUITest, respectively).  However, they share a common fastlane file for end-to-end execution:
1.  `install.sh`
2.  From the `.circleci` directory execute: `fastlane <os> type:<AppType>`
       
       ex: `fastlane android type:native_kotlin` or `fastlane ios type:hybrid_local`

      
Additional options: `sfdx:<true/false>`, `rerun:<true/false>`.

----------

#### Platform Differences

iOS exclusive options: `device:<"iPhone X"/iPhone-7/etc>`, `ios:<11.4/11-4/etc>`.

##### Local Testing
For testing iOS the options above (or the defaults if not supplied) will determine what simulator gets created for the test run.  Due to the overhead of downloading/installing/booting different Android emulator configurations, local builds simply run against which ever emulator is currently open.  

##### CI
Individual iOS test runs work exactly the same in CI as they do locally.  Due to the setup time required for cocoapods, all app type tests are grouped together into two parallel runs. One run for the minimum supported version of iOS, and one for the latest release.  

Android uses Firebase Test Lab for executing the tests in CI.  Because of this, it is much more efficient to do the exact opposite of iOS and split test runs up by app type, since we can test all supported API versions at the same time per app.
