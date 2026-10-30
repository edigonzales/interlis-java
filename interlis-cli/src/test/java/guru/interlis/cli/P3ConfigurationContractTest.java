package guru.interlis.cli;

import com.google.gson.JsonParser;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class P3ConfigurationContractTest {
    @TempDir Path directory;

    @Test void commandLineOverridesFileAndProfileWithObservableRootSelectionAndProvenance() throws Exception {
        Files.writeString(directory.resolve("settings.properties"), "root=Missing\nwarnings=reject\n");
        Files.writeString(directory.resolve("model.ili"),
                "INTERLIS 2.4; MODEL F AT \"https://example.org\" VERSION \"1\" = END F.");
        var rejected = run("check", "--config", "settings.properties", "model.ili");
        assertEquals(CliTermination.INPUT_UNRESOLVED, rejected.result.termination(), rejected.err);
        var accepted = run("check", "--config", "settings.properties", "--root", "F", "model.ili");
        assertEquals(CliTermination.SUCCESS, accepted.result.termination(), accepted.err);
        var explanation = run("check", "--config", "settings.properties", "--root", "F", "--explain-config");
        assertEquals(CliTermination.SUCCESS, explanation.result.termination());
        var settings = JsonParser.parseString(explanation.out).getAsJsonObject().getAsJsonObject("settings");
        assertEquals("CLI", settings.getAsJsonObject("--root").get("origin").getAsString());
        assertEquals("F", settings.getAsJsonObject("--root").getAsJsonArray("values").get(0).getAsString());
        assertEquals("CONFIGURATION_FILE", settings.getAsJsonObject("--warnings").get("origin").getAsString());
        assertEquals("PROFILE_DEFAULT", settings.getAsJsonObject("--offline").get("origin").getAsString());
    }

    @Test void unknownDuplicateAndInvalidConfigurationValuesAreUsageErrorsWithoutLeakingValues() throws Exception {
        for (String contents : List.of("password=marked-secret\n", "root=F\nroot=G\n", "offline=maybe\n", "warnings=unknown\n")) {
            Files.writeString(directory.resolve("settings.properties"), contents);
            var result = run("check", "--config", "settings.properties", "--explain-config");
            assertEquals(CliTermination.USAGE_ERROR, result.result.termination(), result.err);
            assertFalse(result.out.contains("marked-secret"));
            assertFalse(result.err.contains("marked-secret"));
        }
    }

    @Test void configurationExplanationRedactsUriCredentialsAndQuerySecrets() {
        var result = run("check", "--repository", "https://user:marked-secret@example.org/models?token=marked-secret",
                "--explain-config");
        assertEquals(CliTermination.SUCCESS, result.result.termination(), result.err);
        assertFalse(result.out.contains("marked-secret"));
        assertFalse(result.out.contains("user:"));
        assertTrue(result.out.contains("example.org/models?REDACTED"));
    }

    @Test void explanationNeverEchoesSecretsFromHostlessOpaqueOrMalformedUris() throws Exception {
        for (String value : List.of("file:///tmp/x?token=marked-secret",
                "mailto:user:marked-secret@example.org?token=marked-secret",
                "https://user:marked-secret@example.org/%")) {
            Files.writeString(directory.resolve("settings.properties"), "repository=" + value + "\n");
            for (var result : List.of(run("check", "--repository", value, "--explain-config"),
                    run("check", "--config", "settings.properties", "--explain-config"))) {
                assertEquals(CliTermination.SUCCESS, result.result.termination());
                assertFalse(result.out.contains("marked-secret"), result.out);
                assertFalse(result.err.contains("marked-secret"), result.err);
            }
        }
        String windowsDirectory = "C:\\models\\repository";
        var windows = run("check", "--repository", windowsDirectory, "--explain-config");
        assertEquals(CliTermination.SUCCESS, windows.result.termination());
        assertEquals(windowsDirectory, JsonParser.parseString(windows.out).getAsJsonObject().getAsJsonObject("settings")
                .getAsJsonObject("--repository").getAsJsonArray("values").get(0).getAsString());
    }

    @Test void duplicatesAndMissingValuesFailBeforeLoadingSourceFiles() {
        for (var args : List.of(List.of("check", "--json", "--json", "missing.ili"),
                List.of("check", "--cache", "--offline"),
                List.of("format", "--indent", "2", "--indent", "4", "missing.ili"))) {
            var result = run(args.toArray(String[]::new));
            assertEquals(CliTermination.USAGE_ERROR, result.result.termination(), result.err);
            assertFalse(result.err.contains("source load failed"));
        }
    }

    @Test void helpIsDerivedFromTheSameGrammarAndRequiresNoSourceFiles() {
        var result = run("help");
        assertEquals(CliTermination.SUCCESS, result.result.termination());
        assertTrue(result.err.isEmpty());
        for (String command : List.of("check", "format", "diagram")) {
            assertTrue(result.out.contains(command));
            for (var option : CommandSpec.standard(command).options()) assertTrue(result.out.contains(option.name()));
        }
    }

    @Test void warningPolicyChangesTheGateWithoutRelabelingWarningsAsSourceErrors() throws Exception {
        Files.writeString(directory.resolve("warning.ili"), "INTERLIS 2.4; MODEL F AT \"https://example.org\" VERSION \"1\" = TOPIC T(ABSTRACT,ABSTRACT)=END T; END F.");
        var allowed = run("check", "--warnings", "allow", "warning.ili");
        assertEquals(CliTermination.SUCCESS, allowed.result.termination());
        var rejected = run("check", "--warnings", "reject", "warning.ili");
        assertEquals(CliTermination.WARNINGS_REJECTED, rejected.result.termination());
        assertFalse(rejected.result.diagnostics().isEmpty());
        assertTrue(rejected.result.diagnostics().stream().allMatch(d -> d.severity() == guru.interlis.diagnostics.Severity.WARNING));
    }

    private Run run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var environment = new CliEnvironment(directory, new ByteArrayInputStream(new byte[0]),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of("ILI2C_MODEL_PATH", "ignored"));
        var result = new DefaultCompilerCli().run(List.of(args), environment, CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    private record Run(CliExecutionResult result, String out, String err) { }
}
