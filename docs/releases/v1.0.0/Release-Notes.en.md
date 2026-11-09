# Dicta 1.0.0

**Meet a word. Keep your place.** Dicta's first public Android release brings AI dictionary lookups into your reading, with your own provider and API key.

- Select a word in a supported app and open a native Dicta popup; listen, bookmark it, or open the full entry.
- Look up words from Home, explore a daily word, and revisit saved entries without another AI request.
- Search and manage History and Vocabulary independently.
- Hear UK and US pronunciation with Android TTS when the installed engine supports those voices.
- Copy, edit, preview, validate, switch, import and export Profiles to shape the definitions and their presentation.
- Choose light or dark mode and an accent colour.

## Install

Download `Dicta-v1.0.0.apk` from this release and install it on Android 8.0 or later. Configure your HTTPS endpoint, model and API key in Settings. DeepSeek is the tested provider; compatibility with other endpoints varies. AI service fees are charged by your provider; no key or AI credit is included.

The interface is primarily Simplified Chinese, with English–Chinese entries in the default Profile. Device validation used a OnePlus 6T running Android 15; other Android versions have not been device-tested for this release.

The public package is **dev.randolf.dicta**, version **1.0.0**, versionCode **5**. Earlier `dev.dicta.prototype` builds remain separate, retaining their data and BYOK settings. The new app starts with its own settings.

## Integrity and source

Signed with the established **Dicta Release** identity (RSA 3072; APK v2/v3).

Certificate SHA-256:

```text
6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8
```

APK SHA-256 (`Dicta-v1.0.0.apk`):

```text
557c8c3c055ad53cf4d6bdb29f450564f31bc4b3ea000163f240000c7897c1f7
```

`SHA256SUMS` is attached. After downloading both files into the same directory, verify with `sha256sum -c SHA256SUMS`.

Source: [v1.0.0](https://github.com/rando1ph/dicta/tree/v1.0.0). Original code: Apache-2.0; third-party notices and license texts are included in the APK and repository. The [English README](https://github.com/rando1ph/dicta/blob/v1.0.0/README.md) and [简体中文说明](https://github.com/rando1ph/dicta/blob/v1.0.0/README.zh-CN.md) include the reviewed device screenshots and setup guide.
