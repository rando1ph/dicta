# Dicta 1.1.0

**Meet a word. Keep your place.** This update improves word navigation, provider setup and everyday reading.

- Open a dedicated word detail page from Search, recent lookups, History or Vocabulary. Back returns to your previous page, input, filters and scroll position. Opening full details from a selection popup keeps the return path to your reading app.
- Choose DeepSeek, OpenAI or a compatible HTTPS Chat Completions endpoint. Fetch and search model lists, enter a Model ID manually, and choose JSON mode or prompt-only JSON output with client-side schema validation. A Profile's nonempty model setting overrides the global model.
- Keep saved API keys encrypted with Android Keystore. Changing the service host or port requires a new key; editing the model or API path on the same service can retain the saved key. Test lookups use the saved connection and current Profile without adding a history item.
- Automatically fetch the daily word when no matching cache exists, reuse the daily cache, and keep manual refresh and retry. New generations use your configured provider and may incur usage charges.
- Improve narrow-screen and large-font layouts, provider controls and popup actions.
- Show etymology text directly, without a separate card heading or expand/collapse control. The default certainty field and old default disclaimer labels are hidden when displaying existing snapshots; saved entry data and editable Profiles remain intact.

## Install or update

Download `Dicta-v1.1.0.apk` and install it on **Android 8.0 or later**. Package: **dev.randolf.dicta**; version: **1.1.0**; versionCode: **6**. It uses the same release certificate as v1.0.0 and can be installed as an update. Keep the existing app installed to retain its data and settings. Debug candidate builds use a different certificate.

Configure your own HTTPS endpoint, model and API key in Settings. Endpoint and model compatibility varies; a returned model list does not guarantee that every model supports dictionary lookups. Native Anthropic, Azure-specific authentication and the Responses API are outside this release. No API key or AI credit is included. The interface remains primarily Simplified Chinese, with English–Chinese entries in the default Profile; TTS voices depend on the installed Android speech engine.

## Integrity and validation

Signed with the existing **Dicta Release** identity (RSA 3072; APK v2/v3).

Certificate SHA-256:

```text
6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8
```

APK SHA-256 (`Dicta-v1.1.0.apk`, 8,518,281 bytes):

```text
24417b983e384683fcaf4c1e38eb2424ca8de8a3b72179b5963c808709440258
```

Download `SHA256SUMS` into the same directory and run `sha256sum -c SHA256SUMS`.

T07 completed feature tests and user review before this release. T08 checked the production build, package/version, original certificate, APK signature/alignment, ZIP integrity, SHA-256 and public download assets. Emulator regression, update installation and physical-device tests were not repeated for publication. Provider protocols were tested with local TLS mocks in T07; no new live-provider or speech-quality validation is claimed.

Source: [v1.1.0](https://github.com/rando1ph/dicta/tree/v1.1.0). [Verification record](https://github.com/rando1ph/dicta/blob/main/docs/releases/v1.1.0/REPORT.md) · [English README](https://github.com/rando1ph/dicta/blob/v1.1.0/README.md) · [简体中文说明](https://github.com/rando1ph/dicta/blob/v1.1.0/README.zh-CN.md). Original code is licensed under Apache-2.0; third-party notices and license texts remain included in the APK and repository.
