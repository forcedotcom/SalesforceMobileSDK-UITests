#!/usr/bin/env bash

function startAVD {
    export LD_LIBRARY_PATH=${ANDROID_HOME}/emulator/lib64:${ANDROID_HOME}/emulator/lib64/qt/lib
    echo "y" | sdkmanager "system-images;android-22;default;armeabi-v7a"
    echo "no" | avdmanager create avd -n test22 -k "system-images;android-22;default;armeabi-v7a"
    emulator -avd test22 -noaudio -no-window -accel on
}

function restartAVD {
    adb emu kill
    emulator -avd test22 -noaudio -no-window -accel on -wipe-data
}

function waitForAVD {
    set +e

    local bootanim=""
    export PATH=$(dirname $(dirname $(which android)))/platform-tools:$PATH
    until [[ "$bootanim" =~ "stopped" ]]; do
        sleep 5
        bootanim=$(adb -e shell getprop init.svc.bootanim 2>&1)
        echo "emulator status => $bootanim"
    done
    sleep 30
    # unlock the emulator screen
    adb shell input keyevent 82
    echo "Device Booted"
}