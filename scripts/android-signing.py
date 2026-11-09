#!/usr/bin/env python3
"""Noninteractive, local-file Android signing; never builds or installs an APK.

Adapted from Oraculum scripts/android-signing.py at 3f98a0f506f7c372ed1743f1201ad23136c8c639:
private-path checks, sanitized tool environment, private candidate, verification
before no-clobber publication. No Oraculum identity/configuration is imported.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
KEY_DIR = Path.home() / ".local/share/dicta-signing"
ALIAS = "dicta-release"
CERT_FILE = ROOT / "scripts/android-signing-cert.sha256"
BUILD_TOOLS_VERSION = "35.0.0"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def private_path(path, directory=False):
    require(path.is_absolute() and path == path.resolve(), "Signing paths must be absolute without symlinks")
    require(not path.is_relative_to(ROOT), "Signing material must remain outside the repository")
    info = path.lstat()
    require(info.st_uid == os.getuid(), "Signing material must belong to the current user")
    require(stat.S_IMODE(info.st_mode) == (0o700 if directory else 0o600), "Signing permissions must be 0700/0600")
    require(stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode), "Unexpected signing path type")
    require(directory or info.st_nlink == 1, "Signing files must not be hard-linked")


def tool_environment():
    environment = os.environ.copy()
    for name in list(environment):
        if name.startswith("DICTA_RELEASE_") or name in {
            "BASH_ENV", "ENV", "SHELLOPTS", "BASHOPTS", "PS4", "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "JAVA_OPTS", "GRADLE_OPTS",
        }:
            environment.pop(name, None)
    java_home = os.environ.get("DICTA_JAVA_HOME", os.environ.get("JAVA_HOME"))
    if java_home:
        environment["JAVA_HOME"] = java_home
        environment["PATH"] = str(Path(java_home) / "bin") + os.pathsep + environment.get("PATH", "/usr/bin:/bin")
    environment["LC_ALL"] = "C.UTF-8"
    return environment


def tool(name):
    if name in {"java", "keytool"}:
        result = shutil.which(name, path=tool_environment().get("PATH"))
    else:
        sdk = Path(os.environ.get("DICTA_ANDROID_HOME", os.environ.get(
            "ANDROID_HOME", str(Path.home() / ".local/share/dicta-tools/android-sdk"))))
        result = sdk / "build-tools" / BUILD_TOOLS_VERSION / name
    require(result and os.access(result, os.X_OK), f"Missing tool: {name}; see docs/SIGNING.md")
    return str(result)


def run_tool(command):
    # Every child has closed/noninteractive input. Diagnostics may contain
    # credentials, so neither captured data nor exception objects are echoed.
    try:
        return subprocess.run(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL, env=tool_environment(),
                              timeout=120, check=True).stdout
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        raise RuntimeError(f"{Path(command[0]).name} failed; diagnostics suppressed to protect credentials") from None


def material():
    private_path(KEY_DIR, directory=True)
    store, password = KEY_DIR / "dicta-release.jks", KEY_DIR / "keystore.pass"
    for path in (store, password):
        private_path(path)
    require(store.read_bytes()[:4] == bytes.fromhex("feedfeed"), "Expected an actual JKS store")
    secret = password.read_bytes()
    require(secret.endswith(b"\n") and len(secret) > 1 and not any(c in secret[:-1] for c in (b"\n", b"\r", b"\0")),
            "Password file must contain one nonempty line ending in LF")
    return store, password


def key_fingerprint():
    store, password = material()
    # JKS load, private-key proof, validity, dedicated alias, RSA size; only a
    # public certificate digest is returned. Password is read inside the JVM.
    result = run_tool([tool("java"), str(ROOT / "scripts/SigningKeyCheck.java"),
                       str(store), str(password), ALIAS]).decode("ascii").strip()
    require(re.fullmatch(r"[0-9a-f]{64}", result), "Invalid key certificate fingerprint")
    return result


def expected_fingerprint():
    value = CERT_FILE.read_text().strip()
    require(re.fullmatch(r"[0-9a-f]{64}", value), "Invalid pinned certificate fingerprint")
    return value


def publish(source, target):
    # Both are on the same filesystem. Atomic publication refuses all existing
    # destinations, including dangling symlinks, even under concurrent callers.
    with source.open("rb") as stream:
        os.fsync(stream.fileno())
    os.link(source, target)


def initialize():
    require(KEY_DIR == KEY_DIR.resolve() and not KEY_DIR.is_relative_to(ROOT), "Invalid local key directory")
    KEY_DIR.mkdir(mode=0o700, parents=True, exist_ok=True)
    private_path(KEY_DIR, directory=True)
    lock = KEY_DIR / ".init.lock"
    descriptor = os.open(lock, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "rb") as stream:
        private_path(lock)
        fcntl.flock(stream, fcntl.LOCK_EX)
        store, password = KEY_DIR / "dicta-release.jks", KEY_DIR / "keystore.pass"
        exists = [os.path.lexists(p) for p in (store, password)]
        require(all(exists) or not any(exists), "Incomplete signing identity preserved; inspect locally, never regenerate over it")
        reused = all(exists)
        if not reused:
            require(not os.path.lexists(CERT_FILE), "Pinned identity exists; missing local key cannot be regenerated")
            with tempfile.TemporaryDirectory(prefix=".init-", dir=KEY_DIR) as temporary:
                staged_store = Path(temporary) / store.name
                staged_password = Path(temporary) / password.name
                with staged_password.open("xb") as output:
                    os.chmod(staged_password, 0o600)
                    output.write(secrets.token_hex(48).encode("ascii") + b"\n")
                    output.flush()
                    os.fsync(output.fileno())
                run_tool([tool("keytool"), "-genkeypair", "-noprompt", "-storetype", "JKS",
                          "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "3072",
                          "-sigalg", "SHA256withRSA", "-validity", "10000", "-dname", "CN=Dicta Release",
                          "-keystore", str(staged_store), "-storepass:file", str(staged_password),
                          "-keypass:file", str(staged_password)])
                staged_store.chmod(0o600)
                publish(staged_password, password)
                publish(staged_store, store)
        fingerprint = key_fingerprint()
        if os.path.lexists(CERT_FILE):
            require(expected_fingerprint() == fingerprint, "Existing key does not match the pinned certificate; all files preserved")
        else:
            with CERT_FILE.open("x") as output:
                output.write(fingerprint + "\n")
        return {"identity": "reused" if reused else "created", "jks": str(store),
                "alias": ALIAS, "certificateSha256": fingerprint}


def signature_entry(name):
    return bool(re.fullmatch(r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))", name, re.IGNORECASE))


def apk_payload(path):
    store, password = material()
    forbidden = (store.read_bytes(), password.read_bytes().removesuffix(b"\n"))
    payload = {}
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, "APK ZIP CRC integrity failure")
        require(len(archive.namelist()) == len(set(archive.namelist())), "Duplicate APK entries rejected")
        require({"AndroidManifest.xml", "classes.dex", "resources.arsc"} <= set(archive.namelist()), "APK is missing required application entries")
        for entry in archive.infolist():
            name = entry.filename
            require(not name.lower().endswith((".jks", ".keystore", ".pass", ".key", ".p12")), "Sensitive filename in APK")
            data = archive.read(entry)
            require(not any(value in data for value in forbidden), "Signing material found in APK")
            if not signature_entry(name):
                payload[name] = hashlib.sha256(data).hexdigest()
    return payload


def apk_metadata(path):
    badging = run_tool([tool("aapt"), "dump", "badging", str(path)]).decode("utf-8")
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.MULTILINE)
    require(match, "Cannot read APK package/version metadata")
    return dict(zip(("applicationId", "versionCode", "versionName"), match.groups()))


def verify_apk(path, expected):
    output = run_tool([tool("apksigner"), "verify", "--verbose", "--print-certs", str(path)]).decode("utf-8")
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", output, re.MULTILINE)
    require([value.lower() for value in fingerprints] == [expected], "APK signer certificate does not match the pinned Dicta identity")
    run_tool([tool("zipalign"), "-c", "-P", "16", "4", str(path)])
    return {"certificateSha256": expected, **apk_metadata(path), "payloadEntries": len(apk_payload(path)),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "bytes": path.stat().st_size}


def release(apk, target):
    store, password = material()
    expected = expected_fingerprint()
    require(key_fingerprint() == expected, "JKS does not match pinned certificate; no signing performed")
    source = apk.resolve(strict=True)
    require(source.is_file(), "Input APK must be a regular file")
    require(not os.path.lexists(target), "Existing output preserved; choose a new --out path")
    target = target.absolute()
    require(source != target.resolve(), "Input and output must be different paths")
    require(target.suffix == ".apk", "Output must have an .apk extension")
    require(not target.resolve().is_relative_to(KEY_DIR), "APK output cannot be inside the signing identity directory")
    target.parent.mkdir(parents=True, exist_ok=True)
    original_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory(prefix=".sign-", dir=target.parent) as temporary:
        copied, aligned, candidate = (Path(temporary) / name for name in ("input.apk", "aligned.apk", "signed.apk"))
        shutil.copyfile(source, copied)
        require(hashlib.sha256(copied.read_bytes()).hexdigest() == original_hash, "Input changed while copying")
        payload = apk_payload(copied)
        metadata = apk_metadata(copied)
        run_tool([tool("zipalign"), "-P", "16", "4", str(copied), str(aligned)])
        # JKS and key share one password. apksigner first tries the store
        # password for the key; repeating file: would consume a nonexistent
        # second line. The private-key proof above enforces this invariant.
        run_tool([tool("apksigner"), "sign", "--ks", str(store), "--ks-key-alias", ALIAS, "--ks-type", "JKS",
                  "--ks-pass", "file:" + str(password),
                  "--v4-signing-enabled", "false", "--out", str(candidate), str(aligned)])
        result = verify_apk(candidate, expected)
        require(apk_payload(candidate) == payload, "APK application content changed during signing")
        require(all(result[k] == v for k, v in metadata.items()), "APK metadata changed during signing")
        require(hashlib.sha256(source.read_bytes()).hexdigest() == original_hash, "Original input changed; no output published")
        candidate.chmod(0o600)
        publish(candidate, target)
    return {"output": str(target), "inputSha256": original_hash, **result,
            "signatureVerified": True, "zipCrcVerified": True, "alignmentVerified": True,
            "payloadUnchanged": True, "inputUnchanged": True, "signingMaterialAbsent": True}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["init", "check", "release"])
    parser.add_argument("--auto", action="store_true", help="Required acknowledgement of unattended local-file signing for release")
    parser.add_argument("--apk", type=Path, help="Existing reviewed APK; no build is performed")
    parser.add_argument("--out", type=Path, help="New output APK path, never overwritten")
    args = parser.parse_args(argv)
    require(not args.auto or args.command == "release", "--auto is only valid for release")
    require(args.command == "release" or (args.apk is None and args.out is None), "--apk/--out are only valid for release")
    os.umask(0o077)
    if args.command == "init":
        result = initialize()
    elif args.command == "check":
        fingerprint = key_fingerprint()
        require(fingerprint == expected_fingerprint(), "JKS certificate pin mismatch")
        result = {"certificateSha256": fingerprint, "privateKeyProofVerified": True, "permissionsVerified": True}
    else:
        require(args.auto and args.apk is not None and args.out is not None, "Use release --auto --apk INPUT.apk --out NEW-OUTPUT.apk")
        result = release(args.apk, args.out)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, zipfile.BadZipFile, UnicodeError, KeyboardInterrupt) as error:
        print(str(error) if isinstance(error, RuntimeError) else "Signing stopped; diagnostics suppressed; existing key and outputs preserved", file=sys.stderr)
        sys.exit(1)
