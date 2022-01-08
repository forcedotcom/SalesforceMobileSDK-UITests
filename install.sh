#!/usr/bin/env bash

if [[ "$OSTYPE" == "linux-gnu" ]]; then
    sudo apt-get update
    sudo apt-get install libqt5widgets5
    sudo npm install -g cordova@11.0.0
    sudo cordova telemetry off
    sudo npm install -g typescript
    gem install --no-document fastlane
else
    npm install -g cordova@11.0.0
    cordova telemetry off
    sudo gem install cocoapods
    sudo npm install plist
    sudo gem install fastlane
    sudo npm install -g typescript
    sudo gem install --no-document fastlane
    sudo chown -R $USER:$GROUP /Users/distiller/.config/yarn
fi

git clone --branch dev --single-branch --depth 1 https://github.com/forcedotcom/SalesforceMobileSDK-Package.git
cd SalesforceMobileSDK-Package && sudo node ./install.js