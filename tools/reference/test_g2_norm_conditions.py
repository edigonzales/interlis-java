#!/usr/bin/env python3
"""Failure-path and completeness tests for G2 A2-R4 condition worklists."""
from __future__ import annotations

import copy
import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_norm_conditions as conditions

ROOT = Path(__file__).resolve().parents[2]


def inventory_fixture():
    return {"documents": [{"document": "2.3", "entries": [
        {"id": "NORM:2.3:2.2.2:p1:001", "section": "2.2.2", "page": 1, "start": 0, "end": 6, "quote": "Namen."},
        {"id": "NORM:2.3:2.2.2:p1:002", "section": "2.2.2", "page": 1, "start": 7, "end": 14, "quote": "Regeln."},
        {"id": "NORM:2.3:1:p1:001", "section": "1", "page": 1, "start": 15, "end": 22, "quote": "Einleitung."},
    ]}]}


def crosswalk_fixture():
    return {"documents": [{"document": "2.3", "sections": [
        {"section": "1", "title": "Einleitung", "page": 1, "disposition": "EXPLANATORY", "ownerStep": None, "phase": None, "reason": "intro"},
        {"section": "2.2.2", "title": "Namen", "page": 1, "disposition": "COMPILER_RULE", "ownerStep": "B3", "phase": "G2", "reason": "name rule"},
    ]}]}


class ConditionTest(unittest.TestCase):
    def test_compiler_sections_get_four_planned_cases_and_exact_clusters(self):
        data = conditions.build(inventory_fixture(), crosswalk_fixture())
        compiler = [c for c in data["documents"][0]["conditions"] if c["kind"] == "COMPILER_RULE"]
        self.assertEqual(1, len(compiler))
        self.assertEqual(["POSITIVE", "NEGATIVE", "MODEL", "QUERY"], [c["kind"] for c in compiler[0]["cases"]])
        self.assertTrue(all(c["status"] == "PLANNED" for c in compiler[0]["cases"]))
        self.assertEqual("Namen.\n\nRegeln.", compiler[0]["quote"])
        self.assertEqual(conditions.digest(compiler[0]["quote"]), compiler[0]["quoteSha256"])
        self.assertEqual({"2.3": {"conditions": 2, "compilerConditions": 1, "plannedCases": 4}},
                         conditions.validate(data, inventory_fixture(), crosswalk_fixture()))

    def test_uncovered_compiler_paragraph_and_wrong_owner_are_rejected(self):
        inv = inventory_fixture(); cw = crosswalk_fixture()
        data = conditions.build(inv, cw)
        broken = copy.deepcopy(data)
        broken["documents"][0]["conditions"] = [c for c in broken["documents"][0]["conditions"] if c["kind"] != "COMPILER_RULE"]
        with self.assertRaisesRegex(ValueError, "Uncovered compiler paragraphs"):
            conditions.validate(broken, inv, cw)
        broken = copy.deepcopy(data)
        compiler = next(c for c in broken["documents"][0]["conditions"] if c["kind"] == "COMPILER_RULE")
        compiler["ownerStep"] = "Z9"
        with self.assertRaisesRegex(ValueError, "Invalid condition owner"):
            conditions.validate(broken, inv, cw)

    def test_executed_or_duplicated_case_is_rejected(self):
        inv = inventory_fixture(); cw = crosswalk_fixture(); data = conditions.build(inv, cw)
        broken = copy.deepcopy(data)
        compiler = next(c for c in broken["documents"][0]["conditions"] if c["kind"] == "COMPILER_RULE")
        compiler["cases"][0]["status"] = "PASSED"
        with self.assertRaisesRegex(ValueError, "Case claims execution"):
            conditions.validate(broken, inv, cw)
        broken = copy.deepcopy(data)
        compiler = next(c for c in broken["documents"][0]["conditions"] if c["kind"] == "COMPILER_RULE")
        compiler["paragraphIds"] = [compiler["paragraphIds"][0], compiler["paragraphIds"][0]]
        with self.assertRaisesRegex(ValueError, "Duplicate paragraph"):
            conditions.validate(broken, inv, cw)

    def test_versioned_condition_worklist_is_complete_and_not_accepted(self):
        inventory = json.loads((ROOT / "docs/evidence/stabilization/g2-norm-inventory.json").read_text())
        crosswalk = json.loads((ROOT / "docs/evidence/stabilization/g2-norm-crosswalk.json").read_text())
        data = json.loads((ROOT / "docs/evidence/stabilization/g2-norm-conditions.json").read_text())
        validation = conditions.validate(data, inventory, crosswalk)
        self.assertEqual(369, sum(doc["compilerConditions"] for doc in validation.values()))
        self.assertEqual(1476, sum(doc["plannedCases"] for doc in validation.values()))
        self.assertEqual(777, sum(doc["conditions"] for doc in validation.values()))
        all_conditions = [c for doc in data["documents"] for c in doc["conditions"]]
        self.assertTrue(all(c["acceptance"] != "VERIFIED" for c in all_conditions))
        self.assertTrue(all(c["atomization"] == "PARAGRAPH_CLUSTER" or (c["kind"] == "EXPLANATORY" and c.get("fragmentReason")) or (c["kind"] == "PREDEFINED_CONTENT" and c["atomization"] == "SOURCE_LISTING") for c in all_conditions))
        matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
        self.assertEqual(hashlib.sha256((ROOT / "docs/evidence/stabilization/g2-norm-conditions.json").read_bytes()).hexdigest(),
                         matrix["normConditions"]["sha256"])


if __name__ == "__main__":
    unittest.main()
