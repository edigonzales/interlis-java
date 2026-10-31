package guru.interlis.cli;

import com.google.gson.JsonParser;
import guru.interlis.diagnostics.*;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** P3 host boundaries: public claims, command validation and output safety. */
class P3CliBoundaryContractTest {
    @TempDir Path directory;

    @Test void capabilitiesDescribeTheCompletedLanguageWithoutInventingP3Acceptance() {
        var run = run(List.of("capabilities"));
        assertEquals(CliTermination.SUCCESS, run.result.termination());
        var json = JsonParser.parseString(run.out).getAsJsonObject();
        assertTrue(json.getAsJsonArray("notImplemented").isEmpty(), run.out);
        assertTrue(json.getAsJsonArray("partialFeatures").isEmpty(), run.out);
        assertEquals("P3_OPEN", json.get("qualification").getAsString());
        var features = json.getAsJsonArray("features").asList().stream()
                .map(value -> value.getAsString()).toList();
        assertTrue(features.containsAll(List.of("FUNCTION", "TRANSLATION", "VIEW_SCHEMA",
                "GRAPHIC", "CONTEXT", "METADATA_BASKET", "RUNTIME_PARAMETER", "LINE_FORM")));
    }

    @Test void informationalCommandsRejectUnknownAndExtraArguments() {
        for (var args : List.of(List.of("capabilities", "--unknown"),
                List.of("capabilities", "model.ili"))) {
            assertEquals(CliTermination.USAGE_ERROR, run(args).result.termination(), args.toString());
        }
    }

    @Test void unavailableSourceIsAnOperationalFailureWithAVisibleReason() {
        var run = run(List.of("check", directory.resolve("missing.ili").toString()));
        assertEquals(CliTermination.OPERATIONAL_FAILURE, run.result.termination());
        assertFalse(run.err.isBlank());
        assertTrue(run.err.contains("NOT_FOUND"), run.err);
        assertEquals(1, run.result.exitCode());
    }

    @Test void endOfOptionsAllowsAFileWhoseNameStartsWithAHyphen() throws Exception {
        Files.writeString(directory.resolve("-model.ili"),
                "INTERLIS 2.4; MODEL F AT \"https://example.org\" VERSION \"1\" = END F.");
        assertEquals(CliTermination.SUCCESS, run(List.of("check", "--", "-model.ili")).result.termination());
        assertEquals(CliTermination.SUCCESS, run(List.of("format", "--", "-model.ili")).result.termination());
    }

    @Test void everyJsonControlCharacterIsEscapedWithoutChangingItsValue() {
        String value = "\u0000\u0001\b\f\n\r\t\u001f\\\"🦊";
        var diagnostic = new Diagnostic(new DiagnosticId("p3-json-control"),
                new DiagnosticCode("ILI-SYN-0002"), Severity.ERROR, Phase.PARSING,
                "diagnostic.syntax.unexpected", List.of(new DiagnosticArgument.TextArgument("actual", value)),
                Optional.empty(), List.of(), Optional.empty(), List.of(), Optional.empty(),
                Optional.empty(), List.of());
        String encoded = new JsonDiagnosticWriter().write(List.of(diagnostic));
        assertTrue(encoded.chars().noneMatch(c -> c < 0x20), encoded);
        assertEquals(value, JsonParser.parseString(encoded).getAsJsonArray().get(0)
                .getAsJsonObject().getAsJsonArray("arguments").get(0)
                .getAsJsonObject().get("value").getAsString());
    }

    private Run run(List<String> arguments) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var environment = new CliEnvironment(directory, new ByteArrayInputStream(new byte[0]),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
        var result = new DefaultCompilerCli().run(arguments, environment, CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Run(CliExecutionResult result, String out, String err) { }
}
