import hashlib
import copy
import json
import tempfile
from pathlib import Path
import unittest

import g2_inventory as inventory
import g2_predefined


class InventoryTest(unittest.TestCase):
    def test_nested_choices_and_quantifiers_are_not_hidden_in_parent_rule(self):
        source = "type : A? (B | C (D | E)+)* F;\n"
        [rule] = inventory.grammar_inventory(source, "parser.g4", False)
        self.assertEqual("B3", rule["syntaxStep"])
        self.assertEqual("C3", rule["semanticStep"])
        occurrences = rule["occurrences"]
        self.assertEqual(5, sum(row["kind"] == "ALTERNATIVE" for row in occurrences))
        self.assertEqual({"A?", "(B | C (D | E)+)*", "(D | E)+"},
                         {row["text"] for row in occurrences if row["kind"] != "ALTERNATIVE"})
        ids = [row["id"] for row in occurrences]
        self.assertEqual(len(ids), len(set(ids)))
        for row in occurrences:
            self.assertEqual(source[row["start"]:row["end"]], row["text"])
            self.assertEqual(hashlib.sha256(row["text"].encode()).hexdigest(), row["sha256"])

    def test_literals_sets_comments_and_commands_do_not_create_fake_branches(self):
        source = r"""// Fake : A | B;
/* type : FAKE; */
STRING : '//' ( ~["\\\r\n] | '\\"' | '\\\\' )* '|' ';' -> channel(HIDDEN);
BlockComment : '/*' (BlockComment | .)*? '*/' -> channel(HIDDEN);
"""
        rules = inventory.grammar_inventory(source, "lexer.g4", True)
        self.assertEqual(["STRING", "BlockComment"], [row["production"] for row in rules])
        self.assertEqual(4, sum(o["kind"] == "ALTERNATIVE" for o in rules[0]["occurrences"]))
        self.assertFalse(any("HIDDEN" in o["text"] for row in rules for o in row["occurrences"]))
        [repetition] = [o for o in rules[1]["occurrences"] if o["kind"] == "ZERO_OR_MORE"]
        self.assertTrue(repetition["nongreedy"])
        self.assertFalse(any(o["kind"] == "OPTIONAL" for o in rules[1]["occurrences"]))

    def test_empty_alternative_keeps_its_zero_width_source_anchor(self):
        source = "type : A | ;"
        [rule] = inventory.grammar_inventory(source, "parser.g4", False)
        [empty] = [o for o in rule["occurrences"] if o["text"] == ""]
        self.assertEqual(empty["start"], empty["end"])
        self.assertEqual(source.index("|") + 1, empty["start"])

    def test_negation_and_nongreedy_suffix_belong_to_the_repeated_atom(self):
        [rule] = inventory.grammar_inventory("Comment : '!!' ~[\\r\\n]*?;", "lexer.g4", True)
        [repeat] = [o for o in rule["occurrences"] if o["kind"] == "ZERO_OR_MORE"]
        self.assertEqual("~[\\r\\n]*?", repeat["text"])
        self.assertTrue(repeat["nongreedy"])

    def test_unknown_production_and_malformed_grammar_fail_discovery(self):
        for source in ["unknown : A;", "type : (A;", "type : A);", "type : A", "type : /* missing", "type : 'missing;", "type : [missing;"]:
            with self.subTest(source=source), self.assertRaises(ValueError):
                inventory.grammar_inventory(source, "parser.g4", False)

    def test_original_requirement_ids_and_acceptance_are_preserved(self):
        source = """| ID | Normative Anforderung | Phase | Abnahme |
| SYN-003 | Nur 2.3 und 2.4. | P1 | Andere Version diagnostiziert. |
| ADR-001 | Architekturentscheidung | angenommen | Quelle |
| text | SYN-004 | kein normativer Vertrag | x |
"""
        [row] = inventory.spec_requirements(source, "04.md")
        self.assertEqual("SYN-003", row["id"])
        self.assertEqual("Andere Version diagnostiziert.", row["acceptance"])
        self.assertEqual(2, row["line"])

    def test_current_source_inventory_has_no_implicit_semantic_ownership(self):
        data = inventory.scan(inventory.ROOT)
        parser = [row for row in data["grammars"] if row["id"].startswith("SYN:")]
        self.assertEqual(set(inventory.RULE_STEPS), {row["production"] for row in parser})
        self.assertEqual("MISSING_EVIDENCE", data["acceptance"])
        self.assertTrue(data["remainingA2"])
        self.assertEqual(21, len(data["standardFunctions"]))
        self.assertEqual("MISSING_EVIDENCE", data["otherPredefinedContents"]["independentAcceptance"])
        self.assertEqual(289, len(data["specRequirements"]))
        for rule in data["grammars"]:
            self.assertTrue(rule["occurrences"])
            for occurrence in rule["occurrences"]:
                self.assertNotIn("VERIFIED", occurrence.values())

    def test_toc_is_only_a_locator_including_wrapped_appendices(self):
        pages = ["Cover", "3 Beschreibungssprache .......... 3\n3.1 Syntax .......... 3\nAnhang A (normativ) – Internes\n Modell .......... 4", "3 Beschreibungssprache", "Appendix"]
        result = inventory.toc_sections(pages)
        self.assertEqual(["3", "3.1", "APPENDIX:A"], [row["section"] for row in result])
        self.assertEqual(4, result[-1]["page"])
        self.assertFalse(any("state" in row or "acceptance" in row for row in result))
        with self.assertRaises(ValueError):
            inventory.toc_sections(["Cover", "3.1 Missing .... 99"])
        with self.assertRaises(ValueError):
            inventory.toc_sections(["Cover", "3.1 Double .... 2\n3.1 Double .... 2"])

    def test_exact_norm_excerpts_cannot_move_change_or_use_the_other_document(self):
        text = "Header. An exact norm condition. Footer."
        quote = "An exact norm condition."
        start = text.index(quote)
        ref = dict(document="2.3", page=1, extractedPageSha256=inventory.digest(text),
                   quote=quote, quoteSha256=inventory.digest(quote), excerptStart=start,
                   excerptEnd=start + len(quote))
        clause = dict(id="NORM:test", norm={"2.3": ref})
        self.assertEqual(["NORM:test"], inventory.check_norm_excerpts([text], "2.3", [clause]))
        for field, value in [("document", "2.4"), ("page", 2), ("excerptStart", start + 1),
                             ("quote", "A weaker condition."), ("quoteSha256", "0" * 64),
                             ("extractedPageSha256", "0" * 64)]:
            changed = copy.deepcopy(clause)
            changed["norm"]["2.3"][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                inventory.check_norm_excerpts([text], "2.3", [changed])

    def test_versioned_inventory_cannot_silently_omit_or_reorder_current_sources(self):
        path = inventory.ROOT / "docs/evidence/stabilization/g2-source-inventory.json"
        frozen = json.loads(path.read_text())
        actual = inventory.scan(inventory.ROOT)
        for key in ["pins", "grammars", "specRequirements", "standardFunctions", "legacyRules",
                    "atomicRuleMappings", "candidateTestLinks", "candidateTestSourceHashes", "observedAssertionLinks", "otherPredefinedContents"]:
            with self.subTest(key=key):
                self.assertEqual(actual[key], frozen[key])
        matrix = json.loads((inventory.ROOT / inventory.MATRIX).read_text())
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), matrix["sourceInventory"]["sha256"])
        for document in frozen["normDocuments"]:
            version = document["document"]
            self.assertEqual(matrix["documents"][version]["sha256"], document["sha256"])
            clauses = matrix["atomicRules"] + g2_predefined.norm_references(inventory.ROOT)
            self.assertEqual([row["id"] for row in clauses if version in row["norm"]],
                             document["checkedExcerptIds"])
        self.assertEqual("MISSING_EVIDENCE", frozen["acceptance"])

    def test_review_counterprobe_for_changed_steps_weaker_assertions_and_fake_tests(self):
        matrix = json.loads((inventory.ROOT / inventory.MATRIX).read_text())
        requirements = inventory.scan(inventory.ROOT)["specRequirements"]
        expected = inventory.matrix_mappings(inventory.ROOT, matrix, requirements)
        changed = copy.deepcopy(matrix)
        changed["rules"][0]["step"] = "H3"
        with self.assertRaisesRegex(ValueError, "fixed assignment"):
            inventory.matrix_mappings(inventory.ROOT, changed, requirements)
        changed = copy.deepcopy(matrix)
        changed["rules"][0]["requirements"] = ["TEST-001"]
        self.assertNotEqual(expected, inventory.matrix_mappings(inventory.ROOT, changed, requirements))
        for field, value in [("step", "H3"), ("requirements", ["TEST-001"]),
                             ("acceptance", dict(positive="weakened", negative="weakened", modelOrQuery="weakened"))]:
            changed = copy.deepcopy(matrix)
            changed["atomicRules"][0][field] = value
            with self.subTest(field=field):
                self.assertNotEqual(expected, inventory.matrix_mappings(inventory.ROOT, changed, requirements))
        for link in ["NonexistentTest.weakened", "LexicalProfileContractTest.noSuchMethod"]:
            changed = copy.deepcopy(matrix)
            changed["atomicRules"][0]["candidateTests"] = [link]
            with self.subTest(link=link), self.assertRaisesRegex(ValueError, "candidate"):
                inventory.matrix_mappings(inventory.ROOT, changed, requirements)
        changed = copy.deepcopy(matrix)
        changed["atomicRules"][0]["requirements"] = ["UNKNOWN-999"]
        with self.assertRaisesRegex(ValueError, "Incomplete atomic"):
            inventory.matrix_mappings(inventory.ROOT, changed, requirements)

    def test_partial_assertion_evidence_rejects_invented_fragments_and_wrong_run_metadata(self):
        matrix = json.loads((inventory.ROOT / inventory.MATRIX).read_text())
        requirements = inventory.scan(inventory.ROOT)["specRequirements"]
        row_index = next(i for i, row in enumerate(matrix["atomicRules"]) if row.get("observedAssertions"))
        for key, value in [("assertionFragments", ["assertAnInventedCondition()"]),
                           ("methodSha256", "0" * 64), ("source", "../outside.java"),
                           ("jvmXmlSha256", "0" * 64), ("extent", "COMPLETE"),
                           ("jvmXml", "../outside.xml"), ("jvmSuite", "wrong.Suite")]:
            changed = copy.deepcopy(matrix)
            changed["atomicRules"][row_index]["observedAssertions"][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                inventory.matrix_mappings(inventory.ROOT, changed, requirements)

    def test_review_requires_real_matching_unskipped_raw_xml(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "build/results/TEST-example.xml"
            path.parent.mkdir(parents=True)
            xml = '<testsuite name="test.Example" failures="0" errors="0" skipped="0"><testcase classname="test.Example" name="sample()"/></testsuite>'
            path.write_text(xml)
            evidence = {"candidateTest": "Example.sample", "jvmSuite": "test.Example", "jvmXml": "build/results/TEST-example.xml",
                        "jvmXmlSha256": hashlib.sha256(path.read_bytes()).hexdigest()}
            matrix = {"atomicRules": [{"id": "NORM:test", "observedAssertions": [evidence]}]}
            self.assertEqual("PARTIAL_TESTCASE_PASSED", inventory.check_raw_assertion_evidence(root, matrix)[0]["status"])
            path.unlink()
            with self.assertRaisesRegex(ValueError, "Missing raw"):
                inventory.check_raw_assertion_evidence(root, matrix)
            path.write_text(xml.replace("sample()", "other()"))
            with self.assertRaisesRegex(ValueError, "Changed raw"):
                inventory.check_raw_assertion_evidence(root, matrix)
            for bad in ['<testsuite name="test.Example" failures="0" errors="0" skipped="0"><testcase classname="test.Example" name="sample()"><failure/></testcase></testsuite>',
                        '<testsuite name="test.Example" failures="0" errors="0" skipped="1"><testcase classname="test.Example" name="sample()"><skipped/></testcase></testsuite>',
                        '<testsuite name="test.Example" failures="0" errors="0" skipped="0"><testcase classname="test.Example" name="other()"/></testsuite>',
                        '<testsuite name="other.Example" failures="0" errors="0" skipped="0"><testcase classname="other.Example" name="sample()"/></testsuite>',
                        '<testsuite name="test.Example" failures="0" errors="0" skipped="0"><testcase classname="wrong.Example" name="sample()"/></testsuite>']:
                path.write_text(bad)
                evidence["jvmXmlSha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                with self.subTest(bad=bad), self.assertRaisesRegex(ValueError, "Unsuccessful or missing"):
                    inventory.check_raw_assertion_evidence(root, matrix)


if __name__ == "__main__":
    unittest.main()
