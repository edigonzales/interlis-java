package guru.interlis.cli.evidence;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.cli.evidence.EvidenceFiles.*;

class CatalogueEvidenceTest {
    @Test void pinnedSourceAndAtomicMappingsMatchCurrentSources() throws Exception {
        var matrix = data("rule-matrix"); CatalogueEvidence.matrixPins(matrix); CatalogueEvidence.sources(data("source-inventory"), matrix);
    }
    @Test void allGrammarOccurrencesRemainOwnedAndUnaccepted() throws Exception {
        var grammar = data("grammar-inventory"); CatalogueEvidence.grammar(grammar, data("source-inventory"));
        assertEquals(1225, rows(grammar,"occurrences").size()); assertEquals(321, rows(data("source-inventory"),"grammars").size());
    }
    @Test void allSpecRequirementsAndExplicitLinesStayBound() throws Exception {
        var specs = data("spec-contract-inventory"); CatalogueEvidence.specifications(specs, data("source-inventory"));
        assertEquals(289, rows(specs,"numbered").size()); assertEquals(12, rows(specs,"unnumbered").size());
    }
    @Test void allBuiltinSignaturesAndPendingDecisionsStayBound() throws Exception {
        var builtins = data("builtin-inventory"); CatalogueEvidence.builtins(builtins, data("source-inventory"));
        assertEquals(21, rows(builtins,"functions").size());
        assertEquals(2, rows(builtins,"functions").stream().filter(f -> s(f,"acceptance").equals("USER_DECISION_PENDING")).count());
    }
    @Test void predefinedDeclarationsSpecialTypesAndVersionProfilesStayBound() throws Exception {
        var catalogue = json("testdata/g2/predefined-catalogue.json"); CatalogueEvidence.predefined(catalogue, data("source-inventory"), data("rule-matrix"));
        assertEquals(130, rows(catalogue,"declarations").size()); assertEquals(21, rows(catalogue,"specialValuesAndTypes").size());
    }
    @Test void everyRecordedCandidateStillNamesTheExactTestMethod() throws Exception { CatalogueEvidence.candidates(data("test-candidate-index")); }
    @Test void a3JvmAndPortablePairBindingsRemainPresent() throws Exception { CatalogueEvidence.pairs(data("a3-semantic-pairs")); }
    @Test void all19DecisionCasesKeepExpectationsQuotesAndExplicitUserStatus() throws Exception {
        var packet = data("normative-decision-packet-2026-10-28"); CatalogueEvidence.decisions(packet, data("norm-inventory")); assertEquals(19, rows(packet,"cases").size());
    }
    @ParameterizedTest @ValueSource(strings={"id", "acceptance", "ownerStep", "missing"})
    void foreignOrAcceptedGrammarOccurrenceFails(String key) throws Exception {
        var changed = data("grammar-inventory"); if (key.equals("missing")) changed.getAsJsonArray("occurrences").remove(0);
        else rows(changed,"occurrences").getFirst().addProperty(key,"broken");
        assertThrows(Exception.class, () -> CatalogueEvidence.grammar(changed,data("source-inventory")));
    }
    @ParameterizedTest @ValueSource(strings={"duplicate", "missing", "sourceLineSha256", "status", "requirement"})
    void unknownDuplicateOrChangedSpecContractFails(String key) throws Exception {
        var changed=data("spec-contract-inventory"); var row=rows(changed,"numbered").getFirst();
        if(key.equals("duplicate")) changed.getAsJsonArray("numbered").add(row.deepCopy());
        else if(key.equals("missing")) changed.getAsJsonArray("numbered").remove(0); else row.addProperty(key,"broken");
        assertThrows(Exception.class, () -> CatalogueEvidence.specifications(changed,data("source-inventory")));
    }
    @ParameterizedTest @ValueSource(strings={"result", "arguments", "since", "rule", "ownerStep", "acceptance", "missing"})
    void changedOrMissingBuiltinSignatureFails(String key) throws Exception {
        var changed=data("builtin-inventory"); var row=rows(changed,"functions").getFirst();
        if(key.equals("missing")) changed.getAsJsonArray("functions").remove(0);
        else if(key.equals("arguments")) row.add("arguments",new JsonArray()); else row.addProperty(key,"broken");
        assertThrows(Exception.class, () -> CatalogueEvidence.builtins(changed,data("source-inventory")));
    }
    @ParameterizedTest @ValueSource(strings={"missing", "duplicate", "step", "anchor", "independentAcceptance", "weaker"})
    void lostWeakenedOrChangedPredefinedMappingFails(String key) throws Exception {
        var changed=json("testdata/g2/predefined-catalogue.json"); var row=rows(changed,"declarations").getFirst();
        switch(key) {
            case "missing" -> changed.getAsJsonArray("declarations").remove(0);
            case "duplicate" -> changed.getAsJsonArray("declarations").add(row.deepCopy());
            case "anchor" -> rows(row,"sourceAnchors").getFirst().addProperty("sha256","0".repeat(64));
            case "weaker" -> row.getAsJsonObject("acceptance").addProperty("negative","weakened");
            default -> row.addProperty(key,"broken");
        }
        assertThrows(Exception.class, () -> CatalogueEvidence.predefined(changed,data("source-inventory"),data("rule-matrix")));
    }
    @ParameterizedTest @ValueSource(strings={"languages", "ruleReferences", "languageScopeMeaning", "actualSignatureUses", "actualSignatureVersions", "usageState", "pendingUserDecision", "assertionsConditionalOnUserDecision"})
    void wrongSpecialTypeScopeOrObjectUseFails(String key) throws Exception {
        var changed=json("testdata/g2/predefined-catalogue.json");
        var row=rows(changed,"specialValuesAndTypes").stream().filter(r -> s(r,"id").equals("BUILTIN_VALUE:OBJECT")).findFirst().orElseThrow();
        if(key.equals("assertionsConditionalOnUserDecision")) row.addProperty(key,false);
        else if(java.util.Set.of("languages","ruleReferences","actualSignatureUses","actualSignatureVersions").contains(key)) {JsonArray a=new JsonArray();a.add("broken");row.add(key,a);}
        else row.addProperty(key,"broken");
        assertThrows(Exception.class, () -> CatalogueEvidence.predefined(changed,data("source-inventory"),data("rule-matrix")));
    }
    @ParameterizedTest @ValueSource(strings={"word", "context", "feature", "duplicate"})
    void keywordContextAndFeatureChangesFail(String key) throws Exception {
        String original=text(PROFILE); String changed=switch(key) {
            case "word" -> original.replace("ABSTRACT ACCORDING","ABSTRACT NEWWORD ACCORDING");
            case "context" -> original.replace("next==InterlisLexer.MODEL","next==InterlisLexer.STRING");
            case "feature" -> original.replace("TEMPORAL_TYPES(\"DATE/TIMEOFDAY/DATETIME\")","TEMPORAL_TYPES(\"changed\")");
            default -> original.replace("ABSTRACT ACCORDING","ABSTRACT ABSTRACT ACCORDING");
        };
        assertNotEquals(original,changed);
        assertThrows(Exception.class, () -> CatalogueEvidence.languageProfile(changed,json("testdata/g2/predefined-catalogue.json"),data("source-inventory").getAsJsonObject("otherPredefinedContents").getAsJsonObject("lexicalProfile")));
    }
    @Test void multilineBuiltinSyntaxKeepsVersionRuleAndArgumentPositions() {
        var signatures=CatalogueEvidence.signatures("signature(\"len\", Value.NUMERIC, Value.TEXT), new Signature(\"areAreas2\", Value.BOOLEAN, List.of(Value.OBJECTS, Value.TEXT, Value.TEXT),\n LanguageVersion.ILI24, Rule.AREAS_TEXT)");
        assertEquals("ILI24",signatures.getLast().since()); assertEquals("AREAS_TEXT",signatures.getLast().rule()); assertEquals("OBJECTS",signatures.getLast().arguments().getFirst());
        assertThrows(Exception.class, () -> CatalogueEvidence.signatures("new Signature(\"unknown\", unsupported)"));
    }
    @ParameterizedTest @ValueSource(strings={"method", "methodSha256", "suite", "status"})
    void inventedChangedOrAcceptedTestCandidateFails(String key) throws Exception {
        var changed=data("test-candidate-index"); rows(rows(changed,"conditions").getFirst(),"candidates").getFirst().addProperty(key,"broken");
        assertThrows(Exception.class, () -> CatalogueEvidence.candidates(changed));
    }
    @ParameterizedTest @ValueSource(strings={"methodSha256", "markers", "missing"})
    void brokenA3PairFails(String key) throws Exception {
        var changed=data("a3-semantic-pairs"); var pair=rows(changed,"pairs").getFirst();
        if(key.equals("missing")) changed.getAsJsonArray("pairs").remove(0);
        else if(key.equals("markers")) {JsonArray a=new JsonArray();a.add("invented marker");pair.getAsJsonObject("portable").add("markers",a);}
        else pair.getAsJsonObject("jvm").addProperty(key,"0".repeat(64));
        assertThrows(Exception.class, () -> CatalogueEvidence.pairs(changed));
    }
    @ParameterizedTest @ValueSource(strings={"ownExpectation", "userDecision", "quote", "quoteSha256", "missing"})
    void manipulatedDecisionPacketFails(String key) throws Exception {
        var changed=data("normative-decision-packet-2026-10-28"); var row=rows(changed,"cases").getFirst();
        if(key.equals("missing")) changed.getAsJsonArray("cases").remove(0);
        else if(key.startsWith("quote")) rows(rows(row,"normEvidence").getFirst(),"paragraphs").getFirst().addProperty(key,"broken");
        else row.addProperty(key,"broken");
        assertThrows(Exception.class, () -> CatalogueEvidence.decisions(changed,data("norm-inventory")));
    }
}
