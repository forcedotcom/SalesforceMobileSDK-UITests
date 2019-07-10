#!/usr/bin/env bash

if [[ "$OSTYPE" == "linux-gnu" ]]; then
    sudo apt-get update
    sudo apt-get install libqt5widgets5
    sudo npm install -g cordova@8.1.2
    sudo cordova telemetry off
    gem install --no-document fastlane
else
    npm install -g cordova@8.1.2
    cordova telemetry off

    if [ $(pod --version) != "1.6.0" ]; then
        echo y | sudo gem uninstall cocoapods
        sudo gem install cocoapods -v 1.6.0
    fi
fi

git clone --branch dev --single-branch --depth 1 https://github.com/forcedotcom/SalesforceMobileSDK-Package.git
cd SalesforceMobileSDK-Package && node ./install.js