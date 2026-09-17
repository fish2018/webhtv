import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "webhome-devkit" / "scripts" / "validate_webtheme.py"
SCHEMA = ROOT / "webhome-devkit" / "schemas" / "webtheme-v2.schema.json"
MANIFEST = ROOT / "app" / "src" / "main" / "assets" / "webhome" / "theme.json"


def load_validator():
    spec = importlib.util.spec_from_file_location("validate_webtheme", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ValidateWebThemeTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.validator = load_validator()
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    def test_bundled_manifest_is_valid_and_reserved_fields_are_reported(self):
        errors, warnings = self.validator.validate_document(
            self.manifest, self.schema, target="mobile"
        )

        self.assertEqual([], errors)
        self.assertTrue(any("player" in warning and "reserved" in warning for warning in warnings))

    def test_unknown_permission_is_rejected(self):
        document = deepcopy(self.manifest)
        document["permissions"]["home"].append("net.request")

        errors, _ = self.validator.validate_document(document, self.schema, target="mobile")

        self.assertTrue(any("net.request" in error for error in errors))

    def test_invalid_schema_root_is_reported_without_crashing(self):
        errors, warnings = self.validator.validate_document(self.manifest, True)

        self.assertEqual([], warnings)
        self.assertEqual(["Invalid WebTheme schema: root must be a JSON object"], errors)

    def test_invalid_uri_reference_is_rejected(self):
        document = deepcopy(self.manifest)
        document["pages"]["home"]["entry"] = "bad%zz"

        errors, _ = self.validator.validate_document(document, self.schema, target="mobile")

        self.assertTrue(any("uri-reference" in error for error in errors))

    def test_requested_target_must_be_supported_and_declared(self):
        unsupported, _ = self.validator.validate_document(
            self.manifest, self.schema, target="tablet"
        )
        self.assertTrue(any("tablet" in error and "target" in error.lower() for error in unsupported))

        document = deepcopy(self.manifest)
        document["targets"] = ["leanback"]
        mismatched, _ = self.validator.validate_document(document, self.schema, target="mobile")
        self.assertTrue(any("mobile" in error and "target" in error.lower() for error in mismatched))

    def test_page_requires_its_contract_permission(self):
        document = deepcopy(self.manifest)
        document["permissions"]["detail"].remove("vod.detail")

        errors, _ = self.validator.validate_document(document, self.schema, target="leanback")

        self.assertTrue(any("vod.detail" in error for error in errors))

    def test_cli_enforces_the_runtime_manifest_byte_limit(self):
        document = deepcopy(self.manifest)
        limit = self.schema["x-webhtv-maxBytes"]
        document["tokens"] = {"payload": "x" * limit}

        with tempfile.TemporaryDirectory() as directory:
            oversized_manifest = Path(directory) / "theme.json"
            oversized_manifest.write_text(json.dumps(document), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(oversized_manifest), "--target", "mobile"],
                cwd=ROOT,
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertEqual(1, result.returncode)
        self.assertIn("UTF-8 bytes", result.stderr)
        self.assertIn(str(limit), result.stderr)

    def test_cli_reports_invalid_utf8_as_an_input_error(self):
        with tempfile.TemporaryDirectory() as directory:
            invalid_manifest = Path(directory) / "theme.json"
            invalid_manifest.write_bytes(b"\xff")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(invalid_manifest)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertEqual(2, result.returncode)
        self.assertIn("Invalid UTF-8", result.stderr)

    def test_cli_reports_warnings_and_uses_stable_exit_codes(self):
        valid = subprocess.run(
            [sys.executable, str(SCRIPT), str(MANIFEST), "--target", "mobile"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, valid.returncode)
        self.assertIn("OK:", valid.stdout)
        self.assertIn("reserved", valid.stderr)

        document = deepcopy(self.manifest)
        document["permissions"]["home"].append("net.request")
        with tempfile.TemporaryDirectory() as directory:
            invalid_manifest = Path(directory) / "theme.json"
            invalid_manifest.write_text(json.dumps(document), encoding="utf-8")
            invalid = subprocess.run(
                [sys.executable, str(SCRIPT), str(invalid_manifest), "--target", "mobile"],
                cwd=ROOT,
                capture_output=True,
                text=True,
                check=False,
            )
        self.assertEqual(1, invalid.returncode)
        self.assertIn("net.request", invalid.stderr)


if __name__ == "__main__":
    unittest.main()
