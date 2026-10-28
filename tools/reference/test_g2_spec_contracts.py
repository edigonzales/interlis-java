#!/usr/bin/env python3
"""Checks the A2-R4 specification contract worklist."""
from __future__ import annotations

import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_spec_contracts as contracts

ROOT = Path(__file__).resolve().parents[2]


class SpecContractTest(unittest.TestCase):
    def test_versioned_worklist_preserves_all_numbered_requirements_and_exposes_gaps(self):
        source = json.loads((ROOT / "docs/evidence/stabilization/g2-source-inventory.json").read_text())
        data = json.loads((ROOT / "docs/evidence/stabilization/g2-spec-contract-inventory.json").read_text())
        matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
        validation = contracts.validate(data, source)
        self.assertEqual(289, validation["numbered"])
        self.assertEqual(12, validation["unnumbered"])
        self.assertEqual(45, validation["matrixLinked"])
        self.assertEqual(0, validation["ownerMissing"])
        self.assertEqual(164, validation["outsideG2"])
        self.assertTrue(all(row["acceptance"] for row in data["numbered"]))
        self.assertTrue(all(row["sourceLineSha256"] for row in data["unnumbered"]))
        self.assertEqual(hashlib.sha256((ROOT / "docs/evidence/stabilization/g2-spec-contract-inventory.json").read_bytes()).hexdigest(),
                         matrix["specContractInventory"]["sha256"])

    def test_unknown_or_duplicate_requirement_is_rejected(self):
        source = {"specRequirements": [
            {"id": "SYN-001", "requirement": "x", "phase": "P1", "acceptance": "a",
             "source": "docs/spec/04-x.md", "line": 1, "sourceLineSha256": "0" * 64}]}
        matrix = {"rules": []}
        data = contracts.build(source, matrix)
        broken = json.loads(json.dumps(data)); broken["numbered"].append(dict(broken["numbered"][0]))
        with self.assertRaisesRegex(ValueError, "coverage differs"):
            contracts.validate(broken, source)


if __name__ == "__main__":
    unittest.main()
