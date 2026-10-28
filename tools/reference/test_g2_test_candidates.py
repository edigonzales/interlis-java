#!/usr/bin/env python3
"""Checks the R4 keyword candidate index is read-only and reproducible."""
from __future__ import annotations

import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_test_candidates as index

ROOT = Path(__file__).resolve().parents[2]


class TestCandidateIndexTest(unittest.TestCase):
    def test_versioned_index_contains_only_verified_candidate_methods(self):
        data = json.loads((ROOT / "docs/evidence/stabilization/g2-test-candidate-index.json").read_text())
        matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
        validation = index.validate(data)
        self.assertGreater(validation["testMethods"], 0)
        self.assertGreater(validation["conditions"], 0)
        self.assertGreater(validation["requirements"], 0)
        self.assertEqual("CANDIDATE_ONLY", validation["acceptance"])
        self.assertTrue(all(candidate["status"] == "CANDIDATE_ONLY"
                            for section in ("conditions", "requirements")
                            for row in data[section] for candidate in row["candidates"]))
        self.assertEqual(hashlib.sha256((ROOT / "docs/evidence/stabilization/g2-test-candidate-index.json").read_bytes()).hexdigest(),
                         matrix["testCandidateIndex"]["sha256"])

    def test_versioned_index_matches_current_conditions_and_test_sources(self):
        versioned = json.loads((ROOT / "docs/evidence/stabilization/g2-test-candidate-index.json").read_text())
        generated = index.build()
        generated["validation"] = index.validate(generated)
        self.assertEqual(generated, versioned)

    def test_invented_candidate_is_rejected(self):
        data = json.loads((ROOT / "docs/evidence/stabilization/g2-test-candidate-index.json").read_text())
        broken = json.loads(json.dumps(data))
        broken["conditions"][0]["candidates"] = [{
            "suite": "x.Y", "source": "interlis-compiler/src/test/java/x/Y.java", "method": "invented",
            "methodStart": 0, "methodEnd": 1, "methodSha256": "0" * 64, "score": 1, "status": "CANDIDATE_ONLY"}]
        with self.assertRaisesRegex(ValueError, "Unknown candidate method"):
            index.validate(broken)


if __name__ == "__main__":
    unittest.main()
