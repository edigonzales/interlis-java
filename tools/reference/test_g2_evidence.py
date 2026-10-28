import io
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import unittest

import g2_evidence as evidence


class G2EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        (self.root / ".gitignore").write_text("build/\n")
        (self.root / "source.txt").write_text("original\n")
        subprocess.run(["git", "-C", str(self.root), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Fixture", "-c",
                        "user.email=fixture@example.invalid", "commit", "-qm", "Fixture"], check=True)
        self.out = self.root / "build/input"

    def test_snapshot_preserves_tracked_modified_and_nonignored_untracked_files(self):
        (self.root / "source.txt").write_text("modified 🦊\n")
        (self.root / "new.txt").write_text("untracked\n")
        manifest = evidence.snapshot(self.root, self.out)
        self.assertEqual(3, len(manifest["files"]))
        self.assertEqual(manifest, evidence.verify(self.out, self.root))
        (self.root / "source.txt").write_text("later edit\n")
        with self.assertRaisesRegex(ValueError, "Working sources differ"):
            evidence.verify(self.out, self.root)
        evidence.verify(self.out)  # The frozen review input is still valid.

    def test_new_source_and_executable_mode_changes_invalidate_the_review(self):
        evidence.snapshot(self.root, self.out)
        (self.root / "new.txt").write_text("extra\n")
        with self.assertRaisesRegex(ValueError, "Working sources differ"):
            evidence.verify(self.out, self.root)
        (self.root / "new.txt").unlink()
        (self.root / "source.txt").chmod(0o755)
        with self.assertRaisesRegex(ValueError, "Working sources differ"):
            evidence.verify(self.out, self.root)

    def test_archive_tampering_and_unsafe_members_cannot_certify_a_snapshot(self):
        evidence.snapshot(self.root, self.out)
        path = self.out / "source.tar.gz"
        path.write_bytes(path.read_bytes() + b"tampered")
        with self.assertRaisesRegex(ValueError, "Changed source archive"):
            evidence.verify(self.out)
        with tarfile.open(path, "w:gz") as archive:
            member = tarfile.TarInfo("../outside")
            member.size = 1
            archive.addfile(member, io.BytesIO(b"x"))
        manifest = json.loads((self.out / "manifest.json").read_text())
        manifest["archiveSha256"] = evidence.sha(path)
        evidence.write(self.out / "manifest.json", manifest)
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            evidence.verify(self.out)

    def test_missing_failed_empty_and_skipped_jvm_reports_are_not_success(self):
        started = time.time_ns()
        name = "interlis-fixture/build/test-results/test/TEST-fixture.xml"
        expected = {name: 2}
        with self.assertRaisesRegex(ValueError, "Missing JVM"):
            evidence.jvm_summary(self.root, expected, started)
        path = self.root / name
        path.parent.mkdir(parents=True)
        for counts in [(0, 0, 0, 0), (1, 1, 0, 0), (1, 0, 1, 0), (1, 0, 0, 1)]:
            path.write_text('<testsuite tests="%s" errors="%s" failures="%s" skipped="%s"/>' % counts)
            with self.assertRaisesRegex(ValueError, "Incomplete"):
                evidence.jvm_summary(self.root, expected, started)
        path.write_text('<testsuite tests="2" errors="0" failures="0" skipped="0"><testcase/><testcase/></testsuite>')
        self.assertEqual(2, evidence.jvm_summary(self.root, expected, started)["tests"])
        os.utime(path, (946684800, 946684800))
        with self.assertRaisesRegex(ValueError, "Stale JVM"):
            evidence.jvm_summary(self.root, expected, started)

    def test_obsolete_module_and_missing_testcase_details_do_not_count_as_a_jvm_pass(self):
        started = time.time_ns()
        name = "interlis-fixture/build/test-results/test/TEST-fixture.xml"
        path = self.root / name
        path.parent.mkdir(parents=True)
        path.write_text('<testsuite tests="688" errors="0" failures="0" skipped="0"/>')
        with self.assertRaisesRegex(ValueError, "Incomplete JVM testcase"):
            evidence.jvm_summary(self.root, {name: 688}, started)
        other = self.root / "interlis-obsolete/build/test-results/test/TEST-old.xml"
        other.parent.mkdir(parents=True)
        other.write_text('<testsuite tests="1"><testcase/></testsuite>')
        with self.assertRaisesRegex(ValueError, "Unexpected or missing JVM suites"):
            evidence.jvm_summary(self.root, {name: 688}, started)

    def test_partial_browser_matrix_failed_sdk_and_unresolved_conformance_are_rejected(self):
        rows = [{"browser": name, "status": "passed", "result": {"status": "ok"}}
                for name in ["chromium", "firefox", "webkit"]]
        with self.assertRaisesRegex(ValueError, "Incomplete report"):
            evidence.report_result("teaVmBrowsers", rows)
        for partial in [[], rows[:2], [rows[0]] * 3]:
            with self.assertRaisesRegex(ValueError, "Incomplete browser"):
                evidence.report_result("teaVmBrowsers", partial)
        with self.assertRaisesRegex(ValueError, "Failed runtime"):
            evidence.report_result("sdkProcesses", {"status": "failed"})
        for name in ["FAIL", "ERROR", "XPASS"]:
            summary = dict(FAIL=0, ERROR=0, XPASS=0)
            summary[name] = 1
            with self.assertRaisesRegex(ValueError, "Unresolved"):
                evidence.report_result("project", {"overallResult": "PASS", "summary": summary})

    def test_review_counterexamples_for_eight_empty_green_report_variants_are_rejected(self):
        examples = {
            "reference": {},
            "project": {"overallResult": "PASS", "summary": dict(PASS=0, XFAIL=0, FAIL=0, ERROR=0, XPASS=0, TOTAL=0)},
            "teaVmJavaScript": {"status": "passed"},
            "teaVmWasmGC": {"status": "passed"},
            "webImageNode": {"status": "ok"},
            "safari": {"status": "passed", "browser": "Safari", "result": {"status": "ok"}},
            "webImageBrowsers": [{"browser": name, "status": "passed", "result": {"status": "ok"}}
                                 for name in ["chromium", "firefox", "webkit"]],
            "sdkProcesses": {"status": "passed", "schemaVersion": "1.0", "commands": []},
        }
        for key, data in examples.items():
            with self.subTest(key=key), self.assertRaises(ValueError):
                evidence.report_result(key, data)

    def test_portable_case_count_and_browser_contract_values_are_required(self):
        profile = json.loads(evidence.PROFILE.read_text())
        data = dict(status="passed", runtime="teavm-wasm-gc", node=profile["nodeVersion"], tool=profile["projectVersion"],
                    outcome="succeeded", stabilizationCases=profile["portableCases"])
        evidence.report_result("teaVmWasmGC", data)
        for value in [0, profile["portableCases"] - 1, None]:
            with self.assertRaises(ValueError):
                evidence.report_result("teaVmWasmGC", data | {"stabilizationCases": value})
        browser = dict(status="ok", stabilizationCases=profile["portableCases"], capabilities=profile["projectVersion"],
                       analyzeRevision="1", compiledOutcome="succeeded", rejectedOutcome="rejected", workerRestartOutcome="succeeded",
                       exactDecimal=True, diagnostics=0, diagramNodes=2, diagramEdges=0, diagramAssociations=0)
        evidence.portable_contract(browser, profile, True)
        with self.assertRaises(ValueError):
            evidence.portable_contract(browser | {"exactDecimal": False}, profile, True)

    def test_reference_details_and_project_reclassification_must_agree(self):
        profile = json.loads(evidence.PROFILE.read_text())
        profile["conformance"] = profile["conformance"] | {"selectedCases": 2,
            "caseIdsSha256": hashlib.sha256(b'["one","two"]').hexdigest()}
        tests = [dict(testId=name, classification=status, languageVersion="2.4", timedOut=False, exitCode=code,
                      command=["compiler", name], actualOutcome=outcome)
                 for name, status, code, outcome in [("one", "PASS", 0, "accepted"), ("two", "FAIL", 1, "rejected")]]
        summary = dict(PASS=1, XFAIL=0, FAIL=1, ERROR=0, XPASS=0, TOTAL=2)
        raw = dict(schemaVersion="1.0", overallResult="FAIL", summary=summary,
                   compilers=[dict(id="interlis-java", tests=tests, summary=summary)])
        evidence.report_result("reference", raw, profile)
        with self.assertRaisesRegex(ValueError, "disagrees"):
            evidence.report_result("reference", raw | {"summary": summary | {"PASS": 2}}, profile)
        project = dict(referenceSummary=summary, referenceResult="FAIL", normativeAssessments=[dict(testId="two")],
                       summary=summary | {"FAIL": 0, "XFAIL": 1})
        evidence.conformance_pair(raw, project)
        with self.assertRaisesRegex(ValueError, "Missing or duplicate"):
            evidence.conformance_pair(raw, project | {"normativeAssessments": []})


if __name__ == "__main__":
    unittest.main()
