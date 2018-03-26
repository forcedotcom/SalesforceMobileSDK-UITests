[![CircleCI](https://circleci.com/gh/forcedotcom/SalesforceMobileSDK-UITests/tree/master.svg?style=svg)](https://circleci.com/gh/forcedotcom/SalesforceMobileSDK-UITests/tree/master)

# Salesforce MobileSDK UI Tests

This repo contains tests designed to validate the functionality of apps created using the MobileSDK CLI tools [forcedroid](https://www.npmjs.com/package/forcedroid), [forceios](https://www.npmjs.com/package/forceios), and [forcehybrid](https://www.npmjs.com/package/forcehybrid).  Android and iOS test frameworks exist in their own directories and use separate technologies (UIAutomator and XCUITest, respectively).  However, they share a common fastlane file for end-to-end execution:
1.  `install.sh`
2.  From the `.circleci` directory execute: `fastlane <os> type:<AppType>`
       
       ex: `fastlane android type:native` or `fastlane ios type:react_native`

       
----------
      
Additional options: `sfdx:<true/false>`, `skipAppRemoval:<true/false>`.

iOS exclusive options: `device:<"iPhone X"/iPhone-7/etc>`, `ios:<0.3/11-1/etc>`.
       
##### Adding New Tests
Info about test suites and tests only applicable to one app type coming soon.  
    
##### Troubleshooting
If the simulator/emulator fails to login because it needs a verification code, whitelist your IP by doing the following:
1.  Login to the production org 
2.  Go to Login History and confirm the issue by finding the entry with the status `Failed: Computer activation required`.
3.  Copy the source IP 
4.  Open Network Access and add a new range that captures your IP, such as `X.0.0.0` to `X.255.255.255`.  
