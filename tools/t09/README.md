# T09 isolated Debug verification

Run from `main` with the explicit serial of the sole attached OnePlus 6T:

```sh
bash tools/t09/test.sh e5ee4f28
```

The runner checks ADB uniqueness and the `ONEPLUS A6013` / OnePlus6T or
LineageOS `fajita` identity before device operations. It builds only Debug and
its test APK, runs JVM tests then Debug lint, verifies both isolated package
IDs before installation, and requires JUnit's nonzero `OK` test count. It
preserves installed application data and never installs over `dev.randolf.dicta`.
It runs the T09 compatibility suite plus the two existing T07 Popup regression
tests against the isolated Debug app. Before instrumentation it sends
`KEYCODE_WAKEUP` and polls `dumpsys power` for `mWakefulness=Awake` for up to two
seconds. This phone was observed to remain Dozing after WAKEUP; if it is still
not Awake, the runner sends `KEYCODE_POWER` once and allows up to three more
seconds, then stops unless Awake is confirmed. An already unlocked phone is
accepted. The runner dismisses only a non-secure keyguard and requires
`showing=false`; if a secure or unknown keyguard is showing, unlock the phone
manually before rerunning. No system screen timeout changes are made by the
runner. The test fixture keeps its
own Activity awake. Check the phone's screen state and turn it off after the
authorized journeys and graceful teardown.
The targeted instrumented tests require an unconfigured Debug app and refuse
to start if its encrypted provider configuration exists. This check runs before
the Home Activity can request a daily word. They then use an on-device TLS mock
with external DNS disabled; no real API key or paid model call is used. Existing
Debug credentials are preserved on refusal; do not clear user data to bypass it.

For actual Readest and Chrome UI journeys, first run the guarded command above,
then start the opt-in mock review session in a terminal:

```sh
adb -s e5ee4f28 shell am instrument -w -r \
  -e class dev.dicta.prototype.T09ReviewSession -e review true \
  dev.randolf.dicta.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Use another terminal to check readiness and, when finished, stop gracefully:

```sh
adb -s e5ee4f28 shell run-as dev.randolf.dicta.debug cat files/t09-review-ready
# Perform the authorized Readest/Chrome journeys while this session is active.
adb -s e5ee4f28 shell run-as dev.randolf.dicta.debug touch files/t09-review-stop
```

Wait for the instrumentation command to finish so fixture teardown can restore
the original Debug credentials/preferences and remove only its added data.
Do not force-stop or kill it before teardown. The session also expires after
45 minutes. It creates a device-local TLS provider and does not require ADB
reverse, system certificates, network/proxy changes, or production credentials.

Mock content proves the real Android selection, Popup, task/return, rendering,
and snapshot flows; generated definitions are fixture text and do not establish
live provider quality. Record actual reader/browser steps separately from
instrumented contract tests, and capture original PNGs only after checking
that accounts, notifications and secrets are absent.

The original requested Andy Weir *Artemis* is absent from the existing library.
The user explicitly selected the existing *Project Hail Mary* as the substitute
for this run. Use its existing approximately 1% reading position without page
changes. Record the starting position and Lookup App choice; temporarily select
`Dicta Debug` and `Default` only within the authorized journeys. Restore Lookup
App to production `Dicta`, keep the starting reading position, and verify that
both original books remain. Do not clear data, uninstall Readest/production Dicta,
or edit book files, notes or bookmarks. The runner leaves the isolated Debug
and test APKs installed; report their retention or remove only these test
packages after graceful session completion. No release signing, tag or GitHub
Release belongs to T09.
