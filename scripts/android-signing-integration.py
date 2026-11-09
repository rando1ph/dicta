#!/usr/bin/env python3
"""Real signer acceptance on copies of an existing APK. Does not build/install.

Uses the existing local Dicta identity; never creates/replaces a signing key.
Outputs public evidence only. Run from the repository root; see docs/SIGNING.md.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/android-signing.py"
KEY_DIR = Path.home() / ".local/share/dicta-signing"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True, help="New directory for test copies and signed APKs")
    args = parser.parse_args()
    source = args.apk.resolve(strict=True)
    work = args.work_dir.absolute()
    require(not work.resolve().is_relative_to(KEY_DIR), "Test copies must stay outside the identity directory")
    os.umask(0o077)
    work.mkdir(mode=0o700, parents=True, exist_ok=False)
    keys = [KEY_DIR / "dicta-release.jks", KEY_DIR / "keystore.pass"]
    original = digest(source)
    identity_before = [(digest(p), p.stat().st_ino, p.stat().st_mtime_ns) for p in keys]
    environment = {"HOME": str(Path.home()), "PATH": "/usr/bin:/bin", "LC_ALL": "C.UTF-8"}
    for name in ("DICTA_ANDROID_HOME", "DICTA_JAVA_HOME"):
        if name in os.environ:
            environment[name] = os.environ[name]

    def invoke(arguments, closed=False):
        # No rc files, DISPLAY, DBus, inherited credentials, controlling terminal
        # or TTY. In the second case fd 0 is actually absent, not /dev/null.
        return subprocess.run(["/bin/bash", "--noprofile", "--norc", "-c", 'exec /usr/bin/python3 "$@"',
                               "dicta-signing-test", str(SCRIPT), *map(str, arguments)],
                              cwd=ROOT, env=environment, stdin=subprocess.DEVNULL,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120,
                              start_new_session=True, preexec_fn=(lambda: os.close(0)) if closed else None)

    results = []
    for label, closed in (("no-tty-devnull", False), ("no-tty-fd0-closed", True)):
        copied, signed = work / f"{label}-input.apk", work / f"{label}-signed.apk"
        shutil.copyfile(source, copied)
        result = invoke(["release", "--auto", "--apk", copied, "--out", signed], closed)
        require(result.returncode == 0, f"Real signing failed in {label}; diagnostics suppressed")
        public = json.loads(result.stdout)
        require(all(public[k] for k in ("signatureVerified", "zipCrcVerified", "alignmentVerified", "payloadUnchanged", "inputUnchanged", "signingMaterialAbsent")), "Incomplete verification")
        require(digest(copied) == original, "Test input was modified")
        require(public["certificateSha256"] == (ROOT / "scripts/android-signing-cert.sha256").read_text().strip(), "Certificate mismatch")
        before = (digest(signed), signed.stat().st_ino, signed.stat().st_mtime_ns)
        repeated = invoke(["release", "--auto", "--apk", copied, "--out", signed], closed)
        require(repeated.returncode != 0, "Existing output should be refused")
        require(before == (digest(signed), signed.stat().st_ino, signed.stat().st_mtime_ns), "Existing output changed")
        results.append({"mode": label, "freshShell": True, "controllingTerminal": False,
                        "displayAndDbusAbsent": True, "exitCode": result.returncode,
                        "existingOutputPreserved": True, **public})

    # Independent official-tool verification (does not call the signing module).
    sdk = Path(environment.get("DICTA_ANDROID_HOME", str(Path.home() / ".local/share/dicta-tools/android-sdk")))
    apksigner = sdk / "build-tools/35.0.0/apksigner"
    verified = subprocess.run([str(apksigner), "verify", "--verbose", "--print-certs", str(signed)],
                              env=environment, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL, timeout=120)
    require(verified.returncode == 0, "Independent apksigner verification failed")
    expected = results[-1]["certificateSha256"].encode()
    require(b"Signer #1 certificate SHA-256 digest: " + expected in verified.stdout, "Independent certificate mismatch")
    tampered = work / "tampered-negative-test.apk"
    shutil.copyfile(signed, tampered)
    with zipfile.ZipFile(tampered, "a") as archive:
        archive.writestr("assets/signing-integrity-negative-probe", b"intentional test tampering")
    negative = subprocess.run([str(apksigner), "verify", str(tampered)], env=environment,
                              stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
    require(negative.returncode != 0, "Tampered APK was not rejected")
    require(digest(source) == original, "Original APK changed")
    require(identity_before == [(digest(p), p.stat().st_ino, p.stat().st_mtime_ns) for p in keys], "Signing identity changed")
    print(json.dumps({"sourceApk": str(source.relative_to(ROOT)) if source.is_relative_to(ROOT) else str(source),
                      "sourceSha256": original, "sourceUnchanged": True, "identityUnchanged": True,
                      "independentApksignerVerify": True, "tamperedApkRejected": True,
                      "tests": results}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        raise SystemExit(str(error) if isinstance(error, RuntimeError) else "Integration stopped; diagnostics suppressed")
