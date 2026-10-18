package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliApplicationTest {
    private static final Path TESTDATA = Path.of(System.getProperty("testdata.dir"));

    @Test
    void versionAndCapabilities() {
        Run version = run("version");
        assertEquals(0, version.result().exitCode());
        assertTrue(version.out().contains("INTERLIS 2.3, 2.4"));
        Run capabilities = run("capabilities");
        assertEquals(0, capabilities.result().exitCode());
        assertTrue(capabilities.out().contains("\"protocol\":\"interlis.compiler/1.0\""));
    }

    @Test
    void checkPositiveAndNegativeModels() {
        Run positive = run("check",
                TESTDATA.resolve("models/base24.ili").toString(),
                TESTDATA.resolve("models/survey24.ili").toString());
        assertEquals(0, positive.result().exitCode(), positive.err());
        assertEquals(CliTermination.SUCCESS, positive.result().termination());

        Run negative = run("check", TESTDATA.resolve("models/broken24.ili").toString());
        assertEquals(1, negative.result().exitCode());
        assertEquals(CliTermination.SOURCE_REJECTED, negative.result().termination());
        assertTrue(negative.err().contains("ILI-"));
    }

    @Test
    void missingImportIsReportedAsMissingInput() {
        Run run = run("check", "--root", "Survey",
                TESTDATA.resolve("models/survey24.ili").toString());
        assertEquals(1, run.result().exitCode());
        assertTrue(run.err().contains("missing") || run.err().contains("ILI-RES"));
    }

    @Test
    void diagramExportUsesCheckedSchema() {
        Run run = run("diagram", "--root", "Survey", "--format", "mermaid",
                TESTDATA.resolve("models/base24.ili").toString(),
                TESTDATA.resolve("models/survey24.ili").toString());
        assertEquals(0, run.result().exitCode(), run.err());
        assertTrue(run.out().contains("classDiagram"));
        assertTrue(run.out().contains("Building"));
    }

    @Test
    void jsonOutputIsMachineReadable() {
        Run run = run("check", "--json", TESTDATA.resolve("models/broken24.ili").toString());
        assertEquals(1, run.result().exitCode());
        assertTrue(run.out().startsWith("{"));
        assertTrue(run.out().contains("\"diagnostics\":["));
    }

    private static Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliEnvironment environment = new CliEnvironment(Path.of("").toAbsolutePath(),
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
        CliExecutionResult result = new DefaultCompilerCli().run(java.util.List.of(args), environment,
                CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Run(CliExecutionResult result, String out, String err) {
    }
}
