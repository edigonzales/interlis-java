package guru.interlis.cli.evidence;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static guru.interlis.cli.evidence.EvidenceFiles.*;

/** Validates preserved pypdf text coordinates; does not extract or interpret the norm. */
final class NormEvidence {
    private NormEvidence() { }
    static List<String> pages(String version, JsonObject document, JsonObject recorded) throws IOException {
        require(Set.of("2.3", "2.4").contains(version), "Wrong norm version");
        pin("build/norms/ili" + version.replace(".", "") + ".pdf", s(document, "sha256"));
        Path path = inside(ROOT, BASE + "norm-text/ili" + version.replace(".", "") + "-pypdf-6.10.0.json.gz");
        JsonObject data;
        try (var reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8)) {
            data = JsonParser.parseReader(reader).getAsJsonObject();
        }
        require(s(data, "document").equals(version) && s(recorded, "document").equals(version)
                && s(data, "pdfSha256").equals(s(document, "sha256")) && s(recorded, "sha256").equals(s(document, "sha256"))
                && s(data, "extractor").equals("pypdf 6.10.0") && s(recorded, "extractor").equals(s(data, "extractor"))
                && s(data, "offsetUnit").equals("Unicode code points") && recorded.get("url").equals(document.get("url")), "Wrong PDF/extractor binding");
        var pages = strings(data.get("pages"));
        require(pages.size() == n(recorded, "pageCount") && pages.stream().noneMatch(String::isBlank)
                && pages.stream().map(EvidenceFiles::hash).toList().equals(strings(recorded.get("extractedPageHashes"))), "Changed extracted pages");
        return pages;
    }
    static void excerpt(String version, List<String> pages, JsonObject ref) {
        require(s(ref, "document").equals(version) && n(ref, "page") > 0 && n(ref, "page") <= pages.size(), "Wrong norm excerpt document/page");
        String page = pages.get(n(ref, "page") - 1), quote = s(ref, "quote");
        require(n(ref, "excerptStart") < n(ref, "excerptEnd")
                && slice(page, n(ref, "excerptStart"), n(ref, "excerptEnd")).equals(quote)
                && hash(page).equals(s(ref, "extractedPageSha256")) && hash(quote).equals(s(ref, "quoteSha256")), "Changed norm excerpt");
    }
    static void paragraphs(JsonObject document, List<String> pages) {
        var entries = rows(document, "entries");
        require(!entries.isEmpty() && n(document, "pageCount") == pages.size(), "Empty/wrong norm inventory");
        ids(entries, "id");
        List<BitSet> coverage = pages.stream().map(_ -> new BitSet()).toList();
        for (var entry : entries) {
            require(s(entry, "document").equals(s(document, "document")) && n(entry, "page") > 0 && n(entry, "page") <= pages.size(), "Wrong paragraph document/page");
            String page = pages.get(n(entry, "page") - 1);
            require(n(entry, "start") < n(entry, "end") && slice(page, n(entry, "start"), n(entry, "end")).equals(s(entry, "quote")), "Changed paragraph slice");
            require(hash(page).equals(s(entry, "pageSha256")) && hash(s(entry, "quote")).equals(s(entry, "quoteSha256")), "Changed paragraph hash");
            if (s(entry, "classification").equals("UNCLASSIFIED")) require(entry.get("ownerStep").isJsonNull()
                    && entry.getAsJsonArray("matrixRules").isEmpty() && s(entry, "acceptance").equals("MISSING_EVIDENCE"), "Unclassified entry carries acceptance");
            require(s(entry, "atomization").equals("SECTION_LEVEL") && !Set.of("PASSED", "VERIFIED", "ACCEPTED", "ATOMIZED").contains(s(entry, "acceptance")), "Paragraph claims acceptance/atomization");
            var states = Map.of("COMPILER_RULE", "SECTION_LEVEL_PENDING", "LATER_EXECUTION", "LATER_PHASE", "OUTSIDE_COMPILER", "OUTSIDE_COMPILER", "EXPLANATORY", "NOT_APPLICABLE");
            require(s(entry, "acceptance").equals(states.get(s(entry, "classification"))), "Acceptance differs from disposition");
            if (s(entry, "classification").equals("COMPILER_RULE")) require(STEPS.contains(s(entry, "ownerStep")) && s(entry, "phase").equals("G2"), "Invalid paragraph owner");
            else require(entry.get("ownerStep").isJsonNull(), "Non-compiler paragraph carries owner");
            coverage.get(n(entry, "page") - 1).set(n(entry, "start"), n(entry, "end"));
        }
        for (int i = 0; i < pages.size(); i++) {
            int[] points = pages.get(i).codePoints().toArray();
            for (int j = 0; j < points.length; j++) {
                // Python str.isspace includes Unicode separators and C0 information separators.
                boolean whitespace = Character.isWhitespace(points[j]) || Character.isSpaceChar(points[j]) || points[j] == 0x85;
                require(whitespace || coverage.get(i).get(j), "Uncovered page text: " + (i + 1) + ":" + j);
            }
        }
    }
    static void crosswalk(JsonObject cross, JsonObject original) {
        var sections = rows(cross, "sections"); var source = rows(original, "sections");
        require(ids(sections, "section").equals(ids(source, "section")) && sections.size() == source.size(), "Crosswalk must cover every section");
        ids(sections, "id");
        for (int i = 0; i < sections.size(); i++) {
            var row = sections.get(i); var ref = source.get(i);
            require(s(row, "id").equals(s(cross, "document") + ":" + s(row, "section")) && row.get("document").equals(cross.get("document"))
                    && row.get("page").equals(ref.get("page")) && row.get("title").equals(ref.get("title")), "Crosswalk section ids/locators differ");
            require(s(row, "atomization").equals("SECTION_LEVEL") && s(row, "acceptance").equals("MISSING_EVIDENCE"), "Crosswalk claims acceptance/atomization");
            require(Set.of("COMPILER_RULE", "EXPLANATORY", "LATER_EXECUTION", "OUTSIDE_COMPILER").contains(s(row, "disposition")), "Unknown section disposition");
            if (s(row, "disposition").equals("COMPILER_RULE")) require(STEPS.contains(s(row, "ownerStep")) && s(row, "phase").equals("G2"), "Invalid compiler step");
        }
    }
    static void conditions(JsonObject document, JsonObject inventory, JsonObject cross) {
        var conditions = rows(document, "conditions");
        ids(conditions, "id");
        Map<String, JsonObject> sections = new LinkedHashMap<>(), entries = new LinkedHashMap<>();
        rows(cross, "sections").forEach(r -> sections.put(s(r, "section"), r));
        rows(inventory, "entries").forEach(r -> entries.put(s(r, "id"), r));
        Set<String> covered = new HashSet<>(), coveredSections = new HashSet<>();
        for (var c : conditions) {
            JsonObject section = sections.get(s(c, "section"));
            require(section != null && c.get("document").equals(document.get("document")), "Unknown condition section/version");
            String kind = s(c, "kind"), acceptance = s(c, "acceptance");
            boolean fragment = kind.equals("EXPLANATORY") && c.has("fragmentReason") && !s(c, "fragmentReason").isBlank();
            boolean predefined = kind.equals("PREDEFINED_CONTENT") && !section.get("ownerStep").isJsonNull() && s(section, "ownerStep").equals("B1");
            require(kind.equals(s(section, "disposition")) || fragment || predefined, "Condition kind differs from crosswalk");
            require(s(c, "atomization").equals("PARAGRAPH_CLUSTER") || fragment || predefined && s(c, "atomization").equals("SOURCE_LISTING"), "Condition claims atomization");
            require(!Set.of("PASSED", "VERIFIED", "ACCEPTED").contains(acceptance), "Condition claims acceptance");
            if (s(c, "evidence").equals("PARTIAL_LINKED")) require(!rows(c, "linkedAtomicRules").isEmpty() && acceptance.equals("PARTIAL_LINKED_NOT_ACCEPTED"), "Partial link without evidence");
            if (s(c, "evidence").equals("PLANNED")) require(acceptance.equals("PLANNED_NOT_ACCEPTED"), "Planned evidence claims acceptance");
            if (kind.equals("COMPILER_RULE")) {
                require(STEPS.contains(s(c, "ownerStep")) && s(c, "phase").equals("G2"), "Invalid compiler condition owner");
                plannedCases(c, Set.of("POSITIVE", "NEGATIVE", "MODEL", "QUERY"));
            }
            if (predefined) require(s(c, "ownerStep").equals("B1") && s(c, "phase").equals("G2") && rows(c, "cases").isEmpty()
                    && acceptance.equals("CATALOGUE_PARTIAL_NOT_ACCEPTED"), "Invalid predefined condition");
            List<String> quotes = new ArrayList<>();
            var refs = strings(c.get("paragraphIds"));
            require((!kind.equals("COMPILER_RULE") || !refs.isEmpty()) && refs.size() == n(c, "paragraphCount"), "Missing condition paragraphs");
            for (String id : refs) {
                var paragraph = entries.get(id);
                require(paragraph != null && paragraph.get("section").equals(c.get("section")) && covered.add(id), "Missing/duplicate/wrong-section paragraph");
                quotes.add(s(paragraph, "quote"));
            }
            String quote = String.join("\n\n", quotes);
            require(quote.equals(s(c, "quote")) && hash(quote).equals(s(c, "quoteSha256")), "Changed condition quote/hash");
            coveredSections.add(s(c, "section"));
        }
        require(coveredSections.equals(sections.keySet()), "Missing condition section");
        for (var e : entries.values()) {
            var section = sections.get(s(e, "section"));
            if (section != null && s(section, "disposition").equals("COMPILER_RULE")) require(covered.contains(s(e, "id")), "Uncovered compiler paragraph");
        }
    }
    static void plannedCases(JsonObject row, Set<String> kinds) {
        var cases = rows(row, "cases");
        require(ids(cases, "kind").equals(kinds) && cases.size() == kinds.size()
                && cases.stream().allMatch(c -> s(c, "status").equals("PLANNED")), "Missing/duplicate/unplanned cases");
    }
}
