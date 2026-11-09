[English](README.md) · [简体中文](README.zh-CN.md)

<p align="center">
  <img src="docs/assets/readme/icon.svg" width="88" alt="Dicta app icon">
</p>

<h1 align="center">Dicta</h1>
<p align="center"><strong>Meet a word. Keep your place.</strong></p>
<p align="center">An AI dictionary for Android, built around the way you read.</p>
<p align="center">
  <a href="https://github.com/rando1ph/dicta/releases/latest">Download for Android</a> ·
  <a href="#make-it-your-dictionary">Bring your own API key</a> ·
  <a href="LICENSE">Apache-2.0</a>
</p>

<p align="center">
  <img src="docs/assets/readme/hero-guardian-popup.png" width="360" alt="Dicta explaining rainforest in a native popup over The Guardian in Chrome">
  <img src="docs/assets/readme/hero-bbc-popup.png" width="360" alt="Dicta explaining fossilised in a native popup over BBC News in Chrome">
</p>

## A closer look, without losing the thread

An unfamiliar word should be a small detour. Select it in Chrome or another app that supports Android's text selection actions, choose **Dicta**, and explore a clear, readable entry in a native popup. Listen, save the word, then return to your reading.

Prefer to start with a question? Open Dicta and look up a word from Home. The default profile brings together English and Chinese definitions, examples, pronunciation, and word origins in one place.

## From a quick lookup to a lasting vocabulary

- **Look up words where you find them.** A focused popup keeps your reading visible. Open the full entry whenever you want more room.
- **Pick up where you left off.** Search your History and reopen saved entries locally, without another AI request.
- **Keep the words that matter.** Build your own Vocabulary with a tap. Removing a history item leaves a bookmarked entry available.
- **Find a little inspiration.** Discover a Word of the Day on Home, or ask for another using your configured provider.
- **Hear the difference.** Play UK or US pronunciation with Android text-to-speech (TTS), wherever your installed speech engine provides those voices.
- **Make yourself at home.** Choose a light or dark theme and your preferred accent colour.

<table>
  <tr>
    <th>Home &amp; Word of the Day</th>
    <th>History</th>
    <th>Vocabulary</th>
  </tr>
  <tr>
    <td><img src="docs/assets/readme/home.png" width="240" alt="Dicta Home with a daily word and UK and US pronunciation"></td>
    <td><img src="docs/assets/readme/history.png" width="240" alt="Searchable history of words looked up in Dicta"></td>
    <td><img src="docs/assets/readme/vocabulary.png" width="240" alt="Dicta Vocabulary with bookmarked words"></td>
  </tr>
</table>

## Make it your dictionary

**Your provider. Your API key.** Dicta uses BYOK: you choose an HTTPS API endpoint and model, and supply your own key in Settings. DeepSeek is the tested provider; other endpoints must support the same chat completions request and JSON output format, and compatibility varies. AI service access and any usage charges come from your provider. Dicta does not include an API key or AI credit.

Your key is encrypted on the device using Android Keystore. New lookups send the selected or typed word and your profile instructions to your chosen provider; saved entries can be reopened locally. Pronunciation uses your Android speech engine, separately from the text model.

**Start with a complete profile. Shape it as you learn.** The built-in Profile is ready once your API is configured. For more control, copy it and edit the Prompt, Schema, and Template to decide what an entry explains and how it appears. Validate and preview your changes, switch profiles, or import and export them. Previously saved entries retain their original presentation.

<p align="center">
  <img src="docs/assets/readme/profile-template.png" width="320" alt="Dicta Profile editor with template editing, validation, preview, and import and export controls">
</p>

## Get Dicta

1. Download **Dicta-v1.0.0.apk** from [GitHub Releases](https://github.com/rando1ph/dicta/releases/latest) and install it. Android may ask you to allow installation from the app you used to download it.
2. Open **Settings** and save your provider's endpoint, model, and API key.
3. Search from Home, or select a word in a supported app and choose **Dicta** from the selection menu (sometimes under **More**).

Requires **Android 8.0 or later**. Version **1.0.0** is the first public release; tested on a OnePlus 6T running Android 15. The current interface is primarily Simplified Chinese, with bilingual English–Chinese entries in the default profile. Selection actions and available TTS voices depend on the host app and device.

The public app uses `dev.randolf.dicta`. Earlier prototype builds install separately; their data and API settings stay in the prototype app.

## Open source

Copyright © 2026 **rando1ph**. Dicta's original code is licensed under the [Apache License 2.0](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md) for dependencies and asset attribution, and [development notes](docs/DEVELOPMENT.md) to build from source.

Screenshots are unmodified captures from a real device. BBC and The Guardian names, marks, articles, and photographs belong to their respective owners and appear only as browsing context; no affiliation or endorsement is implied.
