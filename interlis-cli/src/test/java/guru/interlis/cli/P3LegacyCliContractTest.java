package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class P3LegacyCliContractTest {
    @TempDir Path directory;

    @Test void helpAndVersionUseTheRecordedChannelWithoutModelDiscovery() {
        for (String option : List.of("-h", "--help", "-u", "--usage", "-v", "--version")) {
            var result = run(option);
            assertEquals(0, result.result.exitCode(), result.err);
            assertEquals(CliTermination.SUCCESS, result.result.termination());
            assertTrue(result.out.isEmpty());
            assertTrue(result.err.contains("interlis-java"));
            assertTrue(result.err.contains("ili2c-check/5.6.9-v1"));
        }
    }

    @Test void checkingAndQuietModesKeepStdoutEmptyAndUseTheSameSemanticEngine() throws Exception {
        model("Good.ili", "MODEL F AT \"https://example.org\" VERSION \"1\" = END F.");
        var normal = run("--no-auto", "-o0", "Good.ili");
        assertEquals(0, normal.result.exitCode(), normal.err);
        assertTrue(normal.out.isEmpty());
        assertTrue(normal.err.contains("compiler run done"));
        var quiet = run("--quiet", "--no-auto", "Good.ili");
        assertEquals(0, quiet.result.exitCode());
        assertTrue(quiet.out.isEmpty()); assertTrue(quiet.err.isEmpty(), quiet.err);
        model("Bad.ili", "MODEL F AT \"https://example.org\" VERSION \"1\" = TOPIC T = CLASS C = x: Missing; END C; END T; END F.");
        var rejected = run("--quiet", "--no-auto", "Bad.ili");
        assertEquals(1, rejected.result.exitCode());
        assertEquals(CliTermination.SOURCE_REJECTED, rejected.result.termination());
        assertTrue(rejected.out.isEmpty()); assertTrue(rejected.err.contains("Error:"));
        assertFalse(rejected.result.diagnostics().isEmpty());
    }

    @Test void localModelDirectoryFindsImportsWithoutSelectingUnrelatedBrokenRoots() throws Exception {
        model("Base.ili", "MODEL Base AT \"https://example.org\" VERSION \"1\" = DOMAIN Name = TEXT*10; END Base.");
        model("Root.ili", "MODEL Root AT \"https://example.org\" VERSION \"1\" = IMPORTS Base; DOMAIN Name EXTENDS Base.Name = TEXT*5; END Root.");
        model("Unrelated.ili", "MODEL Unrelated AT \"https://example.org\" VERSION \"1\" = TOPIC T = CLASS C = a: Missing; END C; END T; END Unrelated.");
        var result = run("--modeldir", directory.toString(), "Root.ili");
        assertEquals(0, result.result.exitCode(), result.err);
        assertTrue(result.out.isEmpty());
        var noAuto = run("--no-auto", "Root.ili");
        assertEquals(1, noAuto.result.exitCode());
        assertEquals(CliTermination.INPUT_UNRESOLVED, noAuto.result.termination());
        var named = run("--modeldir", "%ILI_DIR", "Root");
        assertEquals(0, named.result.exitCode(), named.err);
    }

    @Test void everyInventoriedUnsupportedOptionFailsExplicitlyBeforeIo() {
        for (String option : LegacyCliProfile.ili2c569().unsupportedOptions().keySet()) {
            var result = run(option, "unavailable.ili");
            assertEquals(1, result.result.exitCode(), option);
            assertEquals(CliTermination.USAGE_ERROR, result.result.termination(), option);
            assertTrue(result.err.contains("unsupported option: " + option), result.err);
        }
        assertEquals(1, run("--unknown").result.exitCode());
        assertEquals(1, run().result.exitCode());
        assertEquals(1, run("--modeldir").result.exitCode());
    }

    @Test void traceReflectsRealCompilerEventsAndRetainsOneHostCompletion() throws Exception {
        model("Good.ili", "MODEL F AT \"https://example.org\" VERSION \"1\" = END F.");
        var result = run("--no-auto", "--trace", "Good.ili");
        assertEquals(0, result.result.exitCode(), result.err);
        assertTrue(result.out.isEmpty());
        assertTrue(result.err.contains("Trace: PhaseStarted DECLARATION_INDEXING"), result.err);
        assertTrue(result.result.runId().isPresent());
    }

    @Test void warningSuppressionChangesOnlyTheTextChannel() throws Exception {
        model("Warning.ili", "MODEL F AT \"https://example.org\" VERSION \"1\" = TOPIC T(ABSTRACT,ABSTRACT)=END T; END F.");
        var visible = run("--quiet", "--no-auto", "Warning.ili");
        assertEquals(0, visible.result.exitCode());
        assertTrue(visible.err.contains("Warning:"));
        var suppressed = run("--quiet", "--without-warnings", "--no-auto", "Warning.ili");
        assertEquals(0, suppressed.result.exitCode());
        assertTrue(suppressed.err.isEmpty(), suppressed.err);
        assertFalse(suppressed.result.diagnostics().isEmpty());
        assertEquals(visible.result.diagnostics(), suppressed.result.diagnostics());
    }

    private void model(String name, String body) throws IOException {
        Files.writeString(directory.resolve(name), "INTERLIS 2.4; " + body);
    }
    private Run run(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        var environment = new CliEnvironment(directory, new ByteArrayInputStream(new byte[0]),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
        var result = new DefaultCompilerCli(LegacyCliProfile.ili2c569()).run(List.of(args), environment, CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    private record Run(CliExecutionResult result, String out, String err) { }
}
