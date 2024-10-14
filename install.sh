#!/usr/bin/env bash

if [[ "$OSTYPE" == "linux-gnu" ]]; then
    sudo apt-get update
    # sudo apt-get install libqt5widgets5
    # sudo apt install ninja-build
else
    gem install cocoapods
    npm install plist
    gem install --no-document fastlane
fi

npm install -g cordova
cordova telemetry off
npm install -g typescript

git clone --branch dev --single-branch --depth 1 https://github.com/forcedotcom/SalesforceMobileSDK-Package.git
cd SalesforceMobileSDK-Package && node ./install.js