# Dicta local automatic APK signing

The signing system signs an existing reviewed APK using the established Dicta
Release identity. Building, device verification and GitHub publication are
separate steps. Version 1.0.0 uses this identity; see the
[release verification record](releases/v1.0.0/REPORT.md). The earlier signing-only
acceptance APK was a copy of the M4 debug build, not a formal release candidate.

## Identity and credentials

- JKS: `~/.local/share/dicta-signing/dicta-release.jks`
- Alias: `dicta-release`
- Password: `~/.local/share/dicta-signing/keystore.pass` (one LF-terminated line).
- Directory mode: `700`; JKS/password modes: `600`, owned by the current user.
- Dedicated RSA 3072-bit key, SHA256withRSA certificate, 10,000-day validity.
- The generated password has 384 bits of cryptographic randomness. The JKS and
  private key use the same password.
- Public certificate SHA-256 (also pinned in
  `scripts/android-signing-cert.sha256`):

  `6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8`

Credentials stay outside the repository. Do not copy them into Git, logs,
Notion, Google Drive, APK assets, or any other cloud storage. The certificate
fingerprint is public and may be recorded. The user accepts that programs
running as this Linux user can read the plaintext credentials, and that loss
or compromise of this computer can lose or compromise the signing identity.
No independent recovery backup is established by this preparation.

## Commands

From the Dicta repository root:

```sh
python3 scripts/android-signing.py check
python3 scripts/android-signing.py release --auto \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk \
  --out artifacts/release/v1.0.0/Dicta-v1.0.0.apk
```

Build the unsigned release with `./tools/m1/build.sh :app:assembleRelease` first.
Use a new output path for every release; the signer refuses to overwrite any
existing output. For a complete build/verify/publish procedure, see
[development notes](DEVELOPMENT.md) and the versioned release record.

`release` requires `--auto`, `--apk`, and `--out`. It copies the input into a
private temporary directory, checks it, aligns it, signs it, verifies it, and
atomically publishes a new local output. Existing outputs (including symlinks)
are refused and preserved. Original APKs and credentials are never overwritten.
No source APK is rebuilt. A previously signed test APK is re-signed with the
Dicta certificate; its prior signature metadata is replaced by apksigner.

`init` is noninteractive and has already been run on this computer:

```sh
python3 scripts/android-signing.py init
```

It reuses a valid existing identity, verifies the private key and certificate,
and preserves the key and password. It refuses incomplete identities or a
missing key when a certificate pin already exists. Do not delete or replace
identity files or change the public pin to work around an error.

## Tools and zero interaction

Requires Python 3.9+, Linux, JDK 17+ (`java`, `keytool`), and official Android SDK
Build Tools 35.0.0 (`apksigner`, `zipalign`, `aapt`). The local SDK default is
`~/.local/share/dicta-tools/android-sdk`; `DICTA_ANDROID_HOME` or `ANDROID_HOME`
can select another SDK. `DICTA_JAVA_HOME` or `JAVA_HOME` can select a JDK.
No dependency installation or system configuration change is needed here.

The signer reads the password with `--ks-pass file:<local path>`. The key proof
checks that the private key unlocks with the same password, so no second
`--key-pass` is supplied. Repeating the same one-line file for both options
would consume its nonexistent second line. Every child process receives
`/dev/null` as stdin, a bounded timeout, and sanitized shell/JVM environment
variables. Tool diagnostics are suppressed; passwords are never passed in
argv, environment variables, shell history, or output. There is no Secret
Service/GNOME Keyring dependency, sudo, GUI prompt, login-password prompt,
keystore prompt, or confirmation step.

The script checks ownership, exact permissions, JKS format, symlinks, hard
links, dedicated alias, RSA size, certificate validity, private-key proof and
pinned certificate. Before publishing it verifies official apksigner results,
zipalign alignment, ZIP CRCs, package/version metadata and the hashes of all
application entries (excluding signature metadata). It rejects APK entries
containing the local signing credentials or sensitive filenames.

Official references: [apksigner](https://developer.android.com/tools/apksigner)
and [zipalign](https://developer.android.com/tools/zipalign). Alignment occurs
before signing; the signed APK is not modified afterward.

## Verification

Key-free regression tests:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 scripts/android-signing-test.py
```

Real integration test using the current local identity and independent copies
of an existing APK (choose a fresh work directory for every run):

```sh
python3 scripts/android-signing-integration.py \
  --apk artifacts/m4/Dicta-M4-debug.apk \
  --work-dir artifacts/signing/acceptance-NEW-RUN
```

Actual acceptance on 2026-11-10: **14 regression tests passed**, plus two real
signing runs in new shells without startup files, controlling terminals,
DISPLAY or DBus variables. One used `/dev/null` stdin; the other closed fd 0.
Both returned 0 and passed official signature verification, the certificate
pin, ZIP CRCs, 16 KiB library/4-byte file alignment and all 137 application-entry
hash comparisons. The original APK, signing identity and existing output were
preserved. An independent apksigner verification passed; a deliberately
modified APK failed verification.

The existing test package remains `dev.dicta.prototype`, version
`0.4.0-m4` / versionCode `4`. Both signed test copies have SHA-256
`790f8e0a664e4ced165017205307134b2d4d685032525cc048942f1b565ced2a`.
See [public machine-readable evidence](signing/verification.json) and the
[credential isolation audit](signing/security.json).
No device installation was needed for this signing-only acceptance.

## Porting provenance

Adapted the generic checks and candidate publication pattern from Oraculum's
`scripts/android-signing.py`, `scripts/android-signing-test.py`, and
`scripts/SigningKeyCheck.java`, inspected at commit
`3f98a0f506f7c372ed1743f1201ad23136c8c639`. Its 21 key-free tests passed before
porting. Ported scope: private path checks, environment sanitization, safe tool
failure handling, signing a separate copy, certificate/private-key checks,
verification before no-clobber publication, and corresponding failure tests.
Dicta uses its own paths, alias, JKS, public certificate pin, APK metadata
checks, and local password-file implementation. No Oraculum identity,
credentials, project configuration, build metadata or Secret Service code is
used. Oraculum was read only.
