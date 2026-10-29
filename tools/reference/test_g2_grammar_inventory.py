#!/usr/bin/env python3
"""Checks the grammar occurrence worklist against the source inventory."""
from __future__ import annotations

import copy
import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_grammar_inventory as grammar

ROOT = Path(__file__).resolve().parents[2]


class GrammarInventoryTest(unittest.TestCase):
    def test_versioned_worklist_covers_every_occurrence_and_claims_no_acceptance(self):
        source = json.loads((ROOT / "docs/evidence/stabilization/g2-source-inventory.json").read_text())
        data = json.loads((ROOT / "docs/evidence/stabilization/g2-grammar-inventory.json").read_text())
        matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
        validation = grammar.validate(data, source)
        self.assertEqual(321, validation["productions"])
        # L0A adds seven source occurrences in BlockComment's delimiter guards;
        # every old production remains present, with the same owner/acceptance.
        self.assertEqual(1225, validation["occurrences"])
        self.assertEqual(validation["occurrences"], matrix["grammarInventory"]["occurrences"])
        self.assertEqual(125, validation["parserProductions"])
        self.assertEqual(196, validation["lexerProductions"])
        self.assertTrue(all(occurrence["acceptance"] == "PLANNED_NOT_ACCEPTED" for occurrence in data["occurrences"]))
        self.assertTrue(all(occurrence["ownerStep"] in grammar.STEPS for occurrence in data["occurrences"]))
        self.assertEqual(hashlib.sha256((ROOT / "docs/evidence/stabilization/g2-grammar-inventory.json").read_bytes()).hexdigest(),
                         matrix["grammarInventory"]["sha256"])

    def test_occurrence_from_another_source_is_rejected(self):
        source = {"grammars": [{"production": "x", "source": "InterlisParser.g4", "line": 1,
                                 "occurrences": [{"id": "GRAMMAR:parser:x:root/alt1", "line": 1, "kind": "ALTERNATIVE",
                                                  "text": "x", "sha256": "0" * 64}]}]}
        matrix = {"rules": [{"id": "SYN:x", "step": "B3", "requirements": ["TEST-001"], "state": "OPEN"}]}
        data = grammar.build(source, matrix)
        broken = copy.deepcopy(data); broken["occurrences"][0]["id"] = "GRAMMAR:parser:y:root/alt1"
        with self.assertRaisesRegex(ValueError, "Occurrence ids differ"):
            grammar.validate(broken, source)
        broken = copy.deepcopy(data); broken["occurrences"][0]["acceptance"] = "VERIFIED"
        with self.assertRaisesRegex(ValueError, "claims acceptance"):
            grammar.validate(broken, source)


if __name__ == "__main__":
    unittest.main()
