#!/usr/bin/env bash
# Runs the android upgrade test inside reactivecircus/android-emulator-runner and
# captures on-device diagnostics if the test fails. The emulator step in the
# action invokes the yaml `script:` as per-line `sh -c`, so all multi-line
# shell logic must live in a single invocation (this file).
#
# Args:
#   $1 - app name (passed through to ./test android <app> --upgrade=<from>)
#   $2 - upgradeFrom (SDK version/branch/tag to upgrade from)

set -u

APP="${1:?app argument required}"
UPGRADE_FROM="${2:?upgradeFrom argument required}"

mkdir -p ci-diagnostics

# Stream logcat to a file in the background for post-mortem diagnostics.
adb logcat -v threadtime > ci-diagnostics/logcat.txt &
LOGCAT_PID=$!

./test android "$APP" --upgrade="$UPGRADE_FROM"
TEST_EXIT=$?

if [ "$TEST_EXIT" -ne 0 ]; then
  echo "=== Test failed (exit $TEST_EXIT); capturing device diagnostics ==="
  adb exec-out screencap -p                             > ci-diagnostics/screenshot.png             2>/dev/null || true
  adb shell uiautomator dump /sdcard/view-hierarchy.xml                                             >/dev/null 2>&1 || true
  adb pull  /sdcard/view-hierarchy.xml ci-diagnostics/view-hierarchy.xml                            2>/dev/null || true
  adb shell dumpsys window                              > ci-diagnostics/dumpsys-window.txt         2>/dev/null || true
  adb shell dumpsys activity top                        > ci-diagnostics/dumpsys-activity-top.txt   2>/dev/null || true
  adb shell dumpsys activity activities                 > ci-diagnostics/dumpsys-activities.txt     2>/dev/null || true
  adb shell dumpsys meminfo                             > ci-diagnostics/dumpsys-meminfo.txt        2>/dev/null || true
  adb shell dumpsys gfxinfo                             > ci-diagnostics/dumpsys-gfxinfo.txt        2>/dev/null || true
  adb shell ps -A                                       > ci-diagnostics/ps.txt                     2>/dev/null || true
  adb shell getprop                                     > ci-diagnostics/getprop.txt                2>/dev/null || true
fi

kill "$LOGCAT_PID" 2>/dev/null || true
wait "$LOGCAT_PID" 2>/dev/null || true

exit "$TEST_EXIT"
