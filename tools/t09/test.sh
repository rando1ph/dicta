#!/usr/bin/env bash
# T09: isolated Debug only, on the one explicitly named OnePlus 6T.
set -euo pipefail
dicta_serial="${1:?Usage: tools/t09/test.sh SERIAL}"
[[ $# == 1 ]] || { echo 'Pass exactly one explicit ADB serial.' >&2; exit 1; }
dicta_repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$dicta_repo"
[[ "$(git branch --show-current)" == main ]] || { echo 'T09 requires main.' >&2; exit 1; }

# Count every listed transport, including offline/unauthorized devices. Do not
# inspect or operate any device until the sole transport matches the argument.
dicta_verify_phone() {
    local -a dicta_devices
    local dicta_model dicta_device dicta_lineage
    mapfile -t dicta_devices < <(adb devices | awk 'NR > 1 && NF >= 2 { print $1 }')
    [[ ${#dicta_devices[@]} == 1 && "${dicta_devices[0]}" == "$dicta_serial" ]] || {
        echo 'T09 requires exactly one ADB transport, matching the explicit serial.' >&2
        exit 1
    }
    [[ "$(adb -s "$dicta_serial" get-state)" == device ]] || { echo 'The named phone is not ready.' >&2; exit 1; }
    dicta_model="$(adb -s "$dicta_serial" shell getprop ro.product.model | tr -d '\r')"
    dicta_device="$(adb -s "$dicta_serial" shell getprop ro.product.device | tr -d '\r')"
    dicta_lineage="$(adb -s "$dicta_serial" shell getprop ro.lineage.version | tr -d '\r')"
    [[ "$dicta_model" == 'ONEPLUS A6013' && ( "$dicta_device" == OnePlus6T || "$dicta_lineage" == *fajita* ) ]] || {
        echo 'The sole device is not the authorized OnePlus 6T (fajita).' >&2
        exit 1
    }
}
dicta_verify_phone

# Finish generated test sources before lint reads them. Never request a release
# task, production signing, or a connected-test task that chooses devices itself.
./tools/m1/build.sh :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
./tools/m1/build.sh :app:lintDebug
dicta_sdk="${DICTA_ANDROID_HOME:-${ANDROID_HOME:-$HOME/.local/share/dicta-tools/android-sdk}}"
dicta_aapt="$dicta_sdk/build-tools/35.0.0/aapt"
[[ -x "$dicta_aapt" ]] || { echo 'Build Tools 35.0.0 aapt is required.' >&2; exit 1; }
dicta_apk='app/build/outputs/apk/debug/app-debug.apk'
dicta_test_apk='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
dicta_package="$("$dicta_aapt" dump badging "$dicta_apk" | awk -F "'" '/^package: name=/ { print $2 }')"
dicta_test_package="$("$dicta_aapt" dump badging "$dicta_test_apk" | awk -F "'" '/^package: name=/ { print $2 }')"
[[ "$dicta_package" == dev.randolf.dicta.debug && "$dicta_test_package" == dev.randolf.dicta.debug.test ]] || {
    echo 'Refusing to install APKs without the exact isolated Debug identities.' >&2
    exit 1
}
# A build can take minutes; confirm the same unique phone again before writes.
dicta_verify_phone
adb -s "$dicta_serial" install -r "$dicta_apk"
adb -s "$dicta_serial" install -r "$dicta_test_apk"
# Build time can outlast the phone's normal timeout. Wake without changing it.
dicta_wait_awake() {
    local dicta_wake_deadline=$((SECONDS + $1))
    local dicta_power_state
    while true; do
        dicta_power_state="$(adb -s "$dicta_serial" shell dumpsys power)"
        if rg -q '^[[:space:]]*mWakefulness=Awake\r?$' <<< "$dicta_power_state"; then
            return 0
        fi
        (( SECONDS < dicta_wake_deadline )) || return 1
        sleep 0.2
    done
}
adb -s "$dicta_serial" shell input keyevent KEYCODE_WAKEUP
if ! dicta_wait_awake 2; then
    # On this LineageOS phone WAKEUP can leave the display Dozing. Toggle
    # POWER once only after observing that the phone is still not Awake.
    adb -s "$dicta_serial" shell input keyevent KEYCODE_POWER
    dicta_wait_awake 3 || { echo 'The phone did not become Awake; stopping before instrumentation.' >&2; exit 1; }
fi
dicta_keyguard_policy="$(adb -s "$dicta_serial" shell dumpsys window policy)"
if ! rg -q '^[[:space:]]*showing=false\r?$' <<< "$dicta_keyguard_policy"; then
    rg -q '^[[:space:]]*secure=false\r?$' <<< "$dicta_keyguard_policy" || {
        echo 'Unlock the phone manually before testing; refusing to bypass a secure or unknown keyguard.' >&2
        exit 1
    }
    adb -s "$dicta_serial" shell wm dismiss-keyguard
fi
dicta_keyguard_policy="$(adb -s "$dicta_serial" shell dumpsys window policy)"
if ! rg -q '^[[:space:]]*showing=false\r?$' <<< "$dicta_keyguard_policy"; then
    echo 'The phone is not confirmed unlocked; stopping.' >&2
    exit 1
fi
dicta_test_output="$(mktemp /tmp/dicta-t09-tests.XXXXXX)"
trap 'rm -f -- "$dicta_test_output"' EXIT
adb -s "$dicta_serial" shell am instrument -w -r \
    -e class dev.dicta.prototype.T09PopupCompatibilityTest,dev.dicta.prototype.T07PopupTest \
    dev.randolf.dicta.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "$dicta_test_output"
# am instrument can return zero after a JUnit failure. Require a nonzero test
# count in the success summary; a successful shell exit alone is insufficient.
rg -q '^OK \([1-9][0-9]* tests?\)' "$dicta_test_output"
