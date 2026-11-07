package guru.interlis.cli;

import guru.interlis.execution.*;
import guru.interlis.compiler.CompilerServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompilerCliIdentityContractTest {
    @TempDir Path directory;
    @Test void helpUsesNewIdentity() { information("help"); }
    @Test void shortHelpUsesNewIdentity() { information("-h"); }
    @Test void longHelpUsesNewIdentity() { information("--help"); }
    @Test void versionUsesNewIdentity() { information("version"); }
    @Test void globalVersionUsesNewIdentity() { information("--version"); }
    @Test void emptyInvocationIsUsage() { usage(); }
    @Test void bareFileIsUsageBeforeIo() { usage("missing.ili"); }
    @Test void bareUrlIsUsageBeforeIo() { usage("https://invalid.example/model.ili"); }
    @Test void unknownCommandIsUsageBeforeIo() { usage("typo", "missing.ili"); }
    @Test void oldOptionsAreRejectedBeforeIo() {
        for (String option : List.of("-o0", "--no-auto", "--modeldir")) usage("check", option, "missing.ili");
    }
    @Test void invalidOptionIsRejectedBeforeIo() { usage("check", "--invalid", "missing.ili"); }
    @Test void capabilitiesUseNewIdentity() {
        var run = run("capabilities"); assertEquals(0, run.result.exitCode());
        assertTrue(run.out.contains("\"tool\":\"interlis-compiler\""));
    }
    @Test void explicitImportAndNeighbourIsolationRemainAvailable() throws IOException {
        Files.writeString(directory.resolve("Base.ili"), "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = DOMAIN Name = TEXT*20; END Base.");
        Files.writeString(directory.resolve("Root.ili"), "INTERLIS 2.4; MODEL Root AT \"x\" VERSION \"1\" = IMPORTS Base; DOMAIN Name EXTENDS Base.Name = TEXT*10; END Root.");
        Files.writeString(directory.resolve("Unrelated.ili"), "broken neighbour");
        assertEquals(0, run("check", "Root.ili", "Base.ili").result.exitCode());
        assertEquals(CliTermination.INPUT_UNRESOLVED, run("check", "Root.ili").result.termination());
        assertEquals(0, run("check", "Base.ili").result.exitCode());
    }
    @Test void optionEndPreservesUnicodeAndLeadingMinus() throws IOException {
        Files.writeString(directory.resolve("-Grüezi 😀.ili"), "INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = END M.");
        assertEquals(0, run("check", "--", "-Grüezi 😀.ili").result.exitCode());
    }
    @Test void warningsRemainStructuredAndVisible() throws IOException {
        Files.writeString(directory.resolve("Warning.ili"), "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = TOPIC T(ABSTRACT,ABSTRACT)=END T; END F.");
        var result = run("check", "Warning.ili"); assertEquals(0, result.result.exitCode());
        assertTrue(result.err.contains("WARNING")); assertFalse(result.result.diagnostics().isEmpty());
        assertEquals(CliTermination.WARNINGS_REJECTED, run("check", "--warnings", "reject", "Warning.ili").result.termination());
    }
    private void information(String arg) {
        var result = run(arg); assertEquals(0, result.result.exitCode(), result.err);
        assertTrue(result.out.contains("interlis-compiler")); assertTrue(result.err.isEmpty());
        assertFalse(result.out.contains("ili2c")); assertNoCompiler(result.events);
        if (List.of("help", "-h", "--help").contains(arg)) {
            assertTrue(result.out.contains("Diagram output (diagram)"));
            assertTrue(result.out.contains("--format mermaid|plantuml|json"));
            assertTrue(result.out.contains("interlis-compiler diagram --format json"));
            assertTrue(result.out.contains("JSON output (check, compile, analyze, version)"));
            assertEquals(1, result.out.lines().filter(line -> line.stripLeading().startsWith("--json ")).count());
            assertTrue(result.out.contains("--model-pin NAME VERSION SOURCE SHA256"));
            assertTrue(result.out.contains("--source-pin SOURCE SHA256"));
        }
    }
    private void usage(String... args) {
        var result = run(args); assertEquals(2, result.result.exitCode(), result.err);
        assertEquals(CliTermination.USAGE_ERROR, result.result.termination());
        assertTrue(result.out.isEmpty()); assertTrue(result.err.contains("interlis-compiler check <File.ili>"));
        assertTrue(result.err.contains("interlis-compiler --help"));
        if (args.length == 0) assertEquals("missing command", result.err.lines().findFirst().orElseThrow());
        assertNoCompiler(result.events);
    }
    private void assertNoCompiler(List<RunEvent> events) {
        assertEquals(1, events.stream().filter(e -> e instanceof RunEvent.Started).count());
        assertFalse(events.stream().anyMatch(e -> e instanceof RunEvent.PhaseStarted));
    }
    private Run run(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream(); var events = new ArrayList<RunEvent>();
        var defaults = CompilerServices.defaults();
        var environment = new CliEnvironment(directory, new ByteArrayInputStream(new byte[0]),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), Map.of(),
                new CompilerServices(events::add, defaults.clock(), defaults.runContexts()));
        var result = new DefaultCompilerCli().run(List.of(args), environment, CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8), events);
    }
    private record Run(CliExecutionResult result, String out, String err, List<RunEvent> events) { }
}
