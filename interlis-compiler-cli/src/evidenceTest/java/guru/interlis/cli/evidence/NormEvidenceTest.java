package guru.interlis.cli.evidence;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.cli.evidence.EvidenceFiles.*;

class NormEvidenceTest {
    @Test void bothOriginalPdfsAllPagesParagraphsConditionsAndExactExcerptsStayBound() throws Exception {
        var matrix = data("rule-matrix"); var source = data("source-inventory"); var inventory = data("norm-inventory");
        var cross = data("norm-crosswalk"); var conditions = data("norm-conditions"); var catalogue = json("testdata/g2/predefined-catalogue.json");
        assertEquals(Set.of("2.3", "2.4"), ids(rows(inventory, "documents"), "document"));
        int paragraphs = 0, sections = 0, compiler = 0;
        for (int i = 0; i < 2; i++) {
            var recorded = rows(source, "normDocuments").get(i); String version = s(recorded, "document");
            var pages = NormEvidence.pages(version, matrix.getAsJsonObject("documents").getAsJsonObject(version), recorded);
            var doc = rows(inventory, "documents").get(i); var cw = rows(cross, "documents").get(i);
            NormEvidence.paragraphs(doc, pages); NormEvidence.crosswalk(cw, recorded);
            NormEvidence.conditions(rows(conditions, "documents").get(i), doc, cw);
            List<String> checked = new ArrayList<>();
            List<JsonObject> refs = new ArrayList<>(rows(matrix, "atomicRules")); refs.addAll(rows(catalogue, "declarations"));
            for (var row : refs) if (row.getAsJsonObject("norm").has(version)) {
                NormEvidence.excerpt(version, pages, row.getAsJsonObject("norm").getAsJsonObject(version)); checked.add(s(row, "id"));
            }
            for (var row : rows(catalogue, "declarations")) if (row.has("alternativeNormEvidence")) {
                var alternatives = rows(row, "alternativeNormEvidence");
                for (int n = 0; n < alternatives.size(); n++) if (s(alternatives.get(n), "document").equals(version)) {
                    NormEvidence.excerpt(version, pages, alternatives.get(n)); checked.add(s(row, "id") + ":alternative:" + n);
                }
            }
            var excerpts = rows(catalogue.getAsJsonObject("lexicalNormTables").getAsJsonObject(version), "excerpts");
            for (int n = 0; n < excerpts.size(); n++) { NormEvidence.excerpt(version, pages, excerpts.get(n)); checked.add("RESERVED_TABLE:" + version + ":" + n); }
            assertEquals(strings(recorded.get("checkedExcerptIds")), checked);
            paragraphs += rows(doc, "entries").size(); sections += rows(cw, "sections").size();
            compiler += (int) rows(rows(conditions, "documents").get(i), "conditions").stream().filter(c -> s(c, "kind").equals("COMPILER_RULE")).count();
        }
        assertEquals(2721, paragraphs); assertEquals(311, sections); assertEquals(369, compiler);
    }
    @ParameterizedTest @ValueSource(strings={"document", "page", "excerptStart", "quote", "quoteSha256", "extractedPageSha256"})
    void movedWeakenedOrWrongVersionExcerptFails(String key) {
        String page = "😀 Exact norm. Footer."; String quote = "Exact norm.";
        JsonObject ref = new JsonObject(); ref.addProperty("document", "2.3"); ref.addProperty("page", 1);
        ref.addProperty("excerptStart", 2); ref.addProperty("excerptEnd", 13); ref.addProperty("quote", quote);
        ref.addProperty("quoteSha256", hash(quote)); ref.addProperty("extractedPageSha256", hash(page));
        NormEvidence.excerpt("2.3", List.of(page), ref);
        if (key.equals("page") || key.equals("excerptStart")) ref.addProperty(key, 2); else ref.addProperty(key, "broken");
        if (key.equals("excerptStart")) ref.addProperty(key, 3);
        assertThrows(Exception.class, () -> NormEvidence.excerpt("2.3", List.of(page), ref));
    }
    @ParameterizedTest @ValueSource(strings={"quote", "quoteSha256", "pageSha256", "document", "start", "acceptance", "atomization", "missing", "duplicate", "unclassified"})
    void changedParagraphMissingCoverageAndFalseAcceptanceFail(String key) throws Exception {
        var inventory = rows(data("norm-inventory"), "documents").getFirst().deepCopy();
        var matrix = data("rule-matrix"); var recorded = rows(data("source-inventory"), "normDocuments").getFirst();
        var pages = NormEvidence.pages("2.3", matrix.getAsJsonObject("documents").getAsJsonObject("2.3"), recorded);
        var row = rows(inventory, "entries").getLast();
        switch (key) {
            case "missing" -> inventory.getAsJsonArray("entries").remove(inventory.getAsJsonArray("entries").size()-1);
            case "duplicate" -> inventory.getAsJsonArray("entries").add(row.deepCopy());
            case "start" -> row.addProperty("start", -1);
            case "unclassified" -> { row.addProperty("classification", "UNCLASSIFIED"); row.addProperty("ownerStep", "B3"); }
            default -> row.addProperty(key, "broken");
        }
        assertThrows(Exception.class, () -> NormEvidence.paragraphs(inventory, pages));
    }
    @ParameterizedTest @ValueSource(strings={"missing", "duplicate", "ownerStep", "atomization", "acceptance"})
    void invalidCrosswalkFails(String key) throws Exception {
        var doc = rows(data("norm-crosswalk"), "documents").getFirst().deepCopy();
        var row = rows(doc, "sections").stream().filter(r -> s(r,"disposition").equals("COMPILER_RULE")).findFirst().orElseThrow();
        if (key.equals("missing")) doc.getAsJsonArray("sections").remove(0);
        else if (key.equals("duplicate")) doc.getAsJsonArray("sections").add(row.deepCopy()); else row.addProperty(key,"broken");
        assertThrows(Exception.class, () -> NormEvidence.crosswalk(doc, rows(data("source-inventory"), "normDocuments").getFirst()));
    }
    @ParameterizedTest @ValueSource(strings={"missing", "ownerStep", "case", "duplicateParagraph", "quoteSha256", "acceptance", "kind", "atomization"})
    void incompleteOrAcceptedConditionFails(String key) throws Exception {
        var doc = rows(data("norm-conditions"), "documents").getFirst().deepCopy();
        var row = rows(doc,"conditions").stream().filter(r -> s(r,"kind").equals("COMPILER_RULE")).findFirst().orElseThrow();
        switch(key) {
            case "missing" -> { var a = doc.getAsJsonArray("conditions"); for (int i = 0; i < a.size(); i++) if (a.get(i)==row) { a.remove(i); break; } }
            case "case" -> rows(row,"cases").getFirst().addProperty("status", "PASSED");
            case "duplicateParagraph" -> row.getAsJsonArray("paragraphIds").add(row.getAsJsonArray("paragraphIds").get(0));
            case "acceptance" -> row.addProperty(key,"VERIFIED");
            default -> row.addProperty(key,"broken");
        }
        assertThrows(Exception.class, () -> NormEvidence.conditions(doc, rows(data("norm-inventory"),"documents").getFirst(), rows(data("norm-crosswalk"),"documents").getFirst()));
    }
    @Test void wrongPdfHashVersionAndExtractorAreRejected() throws Exception {
        var document = data("rule-matrix").getAsJsonObject("documents").getAsJsonObject("2.3").deepCopy();
        var recorded = rows(data("source-inventory"),"normDocuments").getFirst().deepCopy();
        document.addProperty("sha256", "0".repeat(64));
        assertThrows(Exception.class, () -> NormEvidence.pages("2.3", document, recorded));
        var original = data("rule-matrix").getAsJsonObject("documents").getAsJsonObject("2.3");
        recorded.addProperty("extractor","PDFBox");
        assertThrows(Exception.class, () -> NormEvidence.pages("2.3", original, recorded));
        assertThrows(Exception.class, () -> NormEvidence.pages("2.4", original, recorded));
    }
}
