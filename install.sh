#!/usr/bin/env bash

# MacOS specific dependencies
if [ "$(uname)" == "Darwin" ]; then
    gem install cocoapods
    npm install plist

    if [[ ! -z ${GITHUB_WORKFLOW} ]]; then
        brew install xcbeautify
    fi
fi

# Hybrid
npm install -g cordova
cordova telemetry off
# React Native
npm install -g typescript
# SF CLI
npm install -g @salesforce/cli
# Fastlane for CLI, App Building, and test launching.
gem install --no-document fastlane

# Packager Repo used to build apps.
git clone --branch dev --single-branch --depth 1 https://github.com/forcedotcom/SalesforceMobileSDK-Package.git
cd SalesforceMobileSDK-Package && node ./install.js