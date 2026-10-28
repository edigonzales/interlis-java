package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliApplicationTest {
    private static final Path TESTDATA = Path.of(System.getProperty("testdata.dir"));

    @TempDir
    Path tempDir;

    @Test
    void versionAndCapabilities() {
        Run version = run("version");
        assertEquals(0, version.result().exitCode());
        assertTrue(version.out().contains("INTERLIS 2.3, 2.4"));
        assertTrue(version.out().contains("api: 0.2"));
        assertTrue(version.out().contains("diagnostics: interlis-diagnostics/v1"));
        assertTrue(version.out().contains("protocol: interlis.compiler/1.0"));
        assertTrue(version.out().contains("format: 1.0"));
        assertTrue(version.out().contains("diagram: 1.0"));
        Run json = run("version", "--json");
        assertEquals(0, json.result().exitCode());
        assertTrue(json.out().contains("\"diagnosticCatalogVersion\":\"interlis-diagnostics/v1\""));
        assertTrue(json.out().contains("\"protocol\":\"interlis.compiler/1.0\""));
        Run capabilities = run("capabilities");
        assertEquals(0, capabilities.result().exitCode());
        assertTrue(capabilities.out().contains("\"protocol\":\"interlis.compiler/1.0\""));
        assertTrue(capabilities.out().contains("\"apiVersion\":\"0.2\""));
        assertTrue(capabilities.out().contains("\"diagnosticCatalogVersion\":\"interlis-diagnostics/v1\""));
        assertTrue(capabilities.out().contains("\"formatVersion\":\"1.0\""));
        assertTrue(capabilities.out().contains("\"diagramSchemaVersion\":\"1.0\""));
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
    void formatWritesStdoutChecksAndWritesAtomically() throws Exception {
        Path file = tempDir.resolve("model.ili");
        String input = "INTERLIS 2.4;\nMODEL M AT \"x\" VERSION \"1\" =\nTOPIC T =\n"
                + "CLASS C =\nvalue : TEXT*10;\nEND C;\nEND T;\nEND M.\n";
        Files.writeString(file, input);

        Run stdout = run("format", "--indent", "4", "--line-ending", "lf", file.toString());
        assertEquals(0, stdout.result().exitCode(), stdout.err());
        assertTrue(stdout.out().contains("    TOPIC T ="));
        assertFalse(stdout.out().contains("\r"));

        Run check = run("format", "--check", file.toString());
        assertEquals(1, check.result().exitCode());
        assertEquals(CliTermination.FORMAT_CHECK_FAILED, check.result().termination());
        assertEquals(input, Files.readString(file));

        Run write = run("format", "--write", file.toString());
        assertEquals(0, write.result().exitCode(), write.err());
        assertTrue(Files.readString(file).contains("  TOPIC T ="));
        assertTrue(Files.readString(file).contains("      value: TEXT*10;"));

        Run clean = run("format", "--check", file.toString());
        assertEquals(0, clean.result().exitCode(), clean.err());
    }

    @Test
    void unsupportedGeneratorAndUnknownOptionsAreRejected() {
        Run generator = run("check", "--export", "XSD",
                TESTDATA.resolve("models/base24.ili").toString());
        assertEquals(2, generator.result().exitCode());
        assertEquals(CliTermination.USAGE_ERROR, generator.result().termination());
        assertTrue(generator.err().contains("unsupported option"));

        Run diagram = run("diagram", "--json",
                TESTDATA.resolve("models/base24.ili").toString());
        assertEquals(2, diagram.result().exitCode());

        Run format = run("format", "--unknown",
                TESTDATA.resolve("models/base24.ili").toString());
        assertEquals(2, format.result().exitCode());
    }

    @Test
    void formatDoesNotWriteInvalidSource() throws Exception {
        Path file = tempDir.resolve("broken.ili");
        String input = "INTERLIS 2.4;\nMODEL M AT \"x\" VERSION \"1\" =\n";
        Files.writeString(file, input);
        Run check = run("format", "--write", file.toString());
        assertEquals(1, check.result().exitCode());
        assertEquals(input, Files.readString(file));
    }

    @Test
    void jsonOutputIsMachineReadable() {
        Run run = run("check", "--json", TESTDATA.resolve("models/broken24.ili").toString());
        assertEquals(1, run.result().exitCode());
        assertTrue(run.out().startsWith("{"));
        assertTrue(run.out().contains("\"diagnostics\":["));
    }

    @Test
    void brokenOutputNeverReportsSuccess() {
        var broken = new PrintStream(new java.io.OutputStream() {
            @Override public void write(int value) throws java.io.IOException {
                throw new java.io.IOException("injected output failure");
            }
        });
        var environment = new CliEnvironment(Path.of("").toAbsolutePath(),
                new ByteArrayInputStream(new byte[0]), broken,
                new PrintStream(new ByteArrayOutputStream()), Map.of());
        assertEquals(CliTermination.OPERATIONAL_FAILURE,
                new DefaultCompilerCli().run(java.util.List.of("version"), environment,
                        CancellationToken.none()).termination());
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
