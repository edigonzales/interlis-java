#!/usr/bin/env python3
"""Failure-path tests for the read-only G2 norm paragraph inventory."""
from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import g2_norm_inventory as inventory


class NormInventoryTest(unittest.TestCase):
    def setUp(self):
        self.pages = [
            "Header\r\n1.1 First section\r\nFirst paragraph with ä.\r\n\r\nSecond paragraph.",
            "Header\r\n1.2 Second section\r\nThird paragraph.",
        ]
        self.sections = [
            {"section": "1.1", "title": "First section", "page": 1},
            {"section": "1.2", "title": "Second section", "page": 2},
        ]

    def test_build_binds_exact_slices_hashes_and_sections(self):
        data = inventory.build_inventory(self.pages, self.sections, "2.3")
        self.assertEqual(2, data["pageCount"])
        self.assertEqual(2, data["sectionCount"])
        for entry in data["entries"]:
            page = self.pages[entry["page"] - 1]
            self.assertEqual(entry["quote"], page[entry["start"]:entry["end"]])
            self.assertEqual(inventory.digest(entry["quote"]), entry["quoteSha256"])
            self.assertEqual(inventory.digest(page), entry["pageSha256"])
            self.assertEqual("UNCLASSIFIED", entry["classification"])
            self.assertIsNone(entry["ownerStep"])
        self.assertEqual(inventory.validate_inventory(data, self.pages)["unclassified"], len(data["entries"]))
        self.assertTrue(any(entry["section"] == "1.1" for entry in data["entries"]))
        self.assertTrue(any(entry["section"] == "1.2" for entry in data["entries"]))

    def test_changed_slice_or_hash_is_rejected(self):
        data = inventory.build_inventory(self.pages, self.sections, "2.3")
        broken = copy.deepcopy(data)
        broken["entries"][0]["quote"] += "x"
        with self.assertRaisesRegex(ValueError, "Changed paragraph slice"):
            inventory.validate_inventory(broken, self.pages)
        broken = copy.deepcopy(data)
        broken["entries"][0]["quoteSha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Changed paragraph hash"):
            inventory.validate_inventory(broken, self.pages)

    def test_unclassified_entry_cannot_carry_acceptance(self):
        data = inventory.build_inventory(self.pages, self.sections, "2.3")
        broken = copy.deepcopy(data)
        broken["entries"][0]["ownerStep"] = "B3"
        with self.assertRaisesRegex(ValueError, "Unclassified entry"):
            inventory.validate_inventory(broken, self.pages)

    def test_uncovered_non_whitespace_is_rejected(self):
        data = inventory.build_inventory(self.pages, self.sections, "2.3")
        broken = copy.deepcopy(data)
        # Remove the last block, leaving its page text uncovered.
        broken["entries"] = broken["entries"][:-1]
        with self.assertRaisesRegex(ValueError, "Uncovered page text"):
            inventory.validate_inventory(broken, self.pages)

    def test_section_heading_requires_exact_known_number(self):
        pages = ["Unknown heading 9.9\r\n1.1 First section\r\nText."]
        data = inventory.build_inventory(pages, self.sections, "2.3")
        self.assertFalse(any(entry["section"] == "9.9" for entry in data["entries"]))
        self.assertTrue(any(entry["section"] == "1.1" for entry in data["entries"]))


if __name__ == "__main__":
    unittest.main()
