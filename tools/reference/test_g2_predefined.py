import copy
import json
import unittest

import g2_inventory
import g2_predefined as predefined


class PredefinedInventoryTest(unittest.TestCase):
    def setUp(self):
        self.root = g2_inventory.ROOT
        self.catalogue = json.loads((self.root / predefined.CATALOGUE).read_text())

    def test_every_declared_family_has_individual_members_versions_and_source(self):
        data = predefined.inventory(self.root)
        rows = {r["id"]: r for r in data["declarations"]}
        for name in ["INTERLIS", "INTERLIS.ANYUNIT", "INTERLIS.METER", "INTERLIS.NOOID",
                     "INTERLIS.METAOBJECT.Name", "INTERLIS.AXIS.Unit", "INTERLIS.TIMESYSTEMS.CALENDAR.Unit",
                     "INTERLIS.BaseTimeSystems.UTC", "INTERLIS.XMLDate", "INTERLIS.ArcSegment.ArcPoint",
                     "INTERLIS.SurfaceBoundary.Lines", "INTERLIS.LineGeometry.MANDATORY:StartSegment"]:
            self.assertIn("PREDEFINED:" + name, rows)
        self.assertEqual({"2.4"}, set(rows["PREDEFINED:INTERLIS.NOOID"]["norm"]))
        self.assertEqual({"2.3"}, set(rows["PREDEFINED:INTERLIS.SurfaceEdge.LineAttrs"]["norm"]))
        for row in rows.values():
            self.assertEqual("MISSING_EVIDENCE", row["independentAcceptance"])
            self.assertTrue(row["sourceAnchors"])
            self.assertEqual({"positive", "negative", "modelOrQuery"}, set(row["acceptance"]))
        self.assertIn("Only context-specific", rows["PREDEFINED:INTERLIS.LineGeometry"]["implementationObservation"])

    def test_real_keyword_tables_match_pinned_norm_tables_without_assuming_counts(self):
        data = predefined.inventory(self.root)
        lexical = data["lexicalProfile"]
        self.assertEqual({"2.3": dict(missingInSource=[], additionalInSource=[]),
                          "2.4": dict(missingInSource=[], additionalInSource=[])}, lexical["tableDifferences"])
        self.assertTrue(all(row["foundInPinnedNormTable"] for row in lexical["reservedWords"]))
        words = {(r["language"], r["word"]) for r in lexical["reservedWords"]}
        self.assertIn(("2.4", "CONTEXT"), words)
        self.assertNotIn(("2.3", "CONTEXT"), words)
        self.assertNotIn(("2.4", "XML"), words)
        self.assertIn("CONTEXTUAL:XML", {r["id"] for r in lexical["contextualTokens"]})

    def test_keyword_and_profile_changes_cannot_disappear_from_the_inventory(self):
        text = (self.root / predefined.PROFILE).read_text()
        baseline = predefined.language_profile(text)
        for altered in [text.replace("ABSTRACT ACCORDING", "ABSTRACT NEWWORD ACCORDING", 1),
                        text.replace("next==InterlisLexer.MODEL", "next==InterlisLexer.STRING", 1),
                        text.replace('TEMPORAL_TYPES("DATE/TIMEOFDAY/DATETIME")', 'TEMPORAL_TYPES("changed")')]:
            self.assertNotEqual(baseline, predefined.language_profile(altered))
        with self.assertRaisesRegex(ValueError, "Duplicate reserved"):
            predefined.language_profile(text.replace("ABSTRACT ACCORDING", "ABSTRACT ABSTRACT ACCORDING", 1))

    def test_catalogue_loss_weaker_assertion_or_changed_source_is_detectable(self):
        expected = predefined.inventory(self.root, self.catalogue)
        changed = copy.deepcopy(self.catalogue)
        changed["declarations"].pop()
        self.assertNotEqual(expected, predefined.inventory(self.root, changed))
        changed = copy.deepcopy(self.catalogue)
        changed["declarations"][0]["acceptance"]["negative"] = "weakened"
        self.assertNotEqual(expected, predefined.inventory(self.root, changed))
        changed = copy.deepcopy(self.catalogue)
        changed["declarations"][0]["sourceAnchors"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "source anchor"):
            predefined.inventory(self.root, changed)
        changed = copy.deepcopy(self.catalogue)
        changed["declarations"][0]["independentAcceptance"] = "VERIFIED"
        with self.assertRaisesRegex(ValueError, "Incomplete predefined"):
            predefined.inventory(self.root, changed)

    def test_no_duplicate_or_unassigned_special_declaration(self):
        for field in ["declarations", "specialValuesAndTypes"]:
            changed = copy.deepcopy(self.catalogue)
            changed[field].append(copy.deepcopy(changed[field][0]))
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "Duplicate"):
                predefined.inventory(self.root, changed)
        changed = copy.deepcopy(self.catalogue)
        changed["declarations"][0]["step"] = "P5"
        with self.assertRaisesRegex(ValueError, "Incomplete predefined"):
            predefined.inventory(self.root, changed)

    def test_norm_excerpt_mutation_uses_the_same_strict_pdf_checker(self):
        text = "A pinned norm statement."
        ref = dict(document="2.3", page=1, excerptStart=0, excerptEnd=len(text),
                   extractedPageSha256=g2_inventory.digest(text), quote=text, quoteSha256=g2_inventory.digest(text))
        clauses = [dict(id="PREDEFINED:test", norm={"2.3": ref})]
        self.assertEqual(["PREDEFINED:test"], g2_inventory.check_norm_excerpts([text], "2.3", clauses))
        clauses[0]["norm"]["2.3"]["quote"] = "weakened"
        with self.assertRaisesRegex(ValueError, "Changed norm excerpt"):
            g2_inventory.check_norm_excerpts([text], "2.3", clauses)

    def test_special_rows_have_explicit_version_and_existing_rule_scope(self):
        data = predefined.inventory(self.root)
        for row in data["specialValuesAndTypes"]:
            self.assertEqual(["2.3", "2.4"], row["languages"])
            self.assertTrue(row["ruleReferences"])
            self.assertTrue(row["languageScopeMeaning"])
        for field, value in [("languages", []), ("languages", ["2.4"]), ("ruleReferences", []),
                             ("ruleReferences", ["SYN:noSuchRule"]), ("languageScopeMeaning", "")]:
            changed = copy.deepcopy(self.catalogue)
            changed["specialValuesAndTypes"][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                predefined.inventory(self.root, changed)

    def test_unused_object_is_explicit_and_its_future_assertions_are_conditional(self):
        data = predefined.inventory(self.root)
        rows = {row["id"]: row for row in data["specialValuesAndTypes"]}
        obj = rows["BUILTIN_VALUE:OBJECT"]
        self.assertEqual([], obj["actualSignatureUses"])
        self.assertEqual([], obj["actualSignatureVersions"])
        self.assertEqual("NO_CURRENT_SIGNATURE_USES", obj["usageState"])
        self.assertTrue(obj["assertionsConditionalOnUserDecision"])
        self.assertEqual("BUILTIN_AREAS2_AREAS3_NORM_PROFILE", obj["pendingUserDecision"])
        self.assertEqual(["2.4"], rows["BUILTIN_VALUE:MULTIPOLYLINE"]["actualSignatureVersions"])
        objects = rows["BUILTIN_VALUE:OBJECTS"]
        self.assertTrue(any(use["function"] == "INTERLIS.areAreas2" for use in objects["actualSignatureUses"]))
        for field, value in [("actualSignatureUses", objects["actualSignatureUses"]),
                             ("actualSignatureVersions", ["2.3", "2.4"]),
                             ("usageState", "USED_IN_SIGNATURES"),
                             ("pendingUserDecision", "decided"), ("assertionsConditionalOnUserDecision", False)]:
            changed = copy.deepcopy(self.catalogue)
            next(row for row in changed["specialValuesAndTypes"] if row["id"] == obj["id"])[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                predefined.inventory(self.root, changed)
        declaration = next(row for row in data["declarations"] if row["path"] == "INTERLIS.areAreas2")
        self.assertIn("OBJECT OF", declaration["norm"]["2.4"]["quote"])
        self.assertIn("OBJECTS OF", declaration["alternativeNormEvidence"][0]["quote"])

    def test_signature_discovery_records_argument_positions_results_and_versions(self):
        text = '''signature("a", Value.NUMERIC, Value.TEXT),
                  new Signature("b", Value.BOOLEAN, List.of(Value.OBJECTS), LanguageVersion.ILI24, Rule.SIMPLE)'''
        uses = predefined.signature_uses(text)
        self.assertEqual([dict(function="INTERLIS.a", position="result", languages=["2.3", "2.4"])], uses["NUMERIC"])
        self.assertEqual([dict(function="INTERLIS.b", position="argument:0", languages=["2.4"])], uses["OBJECTS"])
        self.assertNotIn("OBJECT", uses)
        with self.assertRaisesRegex(ValueError, "Unrecorded builtin"):
            predefined.signature_uses(text + '\nnew Signature("unparsed", somethingDifferent)')


if __name__ == "__main__":
    unittest.main()
