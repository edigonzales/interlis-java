package guru.interlis.cli.conformance;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.cli.conformance.NormativeConformance.*;

class NormativeConformanceContractTest {
    @TempDir Path root;
    Path original, positive, cli;
    JsonObject payload, entry, test, report, summary, manifest, counts;
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static JsonArray list(JsonElement... elements) { var a = new JsonArray(); for (var e : elements) a.add(e); return a; }
    private static JsonArray strings(String... values) { var a = new JsonArray(); for (var v : values) a.add(v); return a; }
    private JsonObject source(Path path) throws Exception {
        var value = new JsonObject(); value.addProperty("path", root.relativize(path).toString()); value.addProperty("sha256", hash(path)); return value;
    }
    @BeforeEach void setup() throws Exception {
        original = root.resolve("original.ili"); positive = root.resolve("positive.ili"); cli = root.resolve("compiler");
        Files.writeString(original, "invalid source"); Files.writeString(positive, "valid source");
        payload = json("""
            {"status":"rejected: RejectionSummary[reasons=[SOURCE_ERRORS], unsupportedFeatureIds=[]]",
             "diagnostics":[{"id":"run-local","code":"ILI-SEM-0005","severity":"ERROR","phase":"TYPE_CHECKING",
               "messageKey":"diagnostic.semantics.type","arguments":[{"name":"actual","kind":"TYPE_NAME","value":"TEXT*20"}],
               "range":{"revision":"1","startOffset":1,"endOffset":2}}]}
            """);
        payload.getAsJsonArray("diagnostics").get(0).getAsJsonObject().getAsJsonObject("range").addProperty("source", original.toUri().toString());
        entry = json("""
            {"testId":"ili23.test","languageVersion":"2.3","referenceExpectation":{"outcome":"accept"},
             "ownExpectation":"rejected","norm":{"url":"https://www.interlis.ch","sections":"2.6"}}
            """);
        entry.add("sources", list(source(original))); entry.add("positiveControls", list(source(positive)));
        entry.add("allowedDiagnostics", signatures(payload, List.of(original), List.of("original.ili")));
        test = json("""
            {"testId":"ili23.test","languageVersion":"2.3","expectation":{"outcome":"accept"},"classification":"FAIL",
             "actualOutcome":"rejected","exitCode":1,"timedOut":false,"inputs":["original.ili"]}
            """);
        test.add("command", strings(cli.toString(), "check", original.toString()));
        counts = json("{\"PASS\":0,\"XFAIL\":0,\"FAIL\":1,\"XPASS\":0,\"ERROR\":0,\"TOTAL\":1}");
        var compiler = new JsonObject(); compiler.add("summary", counts); compiler.add("tests", list(test));
        report = new JsonObject(); report.addProperty("overallResult", "FAIL"); report.add("summary", counts); report.add("compilers", list(compiler));
        summary = new JsonObject(); summary.addProperty("overallResult", "FAIL"); summary.add("summary", counts);
        manifest = json("{\"schemaVersion\":1,\"suiteCommit\":\"commit\",\"suiteSha256\":\"hash\"}"); manifest.add("cases", list(entry));
    }
    private ProcessResult result(JsonObject value, int exit, String stderr) { return new ProcessResult(exit, value.toString(), stderr); }
    private ProcessResult rejected() { return result(payload, 1, ""); }
    private ProcessResult accepted() { return result(json("{\"status\":\"ok\",\"diagnostics\":[]}"), 0, ""); }
    private JsonObject assess(ProcessResult... results) {
        var queue = new ArrayDeque<>(Arrays.asList(results));
        return NormativeConformance.assess(root, cli, "commit", "hash", Set.of("ili23.test"), true,
                manifest, report, summary, 1, command -> queue.removeFirst());
    }
    private void fails(ProcessResult... results) { assertEquals("FAIL", text(assess(results), "overallResult")); }
    private JsonObject diagnostic(JsonObject p) { return p.getAsJsonArray("diagnostics").get(0).getAsJsonObject(); }

    @Test void onlyPinnedOriginalAndCleanPositiveControlEarnException() {
        var assessed = assess(rejected(), accepted());
        assertEquals("PASS", text(assessed, "overallResult"));
        assertEquals(1, assessed.getAsJsonObject("summary").get("XFAIL").getAsInt());
        assertEquals(1, assessed.getAsJsonObject("referenceSummary").get("FAIL").getAsInt());
        assertEquals("FAIL", text(test, "classification"));
    }
    @Test void changedArgumentRangeRevisionSourceOrMultiplicityBlocksException() {
        for (String change : List.of("argument", "range", "revision", "source", "duplicate", "missing")) {
            var altered = payload.deepCopy(); var d = diagnostic(altered);
            switch (change) {
                case "argument" -> d.getAsJsonArray("arguments").get(0).getAsJsonObject().addProperty("value", "BOOLEAN");
                case "range" -> d.getAsJsonObject("range").addProperty("startOffset", 0);
                case "revision" -> d.getAsJsonObject("range").addProperty("revision", "2");
                case "source" -> d.getAsJsonObject("range").addProperty("source", positive.toUri().toString());
                case "duplicate" -> altered.getAsJsonArray("diagnostics").add(d.deepCopy());
                case "missing" -> altered.add("diagnostics", new JsonArray());
                default -> throw new AssertionError(change);
            }
            fails(result(altered, 1, ""));
        }
    }
    @Test void warningsSyntaxAndImplementationDiagnosticsCannotBeWaived() {
        for (var mutation : Map.of("severity", "WARNING", "phase", "PARSING", "code", "ILI-IMPL-0001").entrySet()) {
            var altered = payload.deepCopy(); diagnostic(altered).addProperty(mutation.getKey(), mutation.getValue()); fails(result(altered, 1, ""));
        }
    }
    @Test void cancelledFailedAndIncompleteSupportCannotBeWaived() {
        for (String status : List.of("cancelled: stopped", "failed: defect",
                "rejected: RejectionSummary[reasons=[SOURCE_ERRORS, INCOMPLETE_SUPPORT], unsupportedFeatureIds=[VIEW]]")) {
            var altered = payload.deepCopy(); altered.addProperty("status", status); fails(result(altered, 1, ""));
        }
    }
    @Test void processExitStderrMalformedJsonAndTimeoutFail() {
        fails(result(payload, 2, "")); fails(result(payload, 1, "Exception")); fails(new ProcessResult(1, "not JSON", ""));
        var result = NormativeConformance.assess(root, cli, "commit", "hash", Set.of("ili23.test"), true,
                manifest, report, summary, 1, command -> { throw new java.io.IOException("timeout"); });
        assertEquals("FAIL", text(result, "overallResult"));
    }
    @Test void failedMissingOrWarningPositiveControlPreventsException() {
        fails(rejected(), rejected()); fails(rejected());
        var warning = payload.deepCopy(); warning.addProperty("status", "ok"); fails(rejected(), result(warning, 0, ""));
    }
    @Test void allOriginalAndPositiveHashesAreEnforced() throws Exception {
        for (Path path : List.of(original, positive)) {
            String before = Files.readString(path); Files.writeString(path, "changed"); fails(); Files.writeString(path, before);
        }
    }
    @Test void sourceOrderCommandAndLanguageCannotChange() {
        test.addProperty("languageVersion", "2.4"); fails(); test.addProperty("languageVersion", "2.3");
        test.add("expectation", json("{\"outcome\":\"reject\"}")); fails(); test.add("expectation", entry.get("referenceExpectation"));
        test.add("command", strings(cli.toString(), "check", positive.toString())); fails();
        test.add("command", strings(cli.toString(), "check", original.toString())); test.add("inputs", strings("other.ili")); fails();
    }
    @Test void missingDuplicateAndUnknownResultIdsFail() {
        var compiler = report.getAsJsonArray("compilers").get(0).getAsJsonObject();
        for (JsonArray tests : List.of(new JsonArray(), list(test, test))) {
            compiler.add("tests", tests); assertThrows(IllegalArgumentException.class, this::assess);
        }
        var unknown = test.deepCopy(); unknown.addProperty("testId", "other"); compiler.add("tests", list(unknown));
        assertThrows(IllegalArgumentException.class, this::assess);
    }
    @Test void nonstandardRunnerExitCannotBeHidden() {
        assertThrows(IllegalArgumentException.class, () -> NormativeConformance.assess(root, cli, "commit", "hash",
                Set.of("ili23.test"), true, manifest, report, summary, 2, command -> accepted()));
    }
    @Test void staleOrForgedSummaryFails() {
        summary.addProperty("overallResult", "PASS"); assertThrows(IllegalArgumentException.class, this::assess);
    }
    @Test void changedSuiteOrDuplicateExceptionFails() {
        manifest.addProperty("suiteSha256", "changed"); assertThrows(IllegalArgumentException.class, this::assess);
        manifest.addProperty("suiteSha256", "hash"); manifest.add("cases", list(entry, entry)); assertThrows(IllegalArgumentException.class, this::assess);
    }
    @Test void unknownExceptionInFullRunCannotBeIgnored() {
        var unknown = entry.deepCopy(); unknown.addProperty("testId", "missing"); manifest.add("cases", list(entry, unknown));
        assertThrows(IllegalArgumentException.class, this::assess);
    }
    @Test void xpassAndErrorStayFailures() {
        manifest.add("cases", new JsonArray());
        for (String status : List.of("XPASS", "ERROR")) {
            test.addProperty("classification", status); counts.addProperty("FAIL", 0); counts.addProperty(status, 1); fails(); counts.addProperty(status, 0);
        }
    }
    @Test void interlis1ProfileXfailRemainsSeparateFromNormativeExceptions() {
        String id = "ili23.Attribute10Test.attributeComplete";
        test.addProperty("testId", id); test.addProperty("classification", "XFAIL");
        counts.addProperty("FAIL", 0); counts.addProperty("XFAIL", 1);
        manifest.add("cases", new JsonArray()); report.addProperty("overallResult", "PASS"); summary.addProperty("overallResult", "PASS");
        var result = NormativeConformance.assess(root, cli, "commit", "hash", Set.of(id), true,
                manifest, report, summary, 0, command -> { throw new AssertionError("must not run normative controls"); });
        assertEquals("PASS", text(result, "overallResult"));
        assertEquals(1, result.getAsJsonObject("summary").get("XFAIL").getAsInt());
        assertEquals(0, result.getAsJsonArray("normativeAssessments").size());
    }
    private List<ProcessResult> acceptedOriginal() throws Exception {
        Path negative = root.resolve("zero.ili"); Files.writeString(negative, "zero tolerance");
        var rejection = payload.deepCopy(); diagnostic(rejection).getAsJsonObject("range").addProperty("source", negative.toUri().toString());
        entry.addProperty("ownExpectation", "accepted"); entry.add("referenceExpectation", json("{\"outcome\":\"reject\"}"));
        entry.add("negativeControls", list(source(negative))); entry.add("allowedDiagnostics", signatures(rejection, List.of(negative), List.of("zero.ili")));
        test.add("expectation", entry.get("referenceExpectation")); test.addProperty("actualOutcome", "accepted"); test.addProperty("exitCode", 0);
        return List.of(accepted(), accepted(), result(rejection, 1, ""));
    }
    @Test void acceptedOriginalRequiresExactNegativeControl() throws Exception {
        var result = assess(acceptedOriginal().toArray(ProcessResult[]::new)); assertEquals("PASS", text(result, "overallResult"));
        assertEquals(1, result.getAsJsonObject("summary").get("XFAIL").getAsInt());
        assertEquals(1, result.getAsJsonObject("referenceSummary").get("FAIL").getAsInt());
    }
    @Test void acceptedOriginalCannotOmitOrUnpinNegativeControl() throws Exception {
        acceptedOriginal(); entry.remove("negativeControls"); fails(); entry.add("negativeControls", new JsonArray()); fails();
        entry.add("negativeControls", list(json("{\"path\":\"zero.ili\",\"sha256\":\"wrong\"}"))); fails();
    }
    @Test void acceptedOriginalCannotHideWarningsWrongControlsOrFailure() throws Exception {
        var checks = acceptedOriginal(); fails(rejected()); fails(result(json("{\"status\":\"ok\",\"diagnostics\":[]}"), 0, "Exception"));
        var warning = payload.deepCopy(); warning.addProperty("status", "ok"); fails(result(warning, 0, ""));
        fails(checks.get(0), checks.get(1), checks.get(0));
        var wrong = JsonParser.parseString(checks.get(2).stdout()).getAsJsonObject();
        diagnostic(wrong).getAsJsonArray("arguments").get(0).getAsJsonObject().addProperty("value", "BOOLEAN");
        fails(checks.get(0), checks.get(1), result(wrong, 1, ""));
        wrong.addProperty("status", "failed: defect"); fails(checks.get(0), checks.get(1), result(wrong, 1, ""));
    }
    @Test void acceptedOriginalCannotHideXpassOrUnknownExpectation() throws Exception {
        acceptedOriginal(); test.addProperty("classification", "XPASS"); counts.addProperty("FAIL", 0); counts.addProperty("XPASS", 1); fails();
        test.addProperty("classification", "FAIL"); counts.addProperty("FAIL", 1); counts.addProperty("XPASS", 0); entry.addProperty("ownExpectation", "anything"); fails();
    }
    private JsonObject compatibility(List<ProcessResult> checks) {
        entry.add("referenceExpectation", json("{\"outcome\":\"accept\"}"));
        test.add("expectation", entry.get("referenceExpectation")); test.addProperty("classification", "PASS");
        counts.addProperty("FAIL", 0); counts.addProperty("PASS", 1);
        report.addProperty("overallResult", "PASS"); summary.addProperty("overallResult", "PASS");
        var queue = new ArrayDeque<>(checks);
        return NormativeConformance.assess(root, cli, "commit", "hash", Set.of("ili23.test"), true,
                manifest, report, summary, 0, command -> queue.removeFirst());
    }
    @Test void approvedCompatibilityKeepsRawPassAndRunsAllPinnedControls() throws Exception {
        var checks = acceptedOriginal();
        entry.add("compatibilityDecision", json("{\"id\":\"LEGACY_OID_23_WITHOUT_CONTRACTED\",\"userDecision\":\"APPROVED_USER\"}"));
        var assessed = compatibility(checks);
        assertEquals("PASS", text(assessed,"overallResult"));
        assertEquals(1, assessed.getAsJsonObject("summary").get("PASS").getAsInt());
        assertEquals(0, assessed.getAsJsonObject("summary").get("XFAIL").getAsInt());
        assertEquals("PASS", text(assessed.getAsJsonArray("normativeAssessments").get(0).getAsJsonObject(),"referenceClassification"));
    }
    @Test void rawPassCannotWaiveApprovalOrNegativeControl() throws Exception {
        var checks = acceptedOriginal(); assertEquals("FAIL",text(compatibility(checks),"overallResult"));
        entry.add("compatibilityDecision", json("{\"id\":\"LEGACY_OID_23_WITHOUT_CONTRACTED\",\"userDecision\":\"PENDING\"}"));
        assertEquals("FAIL",text(compatibility(checks),"overallResult"));
        entry.getAsJsonObject("compatibilityDecision").addProperty("userDecision","APPROVED_USER");
        assertEquals("FAIL",text(compatibility(List.of(accepted(),accepted(),accepted())),"overallResult"));
    }
    @Test void controlsOutsideRepositoryCannotBeWaived() {
        entry.add("positiveControls", list(json("{\"path\":\"../outside.ili\",\"sha256\":\"wrong\"}"))); fails();
    }
    @Test void missingAndOldReportsCannotEarnSuccess() throws Exception {
        long started = 10000;
        for (String name : List.of("summary.json", "report.json", "runner-exit.txt")) {
            Files.writeString(root.resolve(name), "{}");
            Files.setLastModifiedTime(root.resolve(name), java.nio.file.attribute.FileTime.fromMillis(started));
        }
        requireFreshReports(root, started);
        Files.setLastModifiedTime(root.resolve("report.json"), java.nio.file.attribute.FileTime.fromMillis(started - 1));
        assertThrows(IllegalArgumentException.class, () -> requireFreshReports(root, started));
        Files.delete(root.resolve("report.json"));
        assertThrows(java.io.IOException.class, () -> requireFreshReports(root, started));
    }
}
