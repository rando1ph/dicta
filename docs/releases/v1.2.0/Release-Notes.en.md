# Dicta 1.2.0

**Meet a word. Keep your place.** This update improves selection popups across
reading apps while preserving the existing Chrome experience.

- Readest's default dictionary and explicitly selected Dicta paths keep the
  reading page behind the popup, including when Dicta's main screen already
  exists in the background.
- Repeated selections show the latest word when an earlier popup or full
  entry remains in the background.
- Open the full entry from the popup using the same saved result, without an
  additional lookup. Back and Close return to the reading app; selected text
  is not replaced.
- Retain BYOK settings, History, Vocabulary, daily words, Profiles and TTS.
  Debug builds remain separately installed as Dicta Debug.

## Install or update

Download `Dicta-v1.2.0.apk` for **Android 8.0 or later**. Package:
`dev.randolf.dicta`; version **1.2.0**; versionCode **7**. It uses the same
official signing certificate as v1.0.0 and v1.1.0. Install over the existing
official app to retain your settings and saved entries; keep that app's data.
Your own provider endpoint, model and API key are still required.

## Validation and compatibility

T09's Readest and Chrome checks on **OnePlus 6T / LineageOS 22.2 / Android 15**
passed T10 human review before this release. The supported integration is
Android's standard `ACTION_PROCESS_TEXT` with `text/plain`. Other readers,
private dictionary protocols and OEM behavior can differ; ColorOS 17 was not
tested. Mock dictionary results in the private review evidence establish the
interaction behavior, not definition quality.

The release build, **81 Release unit tests**, **14 signing regression tests**
and Release lint passed (**0 errors, 13 existing warnings**). A focused update
from the public v1.1.0 APK to the final signed v1.2.0 APK passed on a fresh
isolated AOSP API36 emulator, including cold launch, unchanged encrypted BYOK,
Keystore decryption, History, Vocabulary, custom Profile and daily data. It
used synthetic data and made no real provider request. User phones and their
data were not operated on during T11. No broad compatibility matrix was
repeated for this release.

## Integrity

Existing **Dicta Release** identity, RSA 3072, verified APK v2/v3 signatures.
APK size: **8,518,281 bytes**. Certificate SHA-256:

```text
6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8
```

APK SHA-256:

```text
b159e1fd0d9ebf7c9469e506679cbcb298bd59b1d3046ea3616f6c5aea2ea34b
```

Download `SHA256SUMS` beside the APK and run `sha256sum -c SHA256SUMS`.

[Source](https://github.com/rando1ph/dicta/tree/v1.2.0) ·
[Verification record](https://github.com/rando1ph/dicta/blob/main/docs/releases/v1.2.0/REPORT.md) ·
[简体中文](https://github.com/rando1ph/dicta/blob/v1.2.0/docs/releases/v1.2.0/Release-Notes.zh-CN.md).
Original code remains Apache-2.0, with third-party notices included.
