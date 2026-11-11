# Building Dicta

The public Android application ID is `dev.randolf.dicta`; version 1.0.0 uses
versionCode 5. The Kotlin namespace remains `dev.dicta.prototype`. This keeps
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

Debug and release builds use different signing identities. Do not install a
debug build over the public app; Android rejects that signature change.

For unattended signing with the existing Dicta identity, see
[SIGNING.md](SIGNING.md). Never commit a JKS, password, API key or generated
APK. Signed APKs are distributed as GitHub Release assets.

The default dictionary profile lives in [profiles/default](../profiles/default/README.md).
The build packages those canonical files without editing them. BYOK credentials
are configured in the app and are never part of the build.

Development and validation records:

- [1.0.0 release verification](releases/v1.0.0/REPORT.md)
- [TTS and real-site device showcase](m4/REPORT.md)
- [History, Vocabulary, daily words and Profiles](m3/REPORT.md)
- [BYOK and credential protection](m2/REPORT.md)
- [Native app and text selection popup](m1/REPORT.md)
- [Initial specifications and feasibility](m0/REPORT.md)

Only work on `main`. Do not create branches or worktrees, rebase, or force push.
Use targeted tests for the change being made; historical reports describe their
original test scope and should not be read as fresh release results.

The v1.1.0 T07 candidate uses code 6 / `1.1.0-candidate`. Build it with the
same debug command above; it uses a debug certificate and cannot update the
signed public v1.0.0 app. Do not uninstall the public app to test this candidate.
The existing release signing identity, tag and GitHub Release remain unchanged.

For T07's local TLS mock acceptance suite, run
`bash tools/t07/test.sh emulator-5554` with the actual emulator serial. The
script refuses physical devices, preserves application data and excludes the
historical live-API / installed-TTS-engine tests. See [T07's report](t07/REPORT.md)
for the responsive matrix, test evidence and screenshot review index.

The 2026-11-11 product decisions govern the v1.1.0 release process: finish T07's
daily-word automation, provider credential boundary and etymology display fixes,
then complete emulator regression and screenshot review. Wait for the user's
explicit release approval before starting T08 or signing, tagging and publishing.
T08 verifies retained-data upgrades with the existing release identity in an
emulator/test environment. The user will try the public release on their Ace 6T
after publication; a physical phone is not a pre-release acceptance gate.
