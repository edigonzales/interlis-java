package guru.interlis.cli;

import guru.interlis.compiler.CompilerServices;
import guru.interlis.diagnostics.*;
import guru.interlis.execution.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VerboseDiagnosticsContractTest {
    private static final String RUN_TIME = "2026-11-07 19:20:19";
    @TempDir Path directory;
    record Run(CliExecutionResult result, String out, String err) { }
    Run run(String... args) {
        return run(CancellationToken.none(), args);
    }
    Run run(CancellationToken token, String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        long time = LocalDateTime.of(2026, 11, 7, 19, 20, 19).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        var services = new CompilerServices(RunObserver.noop(), new ClockSource() {
            @Override public long wallTimeEpochMillis() { return time; }
            @Override public long monotonicNanos() { return 0; }
        }, RunContextFactory.defaults());
        var result = new DefaultCompilerCli().run(List.of(args), new CliEnvironment(directory, InputStream.nullInputStream(),
                new PrintStream(out), new PrintStream(err), Map.of(), services), token);
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static String framed(String body, String footer) {
        return "INFO " + DefaultCompilerCli.compilerVersionLine() + System.lineSeparator()
                + body + footer + " " + RUN_TIME + System.lineSeparator();
    }
    @Test void allDiagnosticCommandsDefaultToHeaderAndContextRestoresDetails() throws Exception {
        Files.writeString(directory.resolve("conflict.ili"), "INTERLIS 2.4;\nMODEL F AT \"x\" VERSION \"1\" =\nTOPIC Office=\nCLASS Office=END Office;END Office;END F.");
        Files.writeString(directory.resolve("syntax.ili"), "INTERLIS 2.4;\nMODEL Syntax AT \"x\" VERSION \"1\" = TOPIC");
        for (String command : List.of("check", "compile", "analyze", "diagram", "format")) {
            String file = command.equals("format") ? "syntax.ili" : "conflict.ili";
            for (String language : List.of("en", "de")) {
                var compact = run(command, "--diagnostic-language", language, "--color=never", file);
                var detailed = run(command, "--diagnostic-context", "--diagnostic-language", language, "--color=never", file);
                assertFalse(compact.result.diagnostics().isEmpty(), command);
                assertEquals(compact.result.diagnostics(), detailed.result.diagnostics());
                assertEquals(compact.result.exitCode(), detailed.result.exitCode()); assertEquals(compact.out, detailed.out);
                assertEquals(compact.result.diagnostics().size(), compact.err.lines().count(), compact.err);
                assertFalse(compact.err.contains(" | ")); assertFalse(compact.err.contains("Related location:")); assertFalse(compact.err.contains("Hint:"));
                boolean german = language.equals("de");
                String footer = compact.result.exitCode() == 0
                        ? (german ? "INFO ...Compilerlauf abgeschlossen" : "INFO ...compiler run done")
                        : (german ? "ERROR ...Compilerlauf fehlgeschlagen" : "ERROR ...compiler run failed");
                boolean format = command.equals("format");
                assertEquals(format ? compact.err : framed(compact.err, footer),
                        run(command, "--verbose", "--diagnostic-language", language, "--color=never", file).err);
                assertTrue(detailed.err.contains(" | "), detailed.err);
                if (!command.equals("format")) {
                    assertTrue(detailed.err.contains(language.equals("en") ? "Related location:" : "Verwandte Stelle:"));
                    assertTrue(detailed.err.contains(language.equals("en") ? "Hint:" : "Hinweis:"));
                }
                var color = run(command, "--verbose", "--diagnostic-context", "--diagnostic-language", language, "--color=always", file);
                assertEquals(format ? detailed.err : framed(detailed.err, footer), ColorOutputContractTest.stripSgr(color.err));
                var compactColor = run(command, "--diagnostic-language", language, "--color=always", file);
                assertEquals(compact.err, ColorOutputContractTest.stripSgr(compactColor.err));
            }
        }
    }
    @Test void successAndCancellationHaveDistinctVerboseFooters() throws Exception {
        Files.writeString(directory.resolve("good.ili"), "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\"=END F.");
        assertEquals("", run("check", "good.ili").err);
        var success = run("check", "--verbose", "good.ili");
        assertEquals(0, success.result.exitCode());
        assertEquals(framed("", "INFO ...compiler run done"), success.err);
        var cancelled = run(CancellationToken.requested(CancellationReason.DEADLINE_EXCEEDED),
                "check", "--verbose", "good.ili");
        assertEquals(CliTermination.CANCELLED, cancelled.result.termination());
        assertEquals(framed("cancelled: DEADLINE_EXCEEDED" + System.lineSeparator(),
                "INFO ...compiler run cancelled"), cancelled.err);
        var explain = run("check", "--verbose", "--explain-config");
        assertEquals(0, explain.result.exitCode());
        assertEquals("", explain.err);
        var invalid = run("check", "--verbose", "--warnings", "invalid", "good.ili");
        assertEquals(2, invalid.result.exitCode());
        assertFalse(invalid.err.contains("INFO interlis-compiler"));
        assertFalse(invalid.err.contains("compiler run failed"));
    }
    @Test void jsonAndArtifactsAreIdenticalAndInvalidFlagsFailBeforeIo() throws Exception {
        Files.writeString(directory.resolve("bad.ili"), "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\"=DOMAIN D=Missing;END F.");
        Files.writeString(directory.resolve("good.ili"), "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\"=END F.");
        for (String command : List.of("check", "compile", "analyze")) {
            var a = run(command, "--json", "bad.ili"); var b = run(command, "--json", "--verbose", "--diagnostic-context", "bad.ili");
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
            assertEquals(2, run(command, "--diagnostic-context", "--diagnostic-context", "missing.ili").result.exitCode());
            assertEquals(2, run(command, "--diagnostic-context=true", "missing.ili").result.exitCode());
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
