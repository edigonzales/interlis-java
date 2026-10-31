package guru.interlis.cli.conformance;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Test-only assessment of explicit exceptions; suite execution/classification belongs to the external runner. */
public final class NormativeConformance {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<String> STATUSES = List.of("PASS", "XFAIL", "FAIL", "XPASS", "ERROR");
    record ProcessResult(int exitCode, String stdout, String stderr) { }
    @FunctionalInterface interface CliCheck { ProcessResult run(List<String> command) throws Exception; }
    private NormativeConformance() { }

    static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
    static void write(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(value) + "\n");
    }
    static String hash(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int size;
            while ((size = input.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    static String text(JsonObject value, String key) { return value.get(key).getAsString(); }
    static JsonArray array(JsonObject value, String key) { return value.getAsJsonArray(key); }
    static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }
    static Map<JsonElement, Integer> multiset(JsonArray values) {
        var counts = new HashMap<JsonElement, Integer>();
        values.forEach(value -> counts.merge(value, 1, Integer::sum));
        return counts;
    }

    static JsonArray signatures(JsonObject payload, List<Path> files, List<String> inputs) {
        require(files.size() == inputs.size(), "diagnostic source count mismatch");
        var sources = new HashMap<URI, String>();
        for (int i = 0; i < files.size(); i++) sources.put(files.get(i).toAbsolutePath().toUri(), inputs.get(i));
        var result = new JsonArray();
        for (var element : array(payload, "diagnostics")) {
            var diagnostic = element.getAsJsonObject();
            require(text(diagnostic, "severity").equals("ERROR")
                    && Set.of("SEMANTIC_CHECKING", "TYPE_CHECKING").contains(text(diagnostic, "phase"))
                    && text(diagnostic, "code").startsWith("ILI-SEM-"), "non-semantic diagnostic or warning");
            var range = diagnostic.getAsJsonObject("range").deepCopy();
            String source = sources.get(URI.create(text(range, "source")));
            require(source != null, "unknown diagnostic source");
            range.addProperty("source", source);
            var signature = new JsonObject();
            for (String key : List.of("code", "severity", "phase", "messageKey", "arguments")) {
                signature.add(key, Objects.requireNonNull(diagnostic.get(key), key).deepCopy());
            }
            signature.add("range", range);
            result.add(signature);
        }
        return result;
    }

    static JsonObject checkProcess(ProcessResult process, boolean rejected) {
        require(process.exitCode() == (rejected ? 1 : 0), "unexpected process exit " + process.exitCode());
        require(process.stderr().isEmpty(), "unexpected process stderr");
        var payload = JsonParser.parseString(process.stdout()).getAsJsonObject();
        if (rejected) {
            require(text(payload, "status").startsWith("rejected: RejectionSummary[reasons=[SOURCE_ERRORS],")
                    && text(payload, "status").contains("unsupportedFeatureIds=[]")
                    && !array(payload, "diagnostics").isEmpty(), "expected source rejection without unsupported features");
        } else {
            require(payload.equals(JsonParser.parseString("{\"status\":\"ok\",\"diagnostics\":[]}")),
                    "positive control must succeed without diagnostics");
        }
        return payload;
    }

    private static List<Path> controls(Path root, JsonArray sources) throws Exception {
        require(sources != null && !sources.isEmpty(), "control missing");
        var paths = new ArrayList<Path>();
        for (var element : sources) {
            var source = element.getAsJsonObject();
            Path path = root.resolve(text(source, "path"));
            require(path.toRealPath().startsWith(root.toRealPath()) && hash(path).equals(text(source, "sha256")),
                    "control source pin mismatch");
            paths.add(path);
        }
        return paths;
    }
    private static List<String> sourceNames(JsonArray sources) {
        return sources.asList().stream().map(v -> text(v.getAsJsonObject(), "path")).toList();
    }
    private static void check(JsonObject evidence, String name, Path cli, List<Path> paths,
                              List<String> inputs, boolean rejected, JsonArray expected, CliCheck run) throws Exception {
        var command = new ArrayList<>(List.of(cli.toString(), "check", "--json"));
        paths.forEach(path -> command.add(path.toString()));
        var result = run.run(List.copyOf(command));
        var process = new JsonObject();
        process.add("command", JSON.toJsonTree(command));
        process.addProperty("exitCode", result.exitCode());
        process.addProperty("stdout", result.stdout());
        process.addProperty("stderr", result.stderr());
        evidence.add(name, process);
        var payload = checkProcess(result, rejected);
        if (rejected) require(multiset(signatures(payload, paths, inputs)).equals(multiset(expected)),
                "semantic diagnostics differ from the pinned expectation");
    }

    static JsonObject evaluate(Path root, Path cli, JsonObject entry, JsonObject test, CliCheck run) {
        var evidence = new JsonObject();
        for (String key : List.of("testId", "actualOutcome")) evidence.add(key, test.get(key));
        evidence.add("referenceExpectation", test.get("expectation"));
        evidence.add("referenceClassification", test.get("classification"));
        evidence.add("ownExpectation", entry.get("ownExpectation"));
        evidence.add("norm", entry.get("norm"));
        if (entry.has("compatibilityDecision")) evidence.add("compatibilityDecision", entry.get("compatibilityDecision").deepCopy());
        evidence.addProperty("result", "FAIL");
        try {
            boolean rejected = text(entry, "ownExpectation").equals("rejected");
            boolean compatibility = entry.has("compatibilityDecision");
            if (compatibility) {
                var decision = entry.getAsJsonObject("compatibilityDecision");
                require(text(decision,"id").equals("LEGACY_OID_23_WITHOUT_CONTRACTED")
                        && text(decision,"userDecision").equals("APPROVED_USER")
                        && text(entry,"languageVersion").equals("2.3") && text(entry,"ownExpectation").equals("accepted"), "unapproved compatibility case");
            }
            var expected = new JsonObject(); expected.addProperty("outcome", rejected || compatibility ? "accept" : "reject");
            require(Set.of("accepted", "rejected").contains(text(entry, "ownExpectation"))
                    && entry.get("referenceExpectation").equals(expected) && test.get("expectation").equals(expected)
                    && text(test, "languageVersion").equals(text(entry, "languageVersion"))
                    && !entry.getAsJsonObject("norm").isEmpty() && !test.get("timedOut").getAsBoolean()
                    && test.get("exitCode").getAsInt() == (rejected ? 1 : 0)
                    && test.get("actualOutcome").equals(entry.get("ownExpectation"))
                    && text(test, "classification").equals(compatibility ? "PASS" : "FAIL"), "reference result, language or expectation mismatch");
            var names = sourceNames(array(entry, "sources"));
            var command = strings(array(test, "command"));
            require(names.equals(strings(array(test, "inputs"))) && command.size() == names.size() + 2
                    && command.subList(0, 2).equals(List.of(cli.toString(), "check")), "source list or runner command mismatch");
            var originals = command.subList(2, command.size()).stream().map(Path::of).toList();
            for (int i = 0; i < originals.size(); i++) {
                require(originals.get(i).toString().replace('\\', '/').endsWith("/" + names.get(i))
                        && hash(originals.get(i)).equals(text(array(entry, "sources").get(i).getAsJsonObject(), "sha256")),
                        "original source pin mismatch");
            }
            var positives = controls(root, array(entry, "positiveControls"));
            List<Path> negatives = rejected ? List.of() : controls(root, array(entry, "negativeControls"));
            check(evidence, "original", cli, originals, names, rejected, array(entry, "allowedDiagnostics"), run);
            check(evidence, "positiveControl", cli, positives, sourceNames(array(entry, "positiveControls")), false, null, run);
            if (!rejected) check(evidence, "negativeControl", cli, negatives, sourceNames(array(entry, "negativeControls")),
                    true, array(entry, "allowedDiagnostics"), run);
            evidence.addProperty("result", "PASS");
        } catch (Exception failure) {
            evidence.addProperty("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        return evidence;
    }

    static JsonObject assess(Path root, Path cli, String commit, String archiveHash, Set<String> selected,
                              boolean full, JsonObject manifest, JsonObject report, JsonObject summary,
                              int runnerExit, CliCheck run) {
        require(manifest.get("schemaVersion").getAsInt() == 1 && text(manifest, "suiteCommit").equals(commit)
                && text(manifest, "suiteSha256").equals(archiveHash) && array(report, "compilers").size() == 1,
                "suite/manifest identity mismatch");
        var compiler = array(report, "compilers").get(0).getAsJsonObject();
        var cases = array(compiler, "tests");
        var seen = new HashSet<String>();
        var counts = new JsonObject(); STATUSES.forEach(status -> counts.addProperty(status, 0));
        for (var value : cases) {
            var test = value.getAsJsonObject();
            require(seen.add(text(test, "testId")), "duplicate suite result");
            String status = text(test, "classification");
            require(STATUSES.contains(status), "unknown classification");
            counts.addProperty(status, counts.get(status).getAsInt() + 1);
        }
        require(seen.equals(selected), "incomplete or unknown suite results");
        counts.addProperty("TOTAL", cases.size());
        boolean rawPass = List.of("FAIL", "ERROR", "XPASS").stream().allMatch(k -> counts.get(k).getAsInt() == 0);
        require(counts.equals(report.get("summary")) && counts.equals(summary.get("summary"))
                && counts.equals(compiler.get("summary")) && text(report, "overallResult").equals(rawPass ? "PASS" : "FAIL")
                && report.get("overallResult").equals(summary.get("overallResult")) && runnerExit == (rawPass ? 0 : 1),
                "runner exit, result and reports disagree");
        var entries = new LinkedHashMap<String, JsonObject>();
        for (var element : array(manifest, "cases")) {
            var entry = element.getAsJsonObject();
            require(entries.put(text(entry, "testId"), entry) == null, "duplicate normative expectation");
        }
        require(!full || selected.containsAll(entries.keySet()), "normative expectation missing from full suite");
        var assessments = new JsonArray();
        for (var value : cases) {
            var test = value.getAsJsonObject();
            var entry = entries.get(text(test, "testId"));
            if (entry != null) {
                var assessment = evaluate(root, cli, entry, test, run);
                assessments.add(assessment);
                if (text(assessment, "result").equals("PASS") && text(test,"classification").equals("FAIL")) {
                    counts.addProperty("FAIL", counts.get("FAIL").getAsInt() - 1);
                    counts.addProperty("XFAIL", counts.get("XFAIL").getAsInt() + 1);
                }
            }
        }
        boolean passed = List.of("FAIL", "ERROR", "XPASS").stream().allMatch(k -> counts.get(k).getAsInt() == 0)
                && assessments.asList().stream().allMatch(a -> text(a.getAsJsonObject(), "result").equals("PASS"));
        var result = new JsonObject();
        result.addProperty("overallResult", passed ? "PASS" : "FAIL");
        result.add("summary", counts);
        result.add("referenceSummary", summary.get("summary").deepCopy());
        result.add("referenceResult", report.get("overallResult"));
        result.add("normativeAssessments", assessments);
        result.add("assessmentCaseIds", JSON.toJsonTree(entries.keySet().stream().filter(selected::contains).toList()));
        result.addProperty("referenceReport", "build/conformance/report.json");
        return result;
    }

    static ProcessResult run(Path root, List<String> command) throws Exception {
        Path temporary = Files.createTempDirectory(root.resolve("build"), "normative-process-");
        try {
            Path stdout = temporary.resolve("stdout"), stderr = temporary.resolve("stderr");
            var process = new ProcessBuilder(command).directory(root.toFile())
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                throw new IOException("compiler process timed out");
            }
            return new ProcessResult(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            try (var files = Files.list(temporary)) { for (Path path : files.toList()) Files.deleteIfExists(path); }
            Files.deleteIfExists(temporary);
        }
    }

    static void requireFreshReports(Path output, long started) throws IOException {
        for (String name : List.of("summary.json", "report.json", "runner-exit.txt")) {
            require(Files.getLastModifiedTime(output.resolve(name)).toMillis() >= started, "stale suite report: " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path output = root.resolve("build/conformance");
        var pins = new Properties();
        try (var input = Files.newInputStream(root.resolve("gradle/conformance-suite.properties"))) { pins.load(input); }
        var config = read(output.resolve("run-config.json"));
        long started = Long.parseLong(Files.readString(output.resolve("run-start.txt")));
        requireFreshReports(output, started);
        var suiteCases = array(read(Path.of(text(config, "suite"))), "testCases");
        var pattern = Pattern.compile(text(config, "filter"));
        var selected = new LinkedHashSet<String>();
        var excludedVersions = new TreeSet<String>();
        int excluded = 0;
        for (var value : suiteCases) {
            var test = value.getAsJsonObject();
            if (pattern.matcher(text(test, "id")).find()) require(selected.add(text(test, "id")), "duplicate selected suite ID");
            if (!Set.of("2.3", "2.4").contains(text(test, "languageVersion"))) {
                excluded++; excludedVersions.add(text(test, "languageVersion"));
            }
        }
        boolean full = text(config, "filter").equals("^(ili23|ili24)\\.");
        require(!selected.isEmpty() && (!full || (suiteCases.size() == 610 && selected.size() == 593 && excluded == 17)),
                "unexpected selected suite case set");
        var manifest = read(root.resolve("docs/evidence/conformance/normative-expectations.json"));
        require(array(manifest, "cases").size() == 21, "expected exactly 21 explicit project assessments");
        Path cli = Path.of(text(array(config, "compilers").get(0).getAsJsonObject(), "source"));
        int exit = Integer.parseInt(Files.readString(output.resolve("runner-exit.txt")));
        var raw = read(output.resolve("summary.json"));
        var result = assess(root, cli, pins.getProperty("commit"), pins.getProperty("sha256"), selected, full,
                manifest, read(output.resolve("report.json")), raw, exit, command -> run(root, command));
        write(output.resolve("normative-assessment.json"), result);
        var combined = raw.deepCopy(); result.entrySet().forEach(e -> combined.add(e.getKey(), e.getValue()));
        var suite = new JsonObject();
        for (String key : List.of("commit", "sha256", "url")) suite.addProperty(key, pins.getProperty(key));
        combined.add("suite", suite); combined.addProperty("filter", text(config, "filter"));
        var selection = new JsonObject();
        selection.add("languageVersions", JSON.toJsonTree(List.of("2.3", "2.4")));
        selection.addProperty("suiteCaseCount", suiteCases.size()); selection.addProperty("selectedCaseCount", selected.size());
        selection.addProperty("excludedCaseCount", excluded); selection.add("excludedVersions", JSON.toJsonTree(excludedVersions));
        combined.add("selection", selection); combined.addProperty("exitCode", exit);
        combined.addProperty("cli", root.relativize(cli).toString().replace('\\', '/'));
        write(root.resolve("build/reports/conformance/conformance-summary.json"), combined);
        String log = Files.readString(output.resolve("conformance.log"), StandardCharsets.UTF_8)
                .replace(root.toString(), "<repo>").replace(System.getProperty("user.home"), "<home>");
        Files.writeString(root.resolve("build/reports/conformance/conformance-log.txt"), log.substring(Math.max(0, log.length() - 12000)) + "\n");
        System.out.println("selected=" + selected.size() + " result=" + text(result, "overallResult") + " rawExit=" + exit);
        System.out.println(result.get("summary"));
        if (!text(result, "overallResult").equals("PASS")) System.exit(1);
    }
}
