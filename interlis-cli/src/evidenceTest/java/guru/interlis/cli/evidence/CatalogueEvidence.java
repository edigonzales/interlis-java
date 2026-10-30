package guru.interlis.cli.evidence;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static guru.interlis.cli.evidence.EvidenceFiles.*;

/** Checks existing, pinned worklists. No inventory generation, keyword ranking or semantic approval. */
final class CatalogueEvidence {
    private CatalogueEvidence() { }
    static void matrixPins(JsonObject matrix) throws IOException {
        // inventoryPins records the original A1 grammar, before subsequent grammar fixes.
        // The current source inventory and latestGrammarSha256 bind today's source.
        pin("interlis-compiler/src/main/antlr/InterlisParser.g4", s(matrix, "latestGrammarSha256"));
        for (String key : List.of("sourceInventory", "normInventory", "normConditions", "normativeDecisionPacket",
                "grammarInventory", "builtinInventory", "specContractInventory", "testCandidateIndex", "a3SemanticPairs")) {
            JsonObject row = matrix.getAsJsonObject(key);
            pin(BASE + s(row, "path"), s(row, "sha256"));
        }
        JsonObject norm = matrix.getAsJsonObject("normInventory");
        pin(BASE + s(norm, "crosswalk"), s(norm, "crosswalkSha256"));
        // The Markdown presentation acquired the active-plan notice after its recorded
        // historical hash. The unchanged JSON packet, exact quotes and expectations
        // are the active machine contract (as in the former Python tests).
        pin(BASE + s(matrix, "r3AssertionRun"), s(matrix, "r3AssertionRunSha256"));
    }
    static void sources(JsonObject source, JsonObject matrix) throws IOException {
        pins(source.getAsJsonObject("pins"));
        pins(source.getAsJsonObject("candidateTestSourceHashes"));
        require(s(source, "acceptance").equals("MISSING_EVIDENCE") && !source.getAsJsonArray("remainingA2").isEmpty(), "Source inventory claims completeness");
        var specs = rows(source, "specRequirements"); ids(specs, "id");
        List<JsonObject> actual = new ArrayList<>();
        try (var paths = Files.list(ROOT.resolve("docs/spec"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("(?!17-)[0-9]{2}-.*\\.md")).sorted().toList()) {
                var lines = Files.readAllLines(path);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i); String[] cells = line.strip().replaceAll("^\\||\\|$", "").split("\\|", -1);
                    if (cells.length != 4 || !cells[0].strip().matches("(?!ADR-)[A-Z]+-[0-9]{3}")) continue;
                    JsonObject row = new JsonObject();
                    for (int j = 0; j < 4; j++) row.addProperty(List.of("id", "requirement", "phase", "acceptance").get(j), cells[j].strip());
                    row.addProperty("source", ROOT.relativize(path).toString().replace('\\', '/')); row.addProperty("line", i + 1); row.addProperty("sourceLineSha256", hash(line)); actual.add(row);
                }
            }
        }
        require(actual.equals(specs), "Specification requirements differ from source");
        require(source.get("atomicRuleMappings").equals(matrix.get("atomicRules")), "Atomic matrix mapping changed");
        var legacy = rows(source, "legacyRules"); var rules = rows(matrix, "rules");
        require(legacy.size() == rules.size(), "Legacy matrix coverage changed");
        for (int i = 0; i < legacy.size(); i++) {
            var a = legacy.get(i); var b = rules.get(i);
            require(a.get("id").equals(b.get("id")) && a.get("step").equals(b.get("step"))
                    && a.get("historicalState").equals(b.get("state")) && a.get("independentAcceptance").equals(b.get("independentAcceptance"))
                    && s(a, "mappingSha256").equals(hash(canonicalJson(b))), "Changed historical rule/step/assertion mapping: " + s(a,"id"));
        }
        var requirements = ids(specs, "id"); ids(rows(matrix, "atomicRules"), "id");
        for (var row : rows(matrix, "atomicRules")) {
            require(STEPS.contains(s(row, "step")) && !strings(row.get("requirements")).isEmpty()
                    && requirements.containsAll(strings(row.get("requirements"))) && !row.getAsJsonObject("norm").isEmpty()
                    && Set.of("2.3", "2.4").containsAll(row.getAsJsonObject("norm").keySet()), "Incomplete atomic mapping");
            acceptance(row);
        }
        for (var link : rows(source, "candidateTestLinks")) {
            if (!link.has("link")) { require(s(link, "status").equals("MISSING_CASE_LINK"), "Hidden case-link gap"); continue; }
            String[] parts = s(link, "link").split("\\."); String full = text(s(link, "source"));
            require(s(link, "source").endsWith("/" + parts[0] + ".java"), "Invented candidate class");
            if (parts.length == 2) require(Pattern.compile("@Test\\s+(?:public\\s+)?void\\s+" + Pattern.quote(parts[1]) + "\\s*\\(").matcher(full).find(), "Invented candidate method");
        }
    }
    static void grammar(JsonObject data, JsonObject source) throws IOException {
        List<JsonObject> expected = new ArrayList<>();
        for (var production : rows(source, "grammars")) {
            String full = text(s(production, "source"));
            require(STEPS.contains(s(production, "semanticStep")) && s(production, "syntaxStep").equals("B3"), "Invalid grammar owner");
            for (var occurrence : rows(production, "occurrences")) {
                String slice = slice(full, n(occurrence, "start"), n(occurrence, "end"));
                require(slice.equals(s(occurrence, "text")) && hash(slice).equals(s(occurrence, "sha256")), "Changed grammar range/hash");
                expected.add(occurrence);
            }
        }
        var occurrences = rows(data, "occurrences"); ids(occurrences, "id");
        require(occurrences.size() == expected.size(), "Grammar occurrence coverage differs");
        for (int i = 0; i < occurrences.size(); i++) {
            var a = occurrences.get(i); var b = expected.get(i);
            for (String key : List.of("id", "kind", "text", "sha256", "line")) require(a.get(key).equals(b.get(key)), "Foreign/reordered grammar occurrence");
            require(s(a, "acceptance").equals("PLANNED_NOT_ACCEPTED") && STEPS.contains(s(a, "ownerStep")), "Grammar claims acceptance/invalid owner");
        }
    }
    static void specifications(JsonObject data, JsonObject source) throws IOException {
        var rows = rows(data, "numbered"); var original = rows(source, "specRequirements");
        require(ids(rows, "id").equals(ids(original, "id")) && rows.size() == original.size(), "Requirement coverage differs");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            // Historical line numbers predate L0B-1's documentation insertion. The exact
            // line hash and text, with the current locator in source-inventory, stay bound.
            for (String key : original.get(i).keySet()) if (!key.equals("line")) require(row.get(key).equals(original.get(i).get(key)), "Changed requirement contract");
            require(Set.of("MATRIX_LINKED", "G2_STEP_PLANNED", "OUTSIDE_G2").contains(s(row, "status")), "Invalid requirement status");
            if (!row.get("ownerStep").isJsonNull()) require(STEPS.contains(s(row, "ownerStep")), "Invalid requirement owner");
        }
        ids(rows(data, "unnumbered"), "id");
        for (var row : rows(data, "unnumbered")) {
            var lines = text(s(row, "source")).lines().toList();
            require(lines.stream().filter(line -> hash(line).equals(s(row, "sourceLineSha256"))).count() == 1
                    && s(row, "acceptance").equals("PLANNED_NOT_ACCEPTED"), "Changed unnumbered specification binding");
        }
    }
    record Signature(String name, String result, List<String> arguments, String since, String rule) { }
    static List<Signature> signatures(String text) {
        List<Signature> result = new ArrayList<>(); Map<Integer, Signature> ordered = new TreeMap<>();
        var simple = Pattern.compile("signature\\(\"(\\w+)\"\\s*,\\s*Value\\.(\\w+)((?:\\s*,\\s*Value\\.\\w+)*)\\)").matcher(text);
        while (simple.find()) ordered.put(simple.start(), new Signature(simple.group(1), simple.group(2), values(simple.group(3)), "ILI23", "SIMPLE"));
        var explicit = Pattern.compile("new Signature\\(\"(\\w+)\"\\s*,\\s*Value\\.(\\w+)\\s*,\\s*List\\.of\\((.*?)\\)\\s*,\\s*LanguageVersion\\.(ILI23|ILI24)\\s*,\\s*Rule\\.(\\w+)\\)", Pattern.DOTALL).matcher(text);
        while (explicit.find()) ordered.put(explicit.start(), new Signature(explicit.group(1), explicit.group(2), values(explicit.group(3)), explicit.group(4), explicit.group(5)));
        result.addAll(ordered.values());
        long declared = Pattern.compile("(?:signature|new Signature)\\(\"(\\w+)\"").matcher(text).results().count();
        require(result.size() == declared && !result.isEmpty() && result.stream().map(Signature::name).distinct().count() == declared, "Unrecorded/duplicate builtin signature syntax");
        return result;
    }
    private static List<String> values(String text) { return Pattern.compile("Value\\.(\\w+)").matcher(text).results().map(m -> m.group(1)).toList(); }
    static void builtins(JsonObject data, JsonObject source) throws IOException {
        pin(s(data, "source"), s(data, "sourceSha256"));
        var signatures = signatures(text(FUNCTIONS)); var functions = rows(data, "functions");
        require(functions.size() == signatures.size() && ids(functions, "id").equals(ids(rows(source, "standardFunctions"), "id")), "Builtin coverage differs");
        for (int i = 0; i < functions.size(); i++) {
            var f = functions.get(i); var signature = signatures.get(i);
            require(s(f, "name").equals(signature.name()) && s(f, "result").equals(signature.result()) && strings(f.get("arguments")).equals(signature.arguments())
                    && s(f, "since").equals(signature.since()) && s(f, "rule").equals(signature.rule()), "Changed builtin signature/version");
            require(s(f, "ownerStep").equals("E2") && f.get("matrixRule").equals(f.get("id")), "Invalid builtin owner");
            NormEvidence.plannedCases(f, Set.of("POSITIVE", "NEGATIVE", "CONTEXT", "UNDEFINED"));
            require(Set.of("PLANNED_NOT_ACCEPTED", "USER_DECISION_PENDING").contains(s(f, "acceptance")), "Builtin claims acceptance");
            if (s(f, "acceptance").equals("USER_DECISION_PENDING")) require(!f.get("pendingUserDecision").isJsonNull() && !s(f, "pendingUserDecision").isBlank(), "Decision without ID");
        }
    }
    static void predefined(JsonObject catalogue, JsonObject source, JsonObject matrix) throws IOException {
        var original = source.getAsJsonObject("otherPredefinedContents"); pins(original.getAsJsonObject("sourceHashes"));
        require(s(original, "independentAcceptance").equals("MISSING_EVIDENCE"), "Predefined evidence claims acceptance");
        var rules = ids(rows(matrix, "rules"), "id"); var signatures = signatures(text(FUNCTIONS));
        for (String section : List.of("declarations", "specialValuesAndTypes")) {
            var rows = rows(catalogue, section); ids(rows, "id");
            require(rows.size() == rows(original, section).size(), "Predefined catalogue coverage differs");
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i); var normalized = row.deepCopy();
                require(Set.of("B1", "B2", "B3", "E2").contains(s(row, "step")) && s(row, "independentAcceptance").equals("MISSING_EVIDENCE"), "Invalid predefined mapping");
                acceptance(row); require(!rows(row, "sourceAnchors").isEmpty(), "Missing predefined anchors");
                for (var ref : rows(normalized, "sourceAnchors")) {
                    anchor(ref); String before = slice(text(s(ref, "source")), 0, n(ref, "start"));
                    ref.addProperty("line", before.chars().filter(c -> c == '\n').count() + 1);
                }
                require(normalized.equals(rows(original, section).get(i)), "Changed/weakened predefined mapping");
                if (section.equals("declarations")) require(!row.getAsJsonObject("norm").isEmpty() && Set.of("2.3", "2.4").containsAll(row.getAsJsonObject("norm").keySet()), "Missing predefined version");
                else {
                    require(strings(row.get("languages")).equals(List.of("2.3", "2.4")) && !strings(row.get("ruleReferences")).isEmpty()
                            && rules.containsAll(strings(row.get("ruleReferences"))) && !s(row, "languageScopeMeaning").isBlank(), "Invalid special type scope");
                    if (s(row, "id").startsWith("BUILTIN_VALUE:")) signatureUses(row, signatures);
                }
            }
        }
        languageProfile(text(PROFILE), catalogue, original.getAsJsonObject("lexicalProfile"));
        for (var ref : rows(catalogue, "otherClassificationRules")) anchor(ref);
        sourceCallSites(original);
    }
    private static void sourceCallSites(JsonObject original) throws IOException {
        JsonArray calls = new JsonArray(); List<String> producers = new ArrayList<>();
        for (String module : List.of("interlis-compiler", "interlis-model")) try (var paths = Files.walk(ROOT.resolve(module + "/src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String source = ROOT.relativize(path).toString().replace('\\', '/'), text = Files.readString(path);
                var origins = Pattern.compile("new\\s+(?:guru\\.interlis\\.model\\.)?(?:Origin\\.)?PredefinedOrigin\\s*\\(").matcher(text);
                while (origins.find()) producers.add(source + ":" + (text.substring(0, origins.start()).chars().filter(c -> c == '\n').count() + 1));
                if (!module.equals("interlis-compiler") || source.equals(PROFILE)) continue;
                var refs = Pattern.compile("LanguageProfile\\.Feature\\.(\\w+)|LanguageProfile\\.(featureForToken|supports|classify|isIdentifier)\\(").matcher(text);
                while (refs.find()) {
                    JsonObject row = new JsonObject(); row.addProperty("source", source);
                    row.addProperty("line", text.substring(0, refs.start()).chars().filter(c -> c == '\n').count() + 1);
                    row.addProperty("text", refs.group()); row.add("feature", refs.group(1) == null ? JsonNull.INSTANCE : new JsonPrimitive(refs.group(1)));
                    row.add("operation", refs.group(2) == null ? JsonNull.INSTANCE : new JsonPrimitive(refs.group(2))); row.addProperty("step", "B3"); calls.add(row);
                }
            }
        }
        require(calls.equals(original.get("profileCallSites")), "Unrecorded language-profile call site");
        require(producers.equals(rows(original,"predefinedOriginProducers").stream().map(r -> s(r,"source") + ":" + n(r,"line")).toList()), "Unrecorded predefined-origin producer");
    }
    static void signatureUses(JsonObject row, List<Signature> signatures) {
        String value = s(row, "id").substring("BUILTIN_VALUE:".length()); JsonArray uses = new JsonArray(); Set<String> versions = new TreeSet<>();
        for (var signature : signatures) {
            List<String> languages = signature.since().equals("ILI23") ? List.of("2.3", "2.4") : List.of("2.4");
            List<String> types = new ArrayList<>(); types.add(signature.result()); types.addAll(signature.arguments());
            for (int i = 0; i < types.size(); i++) if (types.get(i).equals(value)) {
                JsonObject use = new JsonObject(); use.addProperty("function", "INTERLIS." + signature.name());
                use.addProperty("position", i == 0 ? "result" : "argument:" + (i - 1)); use.add("languages", new Gson().toJsonTree(languages)); uses.add(use); versions.addAll(languages);
            }
        }
        require(uses.equals(row.get("actualSignatureUses")) && strings(row.get("actualSignatureVersions")).equals(new ArrayList<>(versions))
                && s(row, "usageState").equals(uses.isEmpty() ? "NO_CURRENT_SIGNATURE_USES" : "USED_IN_SIGNATURES"), "Wrong signature uses/version/state");
        if (uses.isEmpty()) require(s(row, "pendingUserDecision").equals("BUILTIN_AREAS2_AREAS3_NORM_PROFILE") && row.get("assertionsConditionalOnUserDecision").getAsBoolean(), "Unused descriptor loses pending decision");
    }
    static void languageProfile(String text, JsonObject catalogue, JsonObject recorded) {
        var tables = Pattern.compile("RESERVED_(23|24)\\s*=\\s*words\\(\"\"\"\\s*(.*?)\\s*\"\"\"\\)", Pattern.DOTALL).matcher(text);
        Set<String> versions = new HashSet<>();
        while (tables.find()) {
            String version = "2." + tables.group(1).charAt(1); require(versions.add(version), "Duplicate reserved table");
            List<String> words = List.of(tables.group(2).split("\\s+"));
            require(new HashSet<>(words).size() == words.size(), "Duplicate reserved word");
            require(new HashSet<>(words).equals(new HashSet<>(strings(catalogue.getAsJsonObject("lexicalNormTables").getAsJsonObject(version).get("words")))), "Changed reserved-word norm table");
            for (var row : rows(recorded, "reservedWords")) if (s(row, "language").equals(version)) {
                require(words.contains(s(row, "word")) && hash(tables.group()).equals(s(row, "tableSha256")), "Changed reserved table binding");
            }
        }
        require(versions.equals(Set.of("2.3", "2.4")), "Missing reserved table");
        var contextual = Pattern.compile("boolean contextual=(.*?)if\\(contextual\\)type=", Pattern.DOTALL).matcher(text);
        require(contextual.find(), "Missing contextual classification");
        for (var row : rows(recorded, "contextualTokens")) require(s(row, "classificationSha256").equals(hash(contextual.group())), "Changed contextual classification");
        var features = Pattern.compile("([A-Z_]+)\\(\"([^\"]+)\"\\)").matcher(text); Map<String, String> labels = new HashMap<>();
        while (features.find()) labels.put("PROFILE:" + features.group(1), features.group(2));
        for (var row : rows(recorded, "features")) require(s(row, "label").equals(labels.get(s(row, "id")))
                && !row.getAsJsonObject("actualSupports").get("2.3").getAsBoolean() && row.getAsJsonObject("actualSupports").get("2.4").getAsBoolean(), "Changed language feature");
    }
    static void candidates(JsonObject data) throws IOException {
        require(s(data, "status").equals("CANDIDATE_ONLY"), "Candidate index claims acceptance");
        long methods = 0;
        try (var paths = Files.walk(ROOT.resolve("interlis-compiler/src/test/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) methods += Pattern.compile("@Test\\s+(?:public\\s+)?void\\s+\\w+\\s*\\(").matcher(Files.readString(path)).results().count();
        }
        require(methods == n(data,"testMethods"), "Changed test-method discovery count");
        for (String section : List.of("conditions", "requirements")) {
            var rows = rows(data, section); require(!rows.isEmpty(), "Empty candidate index"); ids(rows, "id");
            for (var row : rows) for (var candidate : rows(row, "candidates")) {
                require(s(candidate, "status").equals("CANDIDATE_ONLY"), "Candidate claims acceptance"); method(candidate, "suite", "method");
            }
        }
    }
    static void pairs(JsonObject data) throws IOException {
        require(s(data, "status").equals("COUNTERCHECK_PAIRS_MATCHED") && ids(rows(data, "pairs"), "id").equals(Set.of("lost-member-presence", "wrong-same-named-target", "swapped-correspondence-order")), "Missing/foreign A3 pair");
        for (var pair : rows(data, "pairs")) {
            method(pair.getAsJsonObject("jvm"), "suite", "method");
            var portable = pair.getAsJsonObject("portable"); String source = text(s(portable, "source"));
            var markers = strings(portable.get("markers")); require(!markers.isEmpty() && markers.stream().allMatch(source::contains) && s(pair, "status").equals("PASS_BOTH"), "Missing A3 portable marker");
        }
    }
    static void decisions(JsonObject packet, JsonObject inventory) throws IOException {
        var expected = json("docs/evidence/conformance/normative-expectations.json");
        // The frozen 19-case packet keeps its original byte pin. Current cases
        // must retain every original object exactly; only the two consequences
        // of the explicitly approved SET profile are additional assessments.
        String historical="docs/evidence/conformance/normative-expectations-before-set-context.json";
        pin(historical,s(packet,"sourceSha256"));
        decisionSources(packet,expected,json(historical));
        Map<String, JsonObject> paragraphs = new HashMap<>();
        rows(inventory, "documents").forEach(d -> rows(d, "entries").forEach(r -> paragraphs.put(s(r, "id"), r)));
        for (var c : rows(packet, "cases")) {
            var original = rows(expected, "cases").stream().filter(r -> r.get("testId").equals(c.get("testId"))).findFirst().orElseThrow();
            boolean approved = original.has("compatibilityDecision");
            require(c.get("ownExpectation").equals(original.get("ownExpectation")) && c.get("referenceExpectation").equals(original.get("referenceExpectation"))
                    && s(c, "userDecision").equals(approved ? "APPROVED_COMPATIBILITY_DEVIATION" : "PENDING_USER_REVALIDATION")
                    && (!approved || c.get("compatibilityDecision").equals(original.get("compatibilityDecision")))
                    && rows(c, "normEvidence").stream().map(r -> s(r,"document")).collect(java.util.stream.Collectors.toSet()).equals(Set.of("2.3", "2.4")), "Changed normative expectation/decision");
            for (var norm : rows(c, "normEvidence")) {
                // Empty locator-only entries are existing, unaccepted gaps. They do
                // not certify semantics; every paragraph actually cited stays exact.
                for (var p : rows(norm, "paragraphs")) {
                    var ref = paragraphs.get(s(p, "id")); require(ref != null && ref.get("document").equals(norm.get("document")) && ref.get("section").equals(norm.get("section"))
                            && ref.get("page").equals(p.get("page")) && ref.get("quote").equals(p.get("quote")) && hash(s(p, "quote")).equals(s(p, "quoteSha256")), "Changed decision norm quote/hash");
                }
            }
        }
    }

    static void decisionSources(JsonObject packet,JsonObject expected,JsonObject historical) {
        require(s(packet,"source").equals("docs/evidence/conformance/normative-expectations.json")
                &&s(packet,"status").equals("PENDING_USER_REVALIDATION")
                &&rows(historical,"cases").size()==19&&rows(expected,"cases").size()==21
                &&ids(rows(packet,"cases"),"testId").equals(ids(rows(historical,"cases"),"testId")),"Decision packet coverage/status differs");
        for(String key:List.of("schemaVersion","suiteCommit","suiteSha256"))require(expected.get(key).equals(historical.get(key)),"Suite identity changed");
        for(var original:rows(historical,"cases")) {
            var matches=rows(expected,"cases").stream().filter(c->c.get("testId").equals(original.get("testId"))).toList();
            require(matches.size()==1&&matches.getFirst().equals(original),"Historical assessment changed");
        }
        var oldIds=ids(rows(historical,"cases"),"testId");
        var additions=rows(expected,"cases").stream().filter(c->!oldIds.contains(s(c,"testId"))).toList();
        require(additions.size()==2&&ids(additions,"testId").equals(Set.of("ili23.Constraint23Test.setCstrt",
                "ili23.InterlisConversion23Test.iliconversion_ConversionOfSETCONSTRAINT")),"Unapproved additional assessment");
        for(var added:additions) {
            var authorization=added.getAsJsonObject("authorizationBasis");
            require(authorization!=null&&s(authorization,"kind").equals("APPROVED_SET_OBJECT_CONTEXT_PROFILE")
                    &&s(authorization,"userStatement").equals("Normkontext prüfen; Objektwerte nur im WHERE (empfohlen)")
                    &&s(added,"languageVersion").equals("2.3")&&s(added,"ownExpectation").equals("rejected")
                    &&s(added.getAsJsonObject("referenceExpectation"),"outcome").equals("accept")
                    &&s(added.getAsJsonObject("norm"),"sections").equals("2.12"),"SET profile authorization differs");
        }
    }
}
