#!/usr/bin/env bash

# MacOS specific dependencies
if [ "$(uname)" == "Darwin" ]; then
    gem install cocoapods
    npm install plist

    if [[ -n ${GITHUB_WORKFLOW} ]]; then
        brew install xcbeautify
    fi
fi

# Hybrid
npm install -g cordova
cordova telemetry off

# React Native
npm install -g yarn
npm install -g typescript

# SF CLI
npm install -g @salesforce/cli

# Packager Repo used to build apps.
git clone --branch dev --single-branch --depth 1 https://github.com/forcedotcom/SalesforceMobileSDK-Package.git
# shellcheck disable=SC2164
(cd SalesforceMobileSDK-Package; npm install)

# Build CLI
./gradlew :TestOrchestrator:installDist