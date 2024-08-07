#!/usr/bin/env bash

if [[ "$OSTYPE" == "linux-gnu" ]]; then
    sudo apt-get update
    sudo apt-get install libqt5widgets5
    sudo apt install ninja-build
    sudo npm install -g cordova
    cordova telemetry off
    sudo npm install -g typescript
    sudo gem install --no-document fastlane
else
    sudo npm install -g cordova
    cordova telemetry off
    sudo gem install cocoapods
    sudo npm install plist
    sudo gem install fastlane
    sudo npm install -g typescript
    sudo gem install rb-readline
    sudo gem install --no-document fastlane
    sudo chown -R $USER:$GROUP /Users/$USER/.config/yarn
fi

git clone --branch dev --single-branch --depth 1 https://github.com/forcedotcom/SalesforceMobileSDK-Package.git
cd SalesforceMobileSDK-Package && sudo node ./install.js