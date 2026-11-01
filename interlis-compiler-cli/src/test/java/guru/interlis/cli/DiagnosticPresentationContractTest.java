package guru.interlis.cli;

import com.google.gson.*;
import guru.interlis.diagnostics.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticPresentationContractTest {
    @TempDir Path directory;
    private static final String GOOD = "MODEL Good AT \"x\" VERSION \"1\" = END Good.";
    private static final String BAD = "MODEL Bad AT \"x\" VERSION \"1\" = TOPIC T=CLASS C=value:Missing;END C;END T;END Bad.";

    @Test void checkAndCompileUseRequestDiagnosticsWhileAnalyzeRetainsAll() throws Exception {
        Files.writeString(directory.resolve("mixed.ili"), "INTERLIS 2.4;" + GOOD + BAD);
        for (var command : List.of("check", "compile", "diagram")) {
            var run = run(command, "--root", "Good", "mixed.ili");
            assertEquals(0, run.result.exitCode(), run.err); assertTrue(run.err.isEmpty(), run.err);
            assertTrue(run.result.diagnostics().isEmpty());
        }
        var analyze = run("analyze", "--root", "Good", "mixed.ili");
        assertFalse(analyze.result.diagnostics().isEmpty()); assertTrue(analyze.err.contains("Missing"));
        var bad = run("check", "--root", "Bad", "mixed.ili");
        assertEquals(1, bad.result.exitCode()); assertTrue(bad.out.isEmpty()); assertTrue(bad.err.contains("cannot be resolved"));
    }

    @Test void explicitSourcesAndWarningsRespectModelBoundaries() throws Exception {
        Files.writeString(directory.resolve("good.ili"), "INTERLIS 2.4;" + GOOD);
        Files.writeString(directory.resolve("bad.ili"), "INTERLIS 2.4;" + BAD);
        assertTrue(run("check", "--root", "Good", "good.ili", "bad.ili").err.isEmpty());
        Files.writeString(directory.resolve("warnings.ili"), "INTERLIS 2.4;" + GOOD + "MODEL Other AT \"x\" VERSION \"1\" = TOPIC T(ABSTRACT,ABSTRACT)=END T;END Other.");
        var foreign = run("check", "--warnings", "reject", "--root", "Good", "warnings.ili");
        assertEquals(0, foreign.result.exitCode()); assertTrue(foreign.err.isEmpty());
        assertEquals(CliTermination.WARNINGS_REJECTED, run("check", "--warnings", "reject", "--root", "Other", "warnings.ili").result.termination());
        var diagram = run("diagram", "--root", "Other", "--diagnostic-language", "de", "warnings.ili");
        assertEquals(0, diagram.result.exitCode()); assertTrue(diagram.err.contains("Warnung")); assertFalse(diagram.out.isBlank());
    }

    @Test void languageIsExplicitPerInvocationAndValidatedBeforeLoading() throws Exception {
        Files.writeString(directory.resolve("bad.ili"), "INTERLIS 2.4;" + BAD);
        for (var command : List.of("check", "compile", "analyze", "diagram", "format")) {
            var invalid = run(command, "--diagnostic-language", "fr", "missing.ili");
            assertEquals(CliTermination.USAGE_ERROR, invalid.result.termination()); assertFalse(invalid.err.contains("loading"));
        }
        assertTrue(run("check", "--diagnostic-language", "de", "bad.ili").err.contains("kann an dieser Stelle nicht aufgelöst werden"));
        assertTrue(run("check", "bad.ili").err.contains("cannot be resolved at this location"));
    }

    @Test void unchangedOfficeOriginalIsRejectedAndSeparatePositiveControlPasses() throws Exception {
        var original = Path.of(System.getProperty("repo.root"), "testdata/cli-modernization/original/SO_AGI_DataCatalog_Base_20260529.ili");
        var bytes = Files.readAllBytes(original);
        assertEquals("fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var en = run("check", "--verbose", original.toString()); var de = run("check", "--verbose", "--diagnostic-language", "de", original.toString());
        assertEquals(1, en.result.exitCode()); assertEquals(en.result.diagnostics(), de.result.diagnostics());
        assertTrue(en.err.contains("conflicts with a name in the enclosing namespace"), en.err);
        assertTrue(de.err.contains("kollidiert")); assertTrue(de.err.contains("Hinweis:"));
        assertTrue(en.err.contains(":272:9:"), en.err); assertTrue(en.err.contains(":279:5:"), en.err);
        assertFalse(de.err.contains("SCOPE_")); assertFalse(en.err.contains("diagnostic."));
        Files.writeString(directory.resolve("OfficeControl.ili"), "INTERLIS 2.4; MODEL Control AT \"x\" VERSION \"1\" = TOPIC Office = CLASS Contact = END Contact; END Office; END Control.");
        assertEquals(0, run("check", "OfficeControl.ili").result.exitCode());
        assertArrayEquals(bytes, Files.readAllBytes(original));
    }

    @Test void jsonLanguageChangesOnlyPresentationAndIncludesActualRelatedLocations() throws Exception {
        Files.writeString(directory.resolve("conflict.ili"), "!! 🦊\r\nINTERLIS 2.4;\r\nMODEL F AT \"x\" VERSION \"1\" = TOPIC Office =\r\nCLASS Office=END Office;END Office;END F.");
        var en = run("check", "--json", "conflict.ili"); var de = run("check", "--json", "--diagnostic-language", "de", "conflict.ili");
        assertTrue(en.err.isEmpty()); assertTrue(de.err.isEmpty());
        var a = JsonParser.parseString(en.out).getAsJsonObject(); var b = JsonParser.parseString(de.out).getAsJsonObject();
        var da = a.getAsJsonArray("diagnostics").get(0).getAsJsonObject(); var db = b.getAsJsonArray("diagnostics").get(0).getAsJsonObject();
        assertNotEquals(da.get("message"), db.get("message"));
        assertEquals(1, da.getAsJsonArray("relatedLocations").size());
        stripPresentation(a); stripPresentation(b); assertEquals(a, b);
    }

    @Test void anExplicitlyRequestedDamagedFileWithoutAModelStillExplainsItsRejection() throws Exception {
        Files.writeString(directory.resolve("NoModel.ili"), "INTERLIS 2.4;\r\nGARBAGE\r\n");
        var text = run("check", "NoModel.ili"); assertEquals(1, text.result.exitCode());
        assertTrue(text.out.isEmpty()); assertTrue(text.err.contains("ILI-SYN-0004"), text.err);
        var json = run("check", "--json", "NoModel.ili"); assertEquals(1, json.result.exitCode()); assertTrue(json.err.isEmpty());
        assertFalse(JsonParser.parseString(json.out).getAsJsonObject().getAsJsonArray("diagnostics").isEmpty());
        var diagram = run("diagram", "NoModel.ili"); assertEquals(1, diagram.result.exitCode());
        assertTrue(diagram.out.isEmpty()); assertTrue(diagram.err.contains("ILI-SYN-0004"));
    }

    @Test void rendererUsesMatchingRevisionAndUtf16PositionsWithoutReadingFiles() {
        var id = new SourceId("memory:/🦊.ili"); var revision = new SourceRevision(7); var source = new SourceText(id, revision, "🦊\r\n  Missing");
        var range = new SourceRange(id, revision, 6, 13);
        var d = new Diagnostic(new DiagnosticId("position"), new DiagnosticCode("ILI-SEM-0001"), Severity.ERROR, Phase.BINDING,
                "diagnostic.semantics.unresolved", List.of(new DiagnosticArgument.IdentifierArgument("name", "Missing\n\u001b")), Optional.of(range),
                List.of(new RelatedLocation(new SourceRange(id, revision, 0, 2), "diagnostic.related.conflictingDeclaration", List.of(new DiagnosticArgument.IdentifierArgument("name", "🦊")))),
                Optional.of(new DiagnosticId("cause")), List.of(new DiagnosticId("dependency")), Optional.empty(), Optional.empty(), List.of());
        var renderer = new ConsoleDiagnosticRenderer();
        assertTrue(renderer.render(d, Map.of(id, source)).contains(":2:3:"));
        assertTrue(renderer.render(d, Map.of(id, source)).contains(":1:1:"));
        assertTrue(renderer.render(d, Map.of()).contains("@7 (UTF-16 6..13)"));
        assertTrue(renderer.render(d, Map.of(id, new SourceText(id, new SourceRevision(8), source.text()))).contains("@7 (UTF-16"));
        assertTrue(renderer.render(d, Map.of()).contains("Missing\\u000a\\u001b"));
        var json = JsonParser.parseString(new JsonDiagnosticWriter().write(List.of(d))).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("cause", json.get("causeId").getAsString()); assertEquals("dependency", json.getAsJsonArray("dependsOn").get(0).getAsString());
    }

    private static void stripPresentation(JsonElement node) {
        if (node.isJsonObject()) { var o = node.getAsJsonObject(); o.remove("message"); o.remove("hint"); new ArrayList<>(o.entrySet()).forEach(e -> stripPresentation(e.getValue())); }
        else if (node.isJsonArray()) node.getAsJsonArray().forEach(DiagnosticPresentationContractTest::stripPresentation);
    }
    private Run run(String... arguments) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        var environment = new CliEnvironment(directory, new ByteArrayInputStream(new byte[0]), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
        var result = new DefaultCompilerCli().run(List.of(arguments), environment, CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    private record Run(CliExecutionResult result, String out, String err) { }
}
