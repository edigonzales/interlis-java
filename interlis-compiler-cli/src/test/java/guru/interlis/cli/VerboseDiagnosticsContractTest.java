package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VerboseDiagnosticsContractTest {
    @TempDir Path directory;
    record Run(CliExecutionResult result, String out, String err) { }
    Run run(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        var result = new DefaultCompilerCli().run(List.of(args), new CliEnvironment(directory, InputStream.nullInputStream(),
                new PrintStream(out), new PrintStream(err), Map.of()), CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    @Test void allDiagnosticCommandsDefaultToHeaderAndVerboseRestoresDetails() throws Exception {
        Files.writeString(directory.resolve("conflict.ili"), "INTERLIS 2.4;\nMODEL F AT \"x\" VERSION \"1\" =\nTOPIC Office=\nCLASS Office=END Office;END Office;END F.");
        Files.writeString(directory.resolve("syntax.ili"), "INTERLIS 2.4;\nMODEL Syntax AT \"x\" VERSION \"1\" = TOPIC");
        for (String command : List.of("check", "compile", "analyze", "diagram", "format")) {
            String file = command.equals("format") ? "syntax.ili" : "conflict.ili";
            for (String language : List.of("en", "de")) {
                var compact = run(command, "--diagnostic-language", language, "--color=never", file);
                var verbose = run(command, "--verbose", "--diagnostic-language", language, "--color=never", file);
                assertFalse(compact.result.diagnostics().isEmpty(), command);
                assertEquals(compact.result.diagnostics(), verbose.result.diagnostics());
                assertEquals(compact.result.exitCode(), verbose.result.exitCode()); assertEquals(compact.out, verbose.out);
                assertEquals(compact.result.diagnostics().size(), compact.err.lines().count(), compact.err);
                assertFalse(compact.err.contains(" | ")); assertFalse(compact.err.contains("Related location:")); assertFalse(compact.err.contains("Hint:"));
                assertTrue(verbose.err.contains(" | "), verbose.err);
                if (!command.equals("format")) {
                    assertTrue(verbose.err.contains(language.equals("en") ? "Related location:" : "Verwandte Stelle:"));
                    assertTrue(verbose.err.contains(language.equals("en") ? "Hint:" : "Hinweis:"));
                }
                var color = run(command, "--verbose", "--diagnostic-language", language, "--color=always", file);
                assertEquals(verbose.err, ColorOutputContractTest.stripSgr(color.err));
                var compactColor = run(command, "--diagnostic-language", language, "--color=always", file);
                assertEquals(compact.err, ColorOutputContractTest.stripSgr(compactColor.err));
            }
        }
    }
    @Test void jsonAndArtifactsAreIdenticalAndInvalidFlagsFailBeforeIo() throws Exception {
        Files.writeString(directory.resolve("bad.ili"), "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\"=DOMAIN D=Missing;END F.");
        Files.writeString(directory.resolve("good.ili"), "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\"=END F.");
        for (String command : List.of("check", "compile", "analyze")) {
            var a = run(command, "--json", "bad.ili"); var b = run(command, "--json", "--verbose", "bad.ili");
            assertEquals(a.out, b.out); assertTrue(b.err.isEmpty());
        }
        for (String format : List.of("mermaid", "plantuml", "json"))
            assertEquals(run("diagram", "--format", format, "good.ili").out,
                    run("diagram", "--verbose", "--format", format, "good.ili").out);
        var diagramJson = run("diagram", "--format", "json", "good.ili");
        assertEquals(0, diagramJson.result.exitCode(), diagramJson.err);
        assertTrue(com.google.gson.JsonParser.parseString(diagramJson.out).isJsonObject());
        var invalidDiagramJson = run("diagram", "--json", "missing.ili");
        assertEquals(2, invalidDiagramJson.result.exitCode());
        assertTrue(invalidDiagramJson.err.contains("use --format json"));
        assertFalse(invalidDiagramJson.err.contains("source not found"));
        assertEquals(run("format", "good.ili").out, run("format", "--verbose", "good.ili").out);
        for (String command : List.of("check", "compile", "analyze", "diagram", "format")) {
            assertEquals(2, run(command, "--verbose", "--verbose", "missing.ili").result.exitCode());
            assertEquals(2, run(command, "--verbose=true", "missing.ili").result.exitCode());
        }
        for (String command : List.of("help", "version", "capabilities")) assertEquals(2, run(command,"--verbose").result.exitCode());
        assertEquals(2, run("format","--repository","missing","missing.ili").result.exitCode());
    }
    @Test void everySeverityHasTheSameHeaderInBothModesAndLegacyRenderersRemainDetailed() {
        var source = new SourceText(new SourceId("memory:/frame.ili"), new SourceRevision(1), "Missing");
        var range = new SourceRange(source.id(), source.revision(), 0, 7);
        var renderer = new ConsoleDiagnosticRenderer();
        for (var severity : Severity.values()) for (var language : DiagnosticLanguage.values()) {
            var diagnostic = new Diagnostic(new DiagnosticId("test"), new DiagnosticCode("ILI-SEM-0001"), severity, Phase.BINDING,
                    "diagnostic.semantics.unresolved", List.of(new DiagnosticArgument.IdentifierArgument("name", "Missing")),
                    Optional.of(range), List.of(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(), List.of());
            String detailed = renderer.render(diagnostic, Map.of(source.id(), source), language);
            String compact = renderer.render(diagnostic, Map.of(source.id(), source), language, new RenderOptions(false, 1, 1, 160, false));
            assertEquals(detailed.lines().findFirst().orElseThrow(), compact); assertFalse(compact.contains("\n"));
            assertTrue(detailed.contains(" | "));
        }
    }
}
