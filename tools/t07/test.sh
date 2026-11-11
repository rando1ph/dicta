#!/usr/bin/env bash
# T07's offline acceptance suite. Never lets adb choose a device implicitly.
set -euo pipefail
dicta_serial="${1:?Usage: tools/t07/test.sh emulator-SERIAL}"
[[ "$dicta_serial" == emulator-* ]] || { echo 'An emulator serial is required.' >&2; exit 1; }
[[ "$(adb -s "$dicta_serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'The named device is not an emulator.' >&2; exit 1; }
dicta_repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$dicta_repo"
./tools/m1/build.sh :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb -s "$dicta_serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$dicta_serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Historical live-API and installed-TTS-engine tests are explicitly excluded.
dicta_classes='dev.dicta.prototype.T07UiTest,dev.dicta.prototype.SettingsDeviceTest,dev.dicta.prototype.T07StorageTest,dev.dicta.prototype.T07PopupTest,dev.dicta.prototype.M3StorageTest,dev.dicta.prototype.M3ProfileDeviceTest,dev.dicta.prototype.M4AppearanceTest,dev.dicta.prototype.ByokDeviceTest#actualDeviceTlsTransportErrorsJsonTimeoutAndCancellation,dev.dicta.prototype.M4SpeechUiTest#longWordAndLongDefinitionWrapAtTwoHundredPercentFont'
dicta_test_output="$(mktemp /tmp/dicta-t07-tests.XXXXXX)"
trap 'rm -f -- "$dicta_test_output"' EXIT
adb -s "$dicta_serial" shell am instrument -w -r -e class "$dicta_classes" dev.randolf.dicta.test/androidx.test.runner.AndroidJUnitRunner | tee "$dicta_test_output"
# am instrument can exit zero even when JUnit fails; require the runner's success summary.
rg -q '^OK \([1-9][0-9]* tests?\)' "$dicta_test_output"
