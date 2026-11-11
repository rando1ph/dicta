#!/usr/bin/env python3
"""Verify delivery bytes and scan for the locally configured test credential without printing it."""
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
metadata = json.loads((root / "docs/t07/evidence/apk.json").read_text())
apk = root / metadata["path"]
assert hashlib.sha256(apk.read_bytes()).hexdigest() == metadata["sha256"]
assert subprocess.check_output(["git", "branch", "--show-current"], cwd=root, text=True).strip() == "main"
key_file = Path.home() / ".config/dicta/deepseek_api_key"
real_key = key_file.read_bytes().strip() if key_file.exists() else b""
private_files = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root).split(b"\0")
files = [root / item.decode() for item in private_files if item and (root / item.decode()).is_file()]
files += list((root / "artifacts/t07/logs").glob("*"))
files += list((root / "artifacts/t07-revision/logs").glob("*"))
for path in files:
    if not path.is_file():
        continue
    data = path.read_bytes()
    assert not real_key or real_key not in data, f"Credential match in {path.relative_to(root)}"
    assert not re.search(rb"sk-[A-Za-z0-9_-]{24,}", data), f"Key-shaped text in {path.relative_to(root)}"
    assert path.suffix.lower() not in (".jks", ".keystore", ".pass"), "Private signing material is tracked"
with zipfile.ZipFile(apk) as package:
    assert package.testzip() is None
    members = package.namelist()
    for name in members:
        data = package.read(name)
        assert not real_key or real_key not in data, "Credential match in APK"
        if name.endswith(".dex"):
            # OkHttp's production Http2Connection.Builder contains the diagnostic
            # literal "MockWebServer ". Match class/package descriptors instead.
            assert all(marker not in data for marker in (b"Ldev/dicta/prototype/query/FakeProvider;", b"Lokhttp3/mockwebserver/", b"Lokhttp3/tls/", b"Ldev/dicta/prototype/T07", b"test-only-not-a-real-key")), "Test implementation is packaged in app"
report = {
    "branch": "main", "files_scanned": len(files), "apk_members_scanned": len(members),
    "real_credential_matches": 0, "key_shaped_matches": 0, "test_fixtures_in_app_apk": False,
    "zip_crc_valid": True, "apk_sha256_matches_manifest": True,
    "formal_release_signing_performed": False, "human_acceptance": "pending",
}
(root / "docs/t07/evidence/security.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report))
