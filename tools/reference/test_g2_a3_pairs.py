#!/usr/bin/env python3
"""Checks the A3 JVM/portable countercheck pairing artifact."""
from __future__ import annotations

import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_a3_pairs as pairs

ROOT = Path(__file__).resolve().parents[2]


class A3PairsTest(unittest.TestCase):
    def test_versioned_pairs_match_actual_jvm_and_portable_sources(self):
        data = json.loads((ROOT / "docs/evidence/stabilization/g2-a3-semantic-pairs.json").read_text())
        matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
        self.assertEqual(3, pairs.validate(data)["pairs"])
        self.assertEqual("COUNTERCHECK_PAIRS_MATCHED", data["status"])
        self.assertEqual(hashlib.sha256((ROOT / "docs/evidence/stabilization/g2-a3-semantic-pairs.json").read_bytes()).hexdigest(),
                         matrix["a3SemanticPairs"]["sha256"])

    def test_changed_jvm_method_is_rejected(self):
        data = json.loads((ROOT / "docs/evidence/stabilization/g2-a3-semantic-pairs.json").read_text())
        broken = json.loads(json.dumps(data)); broken["pairs"][0]["jvm"]["methodSha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Changed JVM method"):
            pairs.validate(broken)


if __name__ == "__main__":
    unittest.main()
