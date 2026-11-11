# Dicta 1.1.0 release verification

Release: [v1.1.0](https://github.com/rando1ph/dicta/releases/tag/v1.1.0).
Source is the commit identified by the annotated `v1.1.0` tag on `main`.
Verification date: 2026-11-11 (Asia/Shanghai).

## Application and artifact

| Field | Value |
| --- | --- |
| Reviewed main baseline | `a3a5ab7add2eeb74f362a6b5ce6e3664f169c0a8` |
| Application ID | `dev.randolf.dicta` |
| Version | `1.1.0` / versionCode `6` (v1.0.0 used code 5) |
| Android | minSdk 26, target/compile SDK 36 |
| Build | Release, not debuggable; unsigned Gradle APK signed separately |
| Asset | `Dicta-v1.1.0.apk`, 8,518,281 bytes |
| APK SHA-256 | `24417b983e384683fcaf4c1e38eb2424ca8de8a3b72179b5963c808709440258` |
| Certificate SHA-256 | `6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8` |
| Signer | `CN=Dicta Release`, RSA 3072; APK v2/v3 verified |

The only application build change after the approved baseline is changing
`versionName` from `1.1.0-candidate` to `1.1.0`. No accepted feature, Profile,
database, dependency or signing implementation was changed for publication.

## Build and artifact checks

```sh
bash tools/m1/build.sh :app:assembleRelease
python3 scripts/android-signing.py check
python3 scripts/android-signing.py release --auto \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk \
  --out artifacts/release/v1.1.0/Dicta-v1.1.0.apk
```

The production build succeeded. Its required Gradle release packaging checks
ran as part of `assembleRelease`; no separate feature tests were requested.
The established local toolchain and original signing identity were reused.
No key generation or credential copying was performed. The JKS and password
contents, inode, mtime, ownership and permissions were identical before and
after signing; their contents and hashes are not included in this report.

[Signing results](signing.json) confirm the pinned certificate, unchanged
input/application payload, ZIP CRCs, alignment, metadata, size, SHA-256 and
absence of signing material in the APK. The certificate matches the
[v1.0.0 record](../v1.0.0/signing.json).

[Independent checks](verification.json) used official `apksigner verify
--verbose --print-certs`, `zipalign -c -P 16 4`, `aapt dump badging`, ZIP CRCs
and SHA-256 against the signed final APK. The package is non-debuggable.

## Accepted test scope

T07's feature testing, final etymology UI correction and user review were
accepted before T08 began. Historical results remain in the
[T07 report](../../t07/REPORT.md), including its final two targeted tests and
30-screen screenshot index. These are prior acceptance evidence, not new T08
test results.

The user's final release decision explicitly removed repeated emulator
regression, retained-data update installation and physical-device testing.
None was performed during T08; no emulator, phone or user data was operated on.
The user will try the public build on their Ace 6T after publication. T08 makes
no new live-provider, cross-version device or speech-quality claim.

## Documentation and distribution

The bilingual READMEs describe v1.1.0's detail navigation, provider/model
settings, key ownership, daily-word generation and update path. Existing
unmodified README screenshots are explicitly identified as v1.0.0 captures.
The development guide now reflects the approved final T08 validation scope.

Release assets are the signed APK, [SHA256SUMS](SHA256SUMS),
[English notes](Release-Notes.en.md) and [Chinese notes](Release-Notes.zh-CN.md).
The tag identifies the release source; subsequent documentation-only commits
may record public download verification without changing that source.
