# T11 signed production upgrade fixture

This fixture checks actual **official v1.1.0 → signed v1.2.0** updates on a
fresh, dedicated Android Emulator. It does not rerun T09 compatibility tests.
It refuses physical hardware, the Debug package, a wrong version/certificate,
or pre-existing application data during seeding. Never run it on a user's
phone or uninstall/clear an existing application to make it pass.

The test source and manifest are isolated from the normal app build by an
optional Gradle init script. The production APK contains no test fixture.
The test APK needs the same existing release certificate to instrument the
non-debuggable official APK. Keep that test APK local under ignored artifacts;
publish only the four reviewed release assets, never the test APK or AVD.

## Build

```sh
bash tools/m1/build.sh --no-daemon --no-parallel --max-workers=1 \
  -I tools/t11/upgrade.init.gradle :app:assembleReleaseAndroidTest
python3 scripts/android-signing.py release --auto \
  --apk app/build/outputs/apk/androidTest/release/app-release-androidTest.apk \
  --out artifacts/release/v1.2.0/Dicta-T11-upgrade-test.apk
```

Use a fresh output path if the signed fixture already exists. Its explicit
test-manifest version allows the normal signer's metadata checks to run.
No signing code, pin, release configuration or credentials need changing.

## Run only on a new isolated emulator

T11 used a dedicated AOSP API36 AVD under ignored
`artifacts/release/v1.2.0/avds`, serial `emulator-5582`. Its config was copied
from the installed API36 profile with a separate AVD home and fresh userdata.
The existing AVD userdata and physical OnePlus 6T were not changed. The
initial read-only emulator probe was stopped before tests; all installs and
fixture data belong to the dedicated AVD.

Download v1.1.0 anonymously from the official GitHub Release; check its
recorded SHA-256 and original certificate before installing. Also verify the
new signed APK's package/version, original certificate, alignment and CRC.
Explicitly select the dedicated emulator in every ADB command:

```sh
adb -s emulator-5582 shell getprop ro.hardware  # Must be ranchu or goldfish.
adb -s emulator-5582 shell pm path dev.randolf.dicta  # Must be absent initially.
adb -s emulator-5582 install --no-streaming artifacts/release/v1.2.0/Dicta-v1.1.0-official.apk
adb -s emulator-5582 install --no-streaming artifacts/release/v1.2.0/Dicta-T11-upgrade-test.apk
adb -s emulator-5582 shell am instrument -w -r \
  -e class dev.dicta.prototype.UpgradeDataTest#seedOfficialV110 \
  dev.randolf.dicta.test/androidx.test.runner.AndroidJUnitRunner
# Require OK (1 test), not just exit code 0, before proceeding.
adb -s emulator-5582 shell am force-stop dev.randolf.dicta
adb -s emulator-5582 install --no-streaming -r artifacts/release/v1.2.0/Dicta-v1.2.0.apk
adb -s emulator-5582 shell am instrument -w -r \
  -e class dev.dicta.prototype.UpgradeDataTest#verifyOfficialV120 \
  dev.randolf.dicta.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5582 shell am start -W \
  -n dev.randolf.dicta/dev.dicta.prototype.platform.MainActivity
adb -s emulator-5582 shell am force-stop dev.randolf.dicta
adb -s emulator-5582 shell am instrument -w -r \
  -e class dev.dicta.prototype.UpgradeDataTest#verifyOfficialV120 \
  dev.randolf.dicta.test/androidx.test.runner.AndroidJUnitRunner
```

Require `Success` from update installation, `Status: ok` from cold launch,
and `OK (1 test)` from each instrumentation stage with no failure markers.
Pull the installed production APK using its `pm path` and compare its bytes
with the release asset. Stop only the dedicated emulator after verification;
retain its disposable data and local artifacts for diagnosis.

The fixture seeds an inert loopback HTTPS endpoint and a clearly fake key,
three entries, independent history/bookmark references, a daily selection,
custom Profile and preferences. It checks package/version/certificate and
non-debuggable flags; unchanged UID, first-install time, encrypted bytes and
logical rows in all six tables; actual Android Keystore decryption; saved
entry rendering; and new writes followed by removal of only the probe row.
No real provider request is made. This proves the tested upgrade path with
synthetic data, not byte equality of every user's phone data or OEM support.
