# Building Dicta

The public Android application ID is `dev.randolf.dicta`; version 1.2.0 uses
versionCode 7 (1.1.0 used code 6; 1.0.0 used code 5). The Kotlin namespace remains `dev.dicta.prototype`. This keeps
the package change small and lets the public app coexist with the earlier
`dev.dicta.prototype` test app. Do not uninstall that app or clear its data.

Use JDK 17/21, Android SDK platform 36 and Build Tools 35.0.0. Select your
toolchain per command with `DICTA_JAVA_HOME`, `DICTA_ANDROID_HOME`, and,
optionally, `DICTA_GRADLE_USER_HOME`; the helper does not change global tools.

```sh
# Unsigned production APK and release lint
./tools/m1/build.sh :app:assembleRelease :app:lintRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk

# Local debug build
./tools/m1/build.sh :app:assembleDebug
```

Debug builds use `dev.randolf.dicta.debug`, the `Dicta Debug` launcher and
PROCESS_TEXT label, and a `-debug` version suffix. They can coexist with the
public app and keep separate credentials, history and vocabulary. Release
retains `dev.randolf.dicta` and the existing signing identity. Do not install
historical, unisolated Debug APKs over the public app.

For unattended signing with the existing Dicta identity, see
[SIGNING.md](SIGNING.md). Never commit a JKS, password, API key or generated
APK. Signed APKs are distributed as GitHub Release assets.

The default dictionary profile lives in [profiles/default](../profiles/default/README.md).
The build packages those canonical files without editing them. BYOK credentials
are configured in the app and are never part of the build.

Development and validation records:

- [1.2.0 release verification](releases/v1.2.0/REPORT.md)
- [1.1.0 release verification](releases/v1.1.0/REPORT.md)
- [1.0.0 release verification](releases/v1.0.0/REPORT.md)
- [TTS and real-site device showcase](m4/REPORT.md)
- [History, Vocabulary, daily words and Profiles](m3/REPORT.md)
- [BYOK and credential protection](m2/REPORT.md)
- [Native app and text selection popup](m1/REPORT.md)
- [Initial specifications and feasibility](m0/REPORT.md)

Only work on `main`. Do not create branches or worktrees, rebase, or force push.
Use targeted tests for the change being made; historical reports describe their
original test scope and should not be read as fresh release results.

The historical v1.1.0 T07 candidate used code 6 / `1.1.0-candidate` and a debug
certificate. The final v1.1.0 build uses `1.1.0` and the existing release signing
identity. A debug build cannot update the signed public app; do not uninstall
the public app to test a debug build.

For T09's guarded OnePlus 6T Debug-only suite and local TLS review session, see
[tools/t09](../tools/t09/README.md). T09 performs no production signing or
release; its historical candidate used 1.1.0. T10 accepted that work and T11
authorizes the 1.2.0 production release.

For T07's historical (pre-Debug-isolation) local TLS mock acceptance suite, run
`bash tools/t07/test.sh emulator-5554` with the actual emulator serial. The
script refuses physical devices, preserves application data and excludes the
historical live-API / installed-TTS-engine tests. See [T07's report](t07/REPORT.md)
for the responsive matrix, test evidence and screenshot review index.
Its package-specific runner is historical; use the T09 runner for the current
isolated Debug identity and authorized physical-device checks.

For v1.2.0 / T11, use the accepted T09/T10 compatibility evidence. Run the
Release build, Release unit tests and Release lint; check the existing signing
identity and signing regression tests. Run compilation/tests and lint as
separate `--no-daemon --no-parallel --max-workers=1` invocations if lint retains
obsolete KAPT stub references from earlier builds. Do not suppress lint rules.
Verify an actual official v1.1.0 APK to signed v1.2.0 update on a fresh isolated
emulator, preserving Keystore-encrypted BYOK, History, Vocabulary and Profiles;
see [the upgrade fixture](../tools/t11/README.md). Do not operate on physical
phones or repeat the broad compatibility matrix or ColorOS 17 tests.
Publish only after these checks, then anonymously download every asset and
verify hashes, package/version, original certificate and latest routing.
Keep the website/README download entry pointing to GitHub's dynamic latest
release, without hard-coding a new website APK URL for each release.

The historical 2026-11-11 v1.1.0 release decision superseded its earlier upgrade-test plan:
T07 feature tests and user review are complete, and the user approved T08.
T08 builds and signs with the original certificate, checks package/version,
signature, alignment, ZIP integrity and SHA-256, publishes the tag and GitHub
Release, and verifies public assets. Do not repeat emulator regression, update
installation or physical-device tests for this release. The user will try the
public release on their Ace 6T after publication.
