# M1 verification tools

- `build.sh`: per-command isolated toolchain, pinned Gradle Wrapper; default assembleDebug + JVM tests + lintDebug.
- `device.py`: verifies exactly one authorized OnePlus A6013/fajita before each operation. Captures go to ignored `artifacts/m1/`. Never capture a personal screen; inspect locally before upload.
- `host.html`: controlled Chrome text and editable field, no external requests or account.
- `smoke.py`: UIAutomator XML helpers for real Chrome/menu taps and explicit diagnostic intents. Full activity stack is never persisted.
- `check_apk.py`: exact credential byte scan of decompressed APK members, canonical Profile/fixture asset equality, forbidden permission checks; prints only hashes/counts. The credential is used only as a local scan sentinel.

See `docs/m1/REPORT.md` for actual results and limits. Device UI coordinates depend on current screen; inspect nodes before tapping. Only main is permitted. Never reset apps/device, modify global font/rotation settings, or clear browser history for a test.
