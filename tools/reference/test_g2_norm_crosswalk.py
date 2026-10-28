#!/usr/bin/env python3
"""Failure-path tests for the G2 A2-R4 section crosswalk."""
from __future__ import annotations

import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_norm_crosswalk as crosswalk


class CrosswalkTest(unittest.TestCase):
    def setUp(self):
        self.sections = [
            {"section": "1", "title": "Grundprinzipien", "page": 9},
            {"section": "2.2.2", "title": "Namen", "page": 23},
            {"section": "2.8.5", "title": "Numerische Datentypen", "page": 41},
            {"section": "3.2.1", "title": "Ableitbarkeit aus dem Datenmodell", "page": 76},
            {"section": "APPENDIX:A", "title": "Das interne Datenmodell", "page": 94},
            {"section": "APPENDIX:K", "title": "Glossar", "page": 166},
        ]

    def test_language_and_appendix_owners_are_explicit(self):
        rows = crosswalk.build(self.sections, "2.3")
        by_id = {row["section"]: row for row in rows}
        self.assertEqual(("COMPILER_RULE", "B3"), (by_id["2.2.2"]["disposition"], by_id["2.2.2"]["ownerStep"]))
        self.assertEqual(("COMPILER_RULE", "C3"), (by_id["2.8.5"]["disposition"], by_id["2.8.5"]["ownerStep"]))
        self.assertEqual(("COMPILER_RULE", "B2"), (by_id["3.2.1"]["disposition"], by_id["3.2.1"]["ownerStep"]))
        self.assertEqual(("COMPILER_RULE", "B1"), (by_id["APPENDIX:A"]["disposition"], by_id["APPENDIX:A"]["ownerStep"]))
        self.assertEqual("EXPLANATORY", by_id["APPENDIX:K"]["disposition"])
        self.assertEqual({"sections": 6, "auditRequired": 0, "compilerRules": 4},
                         crosswalk.validate(rows, self.sections, "2.3"))

    def test_missing_or_duplicate_section_is_rejected(self):
        rows = crosswalk.build(self.sections, "2.3")
        with self.assertRaisesRegex(ValueError, "cover every section"):
            crosswalk.validate(rows[:-1], self.sections, "2.3")
        broken = [dict(row) for row in rows]
        broken[-1]["id"] = broken[0]["id"]
        with self.assertRaisesRegex(ValueError, "section ids differ"):
            crosswalk.validate(broken, self.sections, "2.3")

    def test_section_row_cannot_claim_atomization_or_acceptance(self):
        rows = crosswalk.build(self.sections, "2.3")
        for field, value in [("atomization", "ATOMIZED"), ("acceptance", "PASSED")]:
            broken = [dict(row) for row in rows]
            broken[0][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "claims atomization/acceptance"):
                crosswalk.validate(broken, self.sections, "2.3")

    def test_apply_marks_section_level_and_rejects_atomization(self):
        rows = crosswalk.build(self.sections, "2.3")
        inventory = {"documents": [{"document": "2.3", "validation": {"atomization": "PENDING"},
                                    "entries": [
                                        {"id": "NORM:2.3:2.2.2:p23:001", "section": "2.2.2"},
                                        {"id": "NORM:2.3:UNSECTIONED:p1:001", "section": "UNSECTIONED"},
                                    ]}]}
        applied = {"documents": [crosswalk.apply_crosswalk(inventory["documents"][0], rows)]}
        first, second = applied["documents"][0]["entries"]
        self.assertEqual("COMPILER_RULE", first["classification"])
        self.assertEqual("B3", first["ownerStep"])
        self.assertEqual("SECTION_LEVEL_PENDING", first["acceptance"])
        self.assertEqual("EXPLANATORY", second["classification"])
        self.assertEqual("NOT_APPLICABLE", second["acceptance"])
        self.assertEqual("SECTION_LEVEL_MAPPING_NOT_ACCEPTED", crosswalk.validate_classified(applied)["acceptance"])
        broken = {"documents": [{"document": "2.3", "validation": {"atomization": "PENDING"},
                                 "entries": [{"id": "NORM:2.3:2.2.2:p23:001", "section": "2.2.2",
                                              "classification": "COMPILER_RULE", "ownerStep": "B3", "phase": "G2",
                                              "acceptance": "SECTION_LEVEL_PENDING", "atomization": "ATOMIZED"}]}]}
        with self.assertRaisesRegex(ValueError, "claims atomization"):
            crosswalk.validate_classified(broken)

    def test_versioned_artifacts_are_complete_section_level_worklists(self):
        root = Path(__file__).resolve().parents[2]
        inventory_path = root / "docs/evidence/stabilization/g2-norm-inventory.json"
        crosswalk_path = root / "docs/evidence/stabilization/g2-norm-crosswalk.json"
        matrix_path = root / "docs/evidence/stabilization/g2-rule-matrix.json"
        inventory = json.loads(inventory_path.read_text())
        crosswalk_data = json.loads(crosswalk_path.read_text())
        matrix = json.loads(matrix_path.read_text())
        self.assertEqual("SECTION_LEVEL_MAPPING_NOT_ACCEPTED", crosswalk.validate_classified(inventory)["acceptance"])
        self.assertEqual(311, sum(len(document["sections"]) for document in crosswalk_data["documents"]))
        self.assertEqual(2721, sum(len(document["entries"]) for document in inventory["documents"]))
        entries = [entry for document in inventory["documents"] for entry in document["entries"]]
        self.assertEqual(0, sum(1 for entry in entries if entry["classification"] == "UNCLASSIFIED"))
        self.assertEqual(0, sum(1 for entry in entries if entry["atomization"] != "SECTION_LEVEL"))
        self.assertEqual(0, sum(1 for entry in entries if entry["acceptance"] in {"PASSED", "VERIFIED", "ATOMIZED"}))
        self.assertEqual(hashlib.sha256(inventory_path.read_bytes()).hexdigest(), matrix["normInventory"]["sha256"])
        self.assertEqual(hashlib.sha256(crosswalk_path.read_bytes()).hexdigest(), matrix["normInventory"]["crosswalkSha256"])

    def test_invalid_compiler_step_is_rejected(self):
        rows = crosswalk.build(self.sections, "2.3")
        broken = [dict(row) for row in rows]
        target = next(row for row in broken if row["section"] == "2.2.2")
        target["ownerStep"] = "Z9"
        with self.assertRaisesRegex(ValueError, "Invalid compiler step"):
            crosswalk.validate(broken, self.sections, "2.3")


if __name__ == "__main__":
    unittest.main()
