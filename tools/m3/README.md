# M3 verification

- Build with `tools/m1/build.sh`; add `:app:assembleDebugAndroidTest` for device tests.
- `install.py` verifies the sole A6013 / fajita and upgrades app/test APKs in place. Never clear data or run Gradle UTP, which uninstalls this app after testing.
- `instrument.py M3StorageTest M3ProfileDeviceTest M3UiTest LargeFontPopupTest PlatformTest` runs the 22 relevant device tests. Isolated Room tests delete only their UUID-named test databases; other tests retain the real encrypted BYOK configuration and saved user entries.
- `check_delivery.py` scans unpacked APKs and app logs for the exact external development key, checks canonical assets and permissions, and compares the existing encrypted credential file with the pre-upgrade fingerprint in ignored `artifacts/m3/credential-before.json`. It prints no credentials or ciphertext.
- For a fresh fingerprint, read only `no_backup/provider.enc` through the guarded ADB helper and store its SHA256/size; never provision, overwrite, or clear existing credentials for M3.
- Actual Chrome menu verification uses the account-free `tools/m2/host.html` on localhost:8766 and only a temporary matching ADB reverse. Inspect the current page/menu before clicking. Explicit PROCESS_TEXT is a diagnostic and does not replace this check.
- Raw screenshots and local state captures remain under ignored `artifacts/m3/`. Review before uploading to the project Drive folder. Never capture BYOK Settings; Profile is a separate page with no device credentials.

See `docs/m3/REPORT.md` for the delivered results and screenshot links. API35 is the tested device; TTS and final visual acceptance remain M4.
