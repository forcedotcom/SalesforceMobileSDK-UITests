#!/usr/bin/env bash

if [[ "$OSTYPE" == "linux-gnu" ]]; then
    sudo apt-get update
    sudo apt-get install libqt5widgets5
    sudo apt install ninja-build
else
    sudo gem install cocoapods
    sudo npm install plist
fi

sudo npm install -g cordova
cordova telemetry off
sudo npm install -g typescript
sudo gem install --no-document fastlane

git clone --branch dev --single-branch --depth 1 https://github.com/forcedotcom/SalesforceMobileSDK-Package.git
cd SalesforceMobileSDK-Package && sudo node ./install.js