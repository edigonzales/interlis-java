#!/usr/bin/env python3
"""Checks the prepared normative decision packet against the frozen expectations."""
from __future__ import annotations

import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_normative_decisions as decisions

ROOT = Path(__file__).resolve().parents[2]


class NormativeDecisionTest(unittest.TestCase):
    def test_packet_covers_all_cases_without_changing_expectations(self):
        expectations = json.loads((ROOT / "docs/evidence/conformance/normative-expectations.json").read_text())
        packet = json.loads((ROOT / "docs/evidence/stabilization/g2-normative-decision-packet-2026-10-28.json").read_text())
        matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
        self.assertEqual(18, len(packet["cases"]))
        self.assertEqual("PENDING_USER_REVALIDATION", packet["status"])
        by_id = {case["testId"]: case for case in expectations["cases"]}
        for case in packet["cases"]:
            original = by_id[case["testId"]]
            self.assertEqual(original["ownExpectation"], case["ownExpectation"])
            self.assertEqual(original["referenceExpectation"], case["referenceExpectation"])
            self.assertEqual("PENDING_USER_REVALIDATION", case["userDecision"])
            self.assertEqual({"2.3", "2.4"}, {item["document"] for item in case["normEvidence"]})
            self.assertTrue(all(paragraph["quoteSha256"] == decisions.digest(paragraph["quote"])
                                for item in case["normEvidence"] for paragraph in item["paragraphs"]))
        self.assertEqual(hashlib.sha256((ROOT / "docs/evidence/stabilization/g2-normative-decision-packet-2026-10-28.json").read_bytes()).hexdigest(),
                         matrix["normativeDecisionPacket"]["sha256"])

    def test_header_cleanup_keeps_body_text(self):
        text = "INTERLIS 2 - Referenzhandbuch\nSeite 23\n2.2.2 Namen\nEin Name ist definiert."
        self.assertIn("Ein Name ist definiert.", decisions.display_quote(text))
        self.assertNotIn("Seite 23", decisions.display_quote(text))


if __name__ == "__main__":
    unittest.main()
