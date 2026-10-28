package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the small ili2c profile originally recorded in
 * docs/evidence/reference/reference-results.json. This is deliberately a
 * selected P1 profile, not the historical 610-case suite. The 2.4 Base fixture
 * now declares stable object identity as required by the 2.4 reference rule;
 * the historical report is not evidence for these modified input bytes.
 */
class ReferenceFixtureParityTest {
    private static final Path TESTDATA = Path.of(System.getProperty("testdata.dir"));

    @Test
    void acceptedFixtureProfileMatchesReference() {
        SourceText base = source("base24.ili");
        SourceText survey = source("survey24.ili");
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        CompilationRequest request = CompilerTestSupport.request(surveySyntax, baseSyntax,
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));

        SourceText legacy = source("legacy23.ili");
        CompilationRequest legacyRequest = new CompilationRequest(
                List.of(new ModelSelector.ByName("Legacy", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(legacy.id(), legacy)), CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(legacyRequest, CancellationToken.none()));
    }

    @Test
    void rejectedFixtureProfileMatchesReference() {
        assertRejected("broken24.ili", "Broken");
        assertRejected("trailing24.ili", "Trailing");
        SourceText survey = source("survey24.ili");
        CompilationRequest missing = new CompilationRequest(
                List.of(new ModelSelector.ByName("Survey", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(survey.id(), survey)), CompilerOptions.defaults());
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(missing, CancellationToken.none()));
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.MISSING_INPUT));
    }

    private static void assertRejected(String file, String model) {
        SourceText source = source(file);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName(model, Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }

    private static SourceText source(String name) {
        try {
            String text = Files.readString(TESTDATA.resolve("models").resolve(name),
                    StandardCharsets.UTF_8);
            return new SourceText(new SourceId("memory:/" + name), new SourceRevision(1), text);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
