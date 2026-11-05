"""Local counterexamples for the validator. These fixtures are not review judgments."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

from jsonschema import ValidationError

from gate import digest, read, validate


class ReviewGateTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name).resolve()
        output = root / "output"
        scratch = root / "scratch"
        output.mkdir()
        scratch.mkdir()
        self.source = root / "Candidate.java"
        self.source.write_text("original")
        self.probe = scratch / "probe.json"
        self.probe.write_text(json.dumps({"exitCode": 0}))
        (output / "review.md").write_text("fixture")
        (output / "verdict.json").write_text("{}")
        self.request = dict(assignment="compiler-gui-swing", phaseId="G0", baseSha="a"*40,
                            phaseBaseSha="a"*40, candidateSha="b"*40, candidateTree="c"*40,
                            outputDirectory=str(output), scratchDirectory=str(scratch),
                            requiredCriteria=["GUI-01", "GUI-02"])
        self.verdict = {key: value for key, value in self.request.items()
                        if key not in ("outputDirectory", "scratchDirectory", "requiredCriteria")}
        self.verdict.update(schemaVersion=1, verdict="PASS", summary="fixture", limitations=[], findings=[],
                            criteria=[dict(id=criterion, status="PASS", assessment="fixture", evidence=[str(self.source)])
                                      for criterion in self.request["requiredCriteria"]],
                            independentChecks=[dict(question="fixture", commandLog=str(self.probe),
                                                    expected="fixture", observed="fixture", exitCode=0, status="PASS")])
        self.process = dict(exitCode=0, timeout=False, fatalError=False, completed=True, sessionId="fixture",
                            model=dict(id="deepseek-flash", providerID="deepseek"), endpoint="https://api.deepseek.com")
        self.manifest = {str(self.source): digest(self.source)}
        self.probes = {str(self.probe): digest(self.probe)}
        self.schema = read(Path(__file__).resolve().parents[1] / "review-verdict.schema.json")

    def check(self, verdict=None, process=None):
        validate(self.request, verdict or self.verdict, process or self.process, self.manifest, self.probes,
                 self.schema, "b"*40, "c"*40)

    def test_control(self):
        self.check()

    def test_rejects_invalid_verdicts(self):
        for change in (dict(candidateSha="d"*40), dict(criteria=self.verdict["criteria"][:1]),
                       dict(criteria=self.verdict["criteria"][:1]*2), dict(verdict="FAIL"), dict(verdict="BLOCKED"),
                       dict(independentChecks=[])):
            with self.subTest(change=change), self.assertRaises((ValueError, ValidationError)):
                verdict = copy.deepcopy(self.verdict)
                verdict.update(change)
                self.check(verdict=verdict)

    def test_rejects_aborted_or_wrong_model_process(self):
        for change in (dict(exitCode=-9), dict(timeout=True), dict(fatalError=True), dict(completed=False),
                       dict(model=dict(id="deepseek-flash", providerID="openrouter"))):
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.check(process={**self.process, **change})

    def test_rejects_changed_sources(self):
        self.source.write_text("changed")
        with self.assertRaisesRegex(ValueError, "changed"):
            self.check()

    def test_rejects_tampered_counterprobe(self):
        self.probe.write_text(json.dumps({"exitCode": 0, "stderr": "edited after execution"}))
        with self.assertRaisesRegex(ValueError, "Counterprobe"):
            self.check()


if __name__ == "__main__":
    unittest.main()
