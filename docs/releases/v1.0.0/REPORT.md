# Dicta 1.0.0 release verification

Release: [v1.0.0](https://github.com/rando1ph/dicta/releases/tag/v1.0.0).
Source is the commit identified by the annotated `v1.0.0` tag on `main`.
Verification date: 2026-11-10 (Asia/Shanghai).

## Application and artifact

| Field | Value |
| --- | --- |
| Application ID | `dev.randolf.dicta` |
| Kotlin namespace | `dev.dicta.prototype` (unchanged) |
| Version | `1.0.0` / versionCode `5` (previous code `4`) |
| Android | minSdk 26, target/compile SDK 36 |
| Build type | Release, not debuggable; unsigned Gradle output signed separately |
| Asset | `Dicta-v1.0.0.apk`, 8,485,513 bytes |
| APK SHA-256 | `557c8c3c055ad53cf4d6bdb29f450564f31bc4b3ea000163f240000c7897c1f7` |
| Certificate SHA-256 | `6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8` |
| Signer | `CN=Dicta Release`, RSA 3072; APK v2 and v3 verified |

The Manifest resolves the Application and both activities to their existing
fully qualified Kotlin names while using the new installed package ID. No
FileProvider authority or deep link needed changing. Android's generated
receiver permission uses the new application ID. The generated test package is
`dev.randolf.dicta.test`, with instrumentation targeting `dev.randolf.dicta` via
`androidx.test.runner.AndroidJUnitRunner`; its Manifest task completed successfully.
No key, database, credential
or user-data migration is performed between the separate installed packages.

## Build and signing

```sh
./tools/m1/build.sh :app:assembleRelease :app:lintRelease
./tools/m1/build.sh :app:testReleaseUnitTest --tests 'dev.dicta.prototype.profile.*'
python3 scripts/android-signing.py check
python3 scripts/android-signing.py release --auto \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk \
  --out artifacts/release/v1.0.0/Dicta-v1.0.0.apk
```

Set `DICTA_JAVA_HOME` to an installed JDK 17/21 if required. These commands use
the existing Dicta SDK/toolchain; no global tool or device setting is changed.
The signer ran in a fresh `bash --noprofile --norc` process under `setsid`,
without DISPLAY/DBus and with `/dev/null` stdin. A lightweight signing self-check
first reproduced the prior M4 acceptance digest. No `init` or key generation ran.

The existing JKS and password stayed in their original external directory;
their bytes, inode, mtime and 700/600 permissions remained unchanged. The public
certificate matches the T06 fingerprint and repository pin. No Keyring,
Secret Service, sudo or manual input was used for signing.

[Signing results](signing.json) record unchanged input/payload, ZIP CRC,
certificate, versions, alignment and output hash. Independent official
`apksigner verify --verbose --print-certs` and `zipalign -c -P 16 4` both passed.
The signed artifact's packaged license texts and default Profile assets were
compared byte-for-byte with source. Actual DEX class definitions were checked
for test-only classes; OkHttp's logger strings mentioning MockWebServer are not
class definitions and are not evidence of a bundled test dependency.

## Targeted validation

- Release build completed successfully. [Lint](lint.json): 0 errors, 13 existing
  warnings (dependency update suggestions, label/backup, KAPT/KTX suggestions).
- [Release Profile tests](tests.json): 31 passed, 0 failed/errors/skipped. The
  full historical M0–M4 and paid API suites were not rerun for this packaging
  and documentation change.
- [OnePlus 6T / API 35](device.json): signed APK installed, public launcher cold
  start succeeded in 729 ms, installed base APK hash matched the release asset,
  and package flags confirmed a production, non-debuggable app.
- Home missing-key handling, History/Vocabulary navigation, Settings,
  read-only default Profile, TTS engine/voice discovery, public PROCESS_TEXT
  resolution, popup and close were checked on the installed Release APK.
- The new package remains unconfigured. No plaintext API key was injected by
  ADB or clipboard. Live provider queries and full TTS playback were accepted
  in M4; their code is unchanged. Only Android 15/API 35 was device-tested here.
- The old prototype package remains installed. All 25 persistent files,
  including encrypted BYOK and database/profile state, had identical hashes
  before and after the release device checks. No uninstall or data clear ran.
- After all device checks, active instrumentation and install/test processes
  were checked, the temporary UI dump was removed, and `KEYCODE_SLEEP` was sent.
  Power reported `Dozing`, the physical display reported `OFF`, and Keyguard
  reported `showing=true` (the device’s normal screen-off/locked state). No shutdown,
  reboot, or screen-timeout setting change was used.

## Documentation, assets and license

[English README](../../../README.md) and [Simplified Chinese README](../../../README.zh-CN.md)
link to each other and to GitHub Releases. They describe the actual product,
BYOK costs, supported TTS voices, configurable Profiles and current UI language.
Technical build/signing instructions live in separate documentation.
[Public rendering checks](readme-validation.json) confirm GitHub renders all seven
images in each README and that all six public PNG URLs return the unchanged
originals. Browser UI inspection was unavailable; source images were visually
inspected locally and the GitHub rendering API was checked directly.

All six selected Google Drive PNGs were downloaded, inspected individually and
matched to their archived SHA-256 digests, byte sizes and 1080×2340 dimensions.
They are unchanged originals. The [asset manifest](../../assets/readme/manifest.json)
records provenance; both READMEs use repository-relative image paths. The icon
SVG reproduces the existing app vector. The download entry is
`https://github.com/rando1ph/dicta/releases/latest`.

The complete Apache-2.0 license is in the repository and APK. The audit covers
70 resolved runtime artifacts, embedded AAR/JAR licenses and notices, system
fonts, native glyphs, branding and screenshot context. The separate MPL-2.0
Public Suffix List notice and full license are included. See
[third-party notices](../../../THIRD_PARTY_NOTICES.md) and
[exact dependency licenses](dependency-licenses.json).

[Security scan](security.json) covers repository files, historical Git blobs,
archive members and the signed APK for the actual local JKS, password and API
credential. No signing material, actual API key or sensitive credential file
was found. Only the public certificate digest is committed. Generated APKs and
private snapshots remain in ignored local `artifacts/`.

Release assets are the signed APK, [SHA256SUMS](SHA256SUMS),
[English notes](Release-Notes.en.md), and [Chinese notes](Release-Notes.zh-CN.md).
