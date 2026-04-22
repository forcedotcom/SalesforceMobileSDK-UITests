[![Build and Test All Apps](https://github.com/forcedotcom/SalesforceMobileSDK-UITests/actions/workflows/nightly.yaml/badge.svg?branch=master)](https://github.com/forcedotcom/SalesforceMobileSDK-UITests/actions/workflows/nightly.yaml)

# Salesforce MobileSDK UI Tests

This repo contains tests designed to validate the functionality of apps created using the MobileSDK CLI tools [forcedroid](https://www.npmjs.com/package/forcedroid), [forceios](https://www.npmjs.com/package/forceios), [forcehybrid](https://www.npmjs.com/package/forcehybrid), and [forcereact](https://www.npmjs.com/package/forcereact). Android and iOS test frameworks exist in their own directories and use separate technologies (UIAutomator and XCUITest, respectively).  However, they share a common CLI for end-to-end execution:
1.  `install.sh`
2.  Run `./test` with your desired arguments:

```terminaloutput
Usage: test-orchestrator <os> [<app type or template>]... [<options>]

Arguments:
  <os>                    android or ios
  <app type or template>  App type (native, native_kotlin, native_swift, hybrid_local, hybrid_remote, complex_hybrid_account_editor, complex_hybrid_mobile_sync, react_native)
                          or a template URL/name (CamelCase or URL)
                          or a complex hybrid sample name (e.g. accounteditor)
                          (multiple allowed, space separated)

Options:
  -d, --compileDebug                   Compile and use the debug configuration of the generated app(s).
  --ios, --iOSVersion=<text>           iOS version to test. If only the major version is provided, the highest available minor version is used.
                                       Multiple allowed with repeated flag or single quoted space separated list.
                                       (ex: --iOS=18.5 --iOS=18.6 or --iOS "17 18 26")
  --device, --iOSDevice=<text>         iOS Simulator device type. Uses SimDeviceType identifier format. (ex: iPhone-SE-3rd-generation)
  -r, --reRun                          Run the validation test again without re-generating the app.
  --sf, --sfdx                         Use SF (formerly SFDX) to generate the app.
  -p, --preserverGeneratedApps         Do not cleanup generated apps from previous runs.
  -u, --upgrade, --upgradeFrom=<text>  Run an upgrade test. Provide the SDK version/branch/tag to upgrade FROM (e.g. '12.1.0').
                                       The app is generated with this version, logged in, then upgraded to dev and verified.
  -f, --firebase=true|false            Run Android tests in Firebase Test Lab. Defaults to on for CI and off otherwise. (default: false)
  -v, --verbose                        Show all command output. Automatically on for CI.
  -h, --help                           Show this message and exit
```

----------

##### Local Testing

iOS runs create (and later destroy) a simulator to test against.  Due to the overhead of downloading/installing/booting different Android emulator configurations, local builds simply run against whichever emulators are currently open.  If the `--firebase` option is provided the test will execute against all supported API levels simultaniously in Test Lab.

To compile the `test` executable after making changes to the `TestOrchestrator` project simply run `./compile.sh`.

However, for local development of the cli it may be more convenient execute the code directly with Gradle:
```bash
./gradlew :TestOrchestrator:run --args="android native" 
```

##### App Regeneration Behavior

| `--reRun` | `--preserverGeneratedApps` | Behavior                                                              |
|-----------|----------------------------|-----------------------------------------------------------------------|
| set       | either                     | Skip deletion entirely — re-use existing app(s)                       |
| not set   | not set                    | Delete all `tmp*` dirs and regenerate everything                      |
| not set   | set                        | Delete only the specified app(s) and regenerate them; preserve others |

##### Upgrade Testing

The `--upgrade` option enables upgrade testing, which validates that a user's login session survives an SDK version upgrade. The flow is:

1. Clone `SalesforceMobileSDK-Package` at the specified old version
2. Generate, customize, and compile the app using the old SDK
3. Install the old app and run the login test
4. Re-generate and re-compile the same app using the current (`dev`) SDK
5. Install the new app **over** the old one (preserving app data)
6. Run the upgrade test asserting the user is still logged in

```bash
# Upgrade from version 12.1.0 on Android
./test android MobileSyncExplorerKotlin --upgrade=12.1.0

# Upgrade from version 12.1.0 on iOS
./test ios MobileSyncExplorerSwift --upgrade=12.1.0 --ios 26
```

**Constraints:**
- `--upgrade` cannot be combined with `--reRun`
- `--upgrade` is not supported with Firebase Test Lab (`--firebase`); Android upgrade tests run on a local emulator
- The old SDK version must be a valid branch or tag in `SalesforceMobileSDK-Package`
