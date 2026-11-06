"""Offline regression checks for the pinned suite-interlis inventory."""

import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest

from run import sha256
from suite_inventory import CATALOG_FILE, load_catalog, verify_checkout, verify_input


class SuiteCatalogTest(unittest.TestCase):
    def setUp(self):
        self.catalog = json.loads(CATALOG_FILE.read_text(encoding="utf-8"))
        self.case = next(case for case in self.catalog["cases"] if case["id"] == "RSU.T01a")

    def test_catalog_covers_the_pinned_suite(self):
        catalog = load_catalog()
        self.assertEqual(catalog["upstream"]["commit"], "f30711184d2374feacb81c5742382d35597164ca")
        self.assertEqual(catalog["upstream"]["transferVersion"], "2.3")
        self.assertEqual(len(catalog["models"]), 9)
        self.assertEqual(len(catalog["cases"]), 223)
        cases = {case["id"]: case for case in catalog["cases"]}
        self.assertEqual(len(cases), 223)
        for case_id, accepted in {
            "RSU.T01a": False,
            "RHE.T01a": False,
            "RKo.T06c": False,
            "REB.T01a": True,
        }.items():
            with self.subTest(case_id=case_id):
                self.assertIs(cases[case_id]["expectedAcceptance"], accepted)

    def assert_invalid_catalog(self, message):
        with TemporaryDirectory() as directory:
            candidate = Path(directory) / "cases.json"
            candidate.write_text(json.dumps(self.catalog), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, message):
                load_catalog(candidate)

    def test_duplicate_case_id_is_rejected(self):
        self.catalog["cases"][1] = self.catalog["cases"][0]
        self.assert_invalid_catalog(r"(?i)duplicate")

    def test_unknown_oracle_status_is_rejected(self):
        self.case["oracleStatus"] = "PASS"
        self.assert_invalid_catalog(r"(?i)oracle")

    def test_agreement_requires_matching_expectations(self):
        self.case["oracleStatus"] = "AGREES"
        self.case["documentation"]["expectedAcceptance"] = True
        self.assert_invalid_catalog(r"(?i)expectation|oracle")

    def test_missing_source_reference_is_rejected(self):
        del self.case["java"]
        self.assert_invalid_catalog(r"(?i)java|source")


class SuiteCheckoutTest(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.checkout = Path(directory.name).resolve()
        self.git("init", "-q")
        self.model_path = "data/models/Local.ili"
        self.model = self.checkout / self.model_path
        self.model.parent.mkdir(parents=True)
        self.model.write_bytes(b"INTERLIS 2.3;\n")
        self.digest = sha256(self.model)
        self.git("add", self.model_path)
        self.git(
            "-c", "user.name=Inventory Test",
            "-c", "user.email=inventory@example.invalid",
            "-c", "commit.gpgsign=false",
            "-c", "core.hooksPath=/dev/null",
            "commit", "-qm", "Test fixture",
        )
        self.commit = self.git("rev-parse", "HEAD")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.checkout, text=True).strip()

    def test_checkout_and_file_match_pin(self):
        self.assertEqual(verify_checkout(self.checkout, self.commit), self.checkout)
        self.assertEqual(verify_input(self.checkout, self.model_path, self.digest), self.model)

    def test_wrong_git_pin_is_rejected(self):
        with self.assertRaisesRegex(ValueError, r"(?i)pin|commit|HEAD"):
            verify_checkout(self.checkout, "0" * len(self.commit))

    def test_missing_git_checkout_is_rejected(self):
        with self.assertRaisesRegex(ValueError, r"(?i)checkout|git"):
            verify_checkout(self.checkout / "missing", self.commit)

    def test_missing_original_file_is_rejected(self):
        self.model.unlink()
        with self.assertRaisesRegex(ValueError, r"(?i)missing"):
            verify_input(self.checkout, self.model_path, self.digest)

    def test_changed_original_file_is_rejected(self):
        self.model.write_bytes(b"INTERLIS 2.4;\n")
        with self.assertRaisesRegex(ValueError, r"(?i)hash|SHA-256"):
            verify_input(self.checkout, self.model_path, self.digest)


if __name__ == "__main__":
    unittest.main()
