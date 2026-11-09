# M2 verification

Use `tools/m1/build.sh` for the isolated, pinned JDK/SDK/Gradle environment.

- `provision.py`: install the app and test APK first. The test generates a fresh in-memory RSA key pair, writes only its public key, receives an RSA-OAEP encrypted payload through stdin/run-as, and saves the credential with Android Keystore AES-GCM. Only the external authorized key file is read. No plaintext key in commands, device files, output, or clipboard. Requires Python cryptography. The receiver exists only in androidTest, never in the app APK.
- `instrument.py`: run the installed test APK through `am instrument`; does not uninstall or clear the app. Network device tests launch our foreground Activity to respect the existing phone background network restrictions. Does not change system settings.
- `host.html`: account-free, public controlled text for **real** Chrome selection. Serve on localhost:8766, temporarily add only tcp:8766 ADB reverse, open Chrome, long-press petrichor → More → Dicta; observe live result and close/Back/outside return. Remove that reverse and stop only this server afterward.
- `finish_device_flow.py`: observed-coordinate UI follow-up on the same controlled Chrome page, immediate Back/no late popup, then cold restart and a fresh live Home query. Reinspect the menu before reuse on a different viewport.
- `check_delivery.py`: exact credential scan of unpacked app/test APKs, canonical assets, exclusion of FakeProvider/provision receiver from the app, allowed permissions, device ciphertext, and app-only logs. Emits hashes and booleans, never sensitive contents.

Only use the device identity guard from `tools/m1/device.py`. Raw captures live under ignored `artifacts/m2/`; review before uploading. Do not capture Settings: the whole settings window is secure, and stored keys are never prefilled into editable fields.

See `docs/m2/REPORT.md` and its evidence for actual results. Direct instrumentation is deliberate: Gradle UTP on this environment uninstalls the tested package after running, which discards its test configuration. Never reset app/device or modify network/Chrome/account/system preferences for tests.
