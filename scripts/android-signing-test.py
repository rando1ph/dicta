"""Key-free regression tests adapted from Oraculum android-signing-test.py."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("signing", Path(__file__).with_name("android-signing.py"))
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="dicta-signing-test-")
        self.root = Path(self.temporary.name)
        self.keys = self.root / "keys"
        self.keys.mkdir(mode=0o700)
        self.store, self.password = self.keys / "dicta-release.jks", self.keys / "keystore.pass"
        self.store.write_bytes(bytes.fromhex("feedfeed") + b"not-a-real-keystore")
        self.password.write_bytes(b"fabricated-unit-test-password\n")
        for path in (self.store, self.password):
            path.chmod(0o600)
        self.pin = self.root / "certificate.sha256"
        self.pin.write_text("a" * 64 + "\n")
        self.overrides = patch.multiple(signing, KEY_DIR=self.keys, CERT_FILE=self.pin)
        self.overrides.start()
        self.source, self.target = self.root / "input.apk", self.root / "output.apk"
        with zipfile.ZipFile(self.source, "w") as archive:
            for name in ("AndroidManifest.xml", "classes.dex", "resources.arsc"):
                archive.writestr(name, b"fabricated-apk-data")

    def tearDown(self):
        self.overrides.stop()
        self.temporary.cleanup()

    def test_permissions_for_directory_key_and_password(self):
        for path in (self.keys, self.store, self.password):
            mode = path.stat().st_mode & 0o777
            path.chmod(0o755 if path.is_dir() else 0o644)
            with self.subTest(path=path), self.assertRaisesRegex(RuntimeError, "permissions"):
                signing.material()
            path.chmod(mode)

    def test_symlink_and_hardlink_rejected(self):
        other = self.root / "other"
        self.store.rename(other)
        self.store.symlink_to(other)
        with self.assertRaisesRegex(RuntimeError, "symlinks"):
            signing.material()
        self.store.unlink()
        self.store.hardlink_to(other)
        with self.assertRaisesRegex(RuntimeError, "hard-linked"):
            signing.material()

    def test_credentials_inside_repo_rejected(self):
        with patch.object(signing, "ROOT", self.root), self.assertRaisesRegex(RuntimeError, "outside"):
            signing.material()

    def test_invalid_password_format_and_wrong_store_type(self):
        for content in (b"", b"\n", b"no-final-LF", b"one\ntwo\n", b"one\r\n", b"bad\0\n"):
            self.password.write_bytes(content)
            with self.subTest(content=content), self.assertRaises(RuntimeError):
                signing.material()
        self.password.write_bytes(b"fabricated-password\n")
        self.store.write_bytes(b"not-JKS")
        with self.assertRaisesRegex(RuntimeError, "actual JKS"):
            signing.material()

    def test_init_reuses_existing_identity_without_modification(self):
        before = [(p.read_bytes(), p.stat().st_ino, p.stat().st_mtime_ns) for p in (self.store, self.password)]
        with patch.object(signing, "key_fingerprint", return_value="a" * 64), patch.object(signing, "run_tool") as run:
            self.assertEqual(signing.initialize()["identity"], "reused")
            run.assert_not_called()
        self.assertEqual(before, [(p.read_bytes(), p.stat().st_ino, p.stat().st_mtime_ns) for p in (self.store, self.password)])

    def test_init_never_replaces_partial_or_missing_pinned_identity(self):
        self.store.unlink()
        with patch.object(signing, "run_tool") as run:
            with self.assertRaisesRegex(RuntimeError, "Incomplete"):
                signing.initialize()
            self.password.unlink()
            with self.assertRaisesRegex(RuntimeError, "cannot be regenerated"):
                signing.initialize()
            run.assert_not_called()

    def test_environment_removes_password_and_jvm_hooks(self):
        with patch.dict(os.environ, {"DICTA_RELEASE_STORE_PASSWORD": "fake", "JAVA_TOOL_OPTIONS": "fake", "BASH_ENV": "fake"}):
            env = signing.tool_environment()
        for name in ("DICTA_RELEASE_STORE_PASSWORD", "JAVA_TOOL_OPTIONS", "BASH_ENV"):
            self.assertNotIn(name, env)

    def test_tool_input_closed_and_failures_suppressed(self):
        for failure in (subprocess.CalledProcessError(1, ["apksigner"], stderr=b"fabricated-sensitive-data"),
                        subprocess.TimeoutExpired(["apksigner"], 120, output=b"fabricated-sensitive-data"),
                        FileNotFoundError("fabricated-sensitive-data")):
            with patch.object(signing.subprocess, "run", side_effect=failure) as run:
                with self.assertRaises(RuntimeError) as caught:
                    signing.run_tool(["apksigner", "sign"])
                self.assertNotIn("sensitive-data", str(caught.exception))
                self.assertEqual(run.call_args.kwargs["stdin"], subprocess.DEVNULL)
                self.assertEqual(run.call_args.kwargs["stderr"], subprocess.DEVNULL)

    def test_cli_never_prompts_and_requires_explicit_prebuilt_paths(self):
        with patch("builtins.input", side_effect=AssertionError("prompt")), patch.object(signing, "release", return_value={}) as release:
            signing.main(["release", "--auto", "--apk", str(self.source), "--out", str(self.target)])
            release.assert_called_once_with(self.source, self.target)
            for arguments in (["release"], ["release", "--auto"], ["init", "--auto"]):
                with self.subTest(arguments=arguments), self.assertRaises(RuntimeError):
                    signing.main(arguments)

    def test_payload_rejects_secret_and_corrupt_zip(self):
        with zipfile.ZipFile(self.source, "a") as archive:
            archive.writestr("assets/unsafe", self.password.read_bytes().strip())
        with self.assertRaisesRegex(RuntimeError, "Signing material"):
            signing.apk_payload(self.source)
        self.source.write_bytes(b"broken archive")
        with self.assertRaises(zipfile.BadZipFile):
            signing.apk_payload(self.source)

    def test_certificate_mismatch_rejected(self):
        with patch.object(signing, "run_tool", return_value=b"Signer #1 certificate SHA-256 digest: " + b"b" * 64 + b"\n"), patch.object(signing, "tool", side_effect=lambda name: name):
            with self.assertRaisesRegex(RuntimeError, "certificate"):
                signing.verify_apk(self.source, "a" * 64)

    def exercise_release(self, failure=None):
        before = self.source.read_bytes()
        commands = []
        metadata = {"applicationId": "dev.dicta.fixture", "versionCode": "4", "versionName": "test"}
        def run(command):
            commands.append(command)
            self.assertNotIn("fabricated-unit-test-password", repr(command))
            self.assertNotIn(command[0], ("sudo", "secret-tool", "bash", "gradle"))
            if command[0] == "zipalign":
                Path(command[-1]).write_bytes(Path(command[-2]).read_bytes())
            if command[:2] == ["apksigner", "sign"]:
                self.assertEqual(command[command.index("--ks-pass") + 1], "file:" + str(self.password))
                self.assertNotIn("--key-pass", command)  # same password; do not consume a second file line
                candidate = Path(command[command.index("--out") + 1])
                candidate.write_bytes(Path(command[-1]).read_bytes())
                if failure == "sign":
                    raise RuntimeError("simulated signer failure")
                if failure == "payload":
                    with zipfile.ZipFile(candidate, "a") as archive:
                        archive.writestr("assets/unexpected", b"changed")
            return b""
        def verify(*args):
            if failure == "verify":
                raise RuntimeError("simulated verification failure")
            return {**metadata, "certificateSha256": "a" * 64}
        with patch.object(signing, "key_fingerprint", return_value="b" * 64 if failure == "pin" else "a" * 64), \
                patch.object(signing, "tool", side_effect=lambda name: name), \
                patch.object(signing, "run_tool", side_effect=run), \
                patch.object(signing, "apk_metadata", return_value=metadata), \
                patch.object(signing, "verify_apk", side_effect=verify):
            if failure:
                with self.assertRaises(RuntimeError):
                    signing.release(self.source, self.target)
                self.assertFalse(self.target.exists())
                self.assertEqual(list(self.root.glob(".sign-*")), [])
            else:
                result = signing.release(self.source, self.target)
                self.assertTrue(result["inputUnchanged"] and result["payloadUnchanged"])
                output_before = self.target.read_bytes()
                with self.assertRaisesRegex(RuntimeError, "Existing output"):
                    signing.release(self.source, self.target)
                self.assertEqual(self.target.read_bytes(), output_before)
        self.assertEqual(self.source.read_bytes(), before)
        return commands

    def test_sign_copy_using_file_sources_no_build_and_no_overwrite(self):
        commands = self.exercise_release()
        self.assertEqual(sum(command[:2] == ["apksigner", "sign"] for command in commands), 1)

    def test_failures_do_not_publish_partial_artifacts(self):
        for failure in ("pin", "sign", "verify", "payload"):
            with self.subTest(failure=failure):
                self.exercise_release(failure)

    def test_atomic_publication_does_not_overwrite_dangling_symlink(self):
        self.target.symlink_to(self.root / "absent")
        with self.assertRaises(FileExistsError):
            signing.publish(self.source, self.target)
        self.assertTrue(self.target.is_symlink())


if __name__ == "__main__":
    unittest.main()
