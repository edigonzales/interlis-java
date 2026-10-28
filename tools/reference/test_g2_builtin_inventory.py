#!/usr/bin/env python3
"""Checks the A2-R4 builtin worklist against the source inventory."""
from __future__ import annotations

import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_builtin_inventory as builtins

ROOT = Path(__file__).resolve().parents[2]


class BuiltinInventoryTest(unittest.TestCase):
    def test_versioned_worklist_covers_all_signatures_without_acceptance(self):
        source = json.loads((ROOT / "docs/evidence/stabilization/g2-source-inventory.json").read_text())
        data = json.loads((ROOT / "docs/evidence/stabilization/g2-builtin-inventory.json").read_text())
        matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
        validation = builtins.validate(data, source)
        self.assertEqual(21, validation["functions"])
        self.assertEqual(84, validation["plannedCases"])
        self.assertEqual(2, validation["userDecisions"])
        self.assertTrue(all(row["ownerStep"] == "E2" for row in data["functions"]))
        self.assertTrue(all(case["status"] == "PLANNED" for row in data["functions"] for case in row["cases"]))
        self.assertEqual(hashlib.sha256((ROOT / "docs/evidence/stabilization/g2-builtin-inventory.json").read_bytes()).hexdigest(),
                         matrix["builtinInventory"]["sha256"])

    def test_signature_parser_keeps_multiline_version_and_rule(self):
        text = '''private static Signature signature(String name, Value result, Value... args) { return null; }
        private static final List<Signature> SIGNATURES = List.of(
            signature("len", Value.NUMERIC, Value.TEXT),
            new Signature("areAreas2", Value.BOOLEAN, List.of(Value.OBJECTS, Value.TEXT, Value.TEXT),
                    LanguageVersion.ILI24, Rule.AREAS_TEXT));'''
        parsed = builtins.parse_signatures(text)
        self.assertEqual(["len", "areAreas2"], [row["name"] for row in parsed])
        self.assertEqual("ILI24", parsed[1]["since"])
        self.assertEqual("AREAS_TEXT", parsed[1]["rule"])


if __name__ == "__main__":
    unittest.main()
