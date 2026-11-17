# Dicta 1.2.0 release verification

Verification date: 2026-11-18 (Asia/Shanghai). T11 is explicitly authorized;
T09 compatibility implementation and T10 human review are accepted.
Release source is identified by annotated tag `v1.2.0` on `main`.

## Identity and scope

| Field | Value |
| --- | --- |
| Reviewed main baseline | `b419bfc89b434e86b7837dcc138968832c14ec77` |
| Accepted implementation | `6127c1fcdd25119b1b6aa5142b7a3494b69e4862` |
| Package | `dev.randolf.dicta` |
| Version | `1.2.0` / versionCode `7` |
| Android | minSdk 26; compile/target SDK 36 |
| Production build | Release; non-debuggable |
| APK | `Dicta-v1.2.0.apk`, 8,518,281 bytes |
| APK SHA-256 | `b159e1fd0d9ebf7c9469e506679cbcb298bd59b1d3046ea3616f6c5aea2ea34b` |
| Certificate SHA-256 | `6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8` |
| Debug | `dev.randolf.dicta.debug`, `1.2.0-debug`, separate debug certificate |

Only the version/code change affects production source after the reviewed
baseline. The release adds documentation, evidence and an isolated test-only
upgrade fixture. Accepted Popup code, database schema, Profiles, dependencies
and signing implementation are unchanged. Work remained on `main`, without
branches, worktrees, rebase or force push.

## Build, tests and signing

```sh
bash tools/m1/build.sh --no-daemon --no-parallel --max-workers=1 \
  :app:assembleRelease :app:testReleaseUnitTest
bash tools/m1/build.sh --no-daemon --no-parallel --max-workers=1 :app:lintRelease
PYTHONDONTWRITEBYTECODE=1 python3 scripts/android-signing-test.py
python3 scripts/android-signing.py check
```

Release build and **81/81** unit tests passed; Release lint **0 errors / 13
existing warnings**; signing regressions **14/14**. The initial combined
compile/test/lint invocation failed because lint referenced an obsolete
`DeepSeekProviderTest.java` KAPT stub. Fresh sequential compile/tests followed
by separate non-daemon lint passed without changing app source, dependencies
or suppressing lint. Local full logs are retained under
`artifacts/release/v1.2.0/`.

The existing `scripts/android-signing.py` release implementation performed
automatic local signing. Original JKS/password contents, inode, mtime, owner,
group and permissions were compared in memory before/after production signing
and matched. No identity was generated/replaced, pin changed or credential
copied. The existing script also signed the local-only instrumentation APK
with `release --auto`; this APK and its fake data are excluded from publication.

[Signing evidence](signing.json) and [independent artifact checks](verification.json)
confirm the unchanged source payload, certificate pin, package/code/version,
APK v2/v3 signatures, `zipalign -c -P 16 4`, ZIP CRC and SHA-256. A separate
Debug build confirms isolated package/version/debuggable identity. The final
production manifest contains the reviewed empty Popup task affinity and
`singleTop` mode. Sensitive-material checks are in [security.json](security.json).

## Actual official-APK upgrade

The public v1.1.0 APK was anonymously downloaded with HTTP 200 and verified
against its historical SHA-256
`24417b983e384683fcaf4c1e38eb2424ca8de8a3b72179b5963c808709440258`
and original certificate before installation. Both APKs are non-debuggable,
original-certificate production artifacts; no debug surrogate was used.

A dedicated fresh AOSP API36 emulator (`emulator-5582`) used separate AVD
configuration and userdata under ignored artifacts. Existing AVD userdata
was retained. The initial read-only emulator probe was stopped before the
test. No commands operated on the connected physical phone.

The [fixture](../../../tools/t11/README.md) seeded only synthetic data into
official v1.1.0, then installed the final signed v1.2.0 with `adb install -r`.
One seed test and verification tests immediately after update and after an
actual cold launch all passed. Installed APK bytes equal the final asset.

[Upgrade evidence](upgrade.json) verifies:

- Unchanged UID, first-install time and BYOK ciphertext SHA-256; actual
  Android Keystore decryption preserves endpoint, model, fake key, protocol
  and output mode.
- Identical logical snapshots of all six Room tables: entries, history,
  bookmarks, daily, profiles and preferences; unchanged schema version 2.
- History and independent Vocabulary references remain readable, saved entry
  Schema/Template still render, custom Profile/active revision and daily data
  remain available.
- Retained storage still accepts writes; deleting only the newly inserted
  probe history row preserves the original snapshot and bookmarks.

This establishes the tested update path with synthetic data. It does not
claim byte equality of private data on a user's real phone. User phone apps,
BYOK, books and other personal data were not accessed, replaced, uninstalled,
cleared or downgraded. No real provider request or API charge was needed.
The dedicated emulator was stopped with its test data retained locally.

## Accepted compatibility and limits

[T09](../../t09/REPORT.md) supplies the previously accepted OnePlus 6T /
LineageOS 22.2 / Android 15 Readest and Chrome evidence, 13 targeted native
tests and private screenshots. T10 human review passed before T11. Historical
evidence remains identified as historical; its private book screenshots are
not published as release assets.

T11 does not repeat that compatibility matrix or add ColorOS17/Ace6T testing.
Standard PROCESS_TEXT / text/plain is the integration contract. Other reader
protocols, OEM behavior, live-provider semantics and speech quality have no
new acceptance claim. Android 8 is the build minimum, not proof of every API.

## Distribution and recovery

The four public assets are the signed APK, [SHA256SUMS](SHA256SUMS),
[English notes](Release-Notes.en.md) and [Chinese notes](Release-Notes.zh-CN.md).
The bilingual READMEs use the dynamic GitHub `releases/latest` entry; the
website's existing latest-release mechanism requires no versioned URL change.
Old v1.0.0/v1.1.0 releases and tags are retained. Post-publication evidence may
be added on `main` without changing the release tag or signed APK.

Before publication, a failed check stops publication and preserves outputs
and credentials. An interrupted upload must be reconciled against GitHub
release/asset metadata before retrying missing work. For an installation
failure, keep the current app/data and retry only the verified same-certificate
APK after resolving the cause. Do not uninstall, clear data or force a
downgrade. For a confirmed post-release regression, retain old artifacts and
user data, document the affected behavior, and use an authorized forward
fix with a higher versionCode. An older release is not an automatic safe
data-preserving downgrade path.
