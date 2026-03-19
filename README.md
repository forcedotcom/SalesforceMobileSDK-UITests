[![Build and Test All Apps](https://github.com/forcedotcom/SalesforceMobileSDK-UITests/actions/workflows/nightly.yaml/badge.svg?branch=master)](https://github.com/forcedotcom/SalesforceMobileSDK-UITests/actions/workflows/nightly.yaml)

# Salesforce MobileSDK UI Tests

This repo contains tests designed to validate the functionality of apps created using the MobileSDK CLI tools [forcedroid](https://www.npmjs.com/package/forcedroid), [forceios](https://www.npmjs.com/package/forceios), [forcehybrid](https://www.npmjs.com/package/forcehybrid), and [forcereact](https://www.npmjs.com/package/forcereact). Android and iOS test frameworks exist in their own directories and use separate technologies (UIAutomator and XCUITest, respectively).  However, they share a common CLI for end-to-end execution:
1.  `install.sh`
2.  Run `./test` with your desired arguments:

```terminaloutput
Usage: test <os> [<app type or template>]... [<options>]

Arguments:
  <os>                    android or ios
  <app type or template>  App type (native, native_kotlin, native_swift, hybrid_local, hybrid_remote, complex_hybrid_account_editor, complex_hybrid_mobile_sync, react_native)
                          or a template URL/name (CamelCase or URL)
                          or a complex hybrid sample name (e.g. accounteditor)
                          (multiple allowed, space separated)

Options:
  -d, --compileDebug            Compile and use the debug configuration of the generated app(s).
  --ios, --iOSVersion=<text>    iOS version to test. If only the major version is provided, the highest available minor version is used.
                                Multiple allowed with repeated flag or single quoted space separated list.
                                (ex: --iOS=18.5 --iOS=18.6 or --iOS "17 18 26")
  --device, --iOSDevice=<text>  iOS Simulator device type. Uses SimDeviceType identifier format. (ex: iPhone-SE-3rd-generation)
  -r, --reRun                   Run the validation test again without re-generating the app.
  -f, --firebase=true|false     Run Android tests in Firebase Test Lab. Defaults to on for CI and off otherwise. (default: false)
  --sf, --sfdx                  Use SF (formerly SFDX) to generate the app.
  -p, --preserverGeneratedApps  Do not cleanup generated apps from previous runs.
  -v, --verbose                 Show all command output. Automatically on for CI.
  -h, --help                    Show this message and exit
```

----------

##### Local Testing

iOS runs create (and later destroy) a simulator to test against.  Due to the overhead of downloading/installing/booting different Android emulator configurations, local builds simply run against which ever emulators are currently open.  If the `--firebase` option is provided the test will execute against all supported API levels simultaniously in Test Lab.

To compile the `test` executable after making changes to the `TestOrchestrator` project simply run: 
```bash
./gradlew :TestOrchestrator:installDist
```

However, for local development of the cli it the code can be executed directly with gradle:
```bash
./gradlew :TestOrchestrator:run --args="android native" 
```
