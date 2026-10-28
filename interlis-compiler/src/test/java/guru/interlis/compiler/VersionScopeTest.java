package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionScopeTest {
    @Test
    void interlis23ModelCompiles() {
        String model = """
                INTERLIS 2.3;
                MODEL Old AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
                    CLASS Item =
                      label : TEXT*20;
                    END Item;
                  END Data;
                END Old.
                """;
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile(model, "Old"),
                () -> "expected 2.3 success");
    }

    @Test
    void multicoordIsAcceptedIn24AndRejectedIn23() {
        String model24 = """
                INTERLIS 2.4;
                MODEL New AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
                    CLASS Item =
                      point : MULTICOORD 0.000..1000.000, 0.000..1000.000;
                    END Item;
                  END Data;
                END New.
                """;
        CompilationOutcome outcome24 = compile(model24, "New");
        assertInstanceOf(CompilationOutcome.Succeeded.class, outcome24,
                () -> describe(outcome24));
        String model23 = model24.replace("INTERLIS 2.4;", "INTERLIS 2.3;")
                .replace("MODEL New", "MODEL New").replace("END New.", "END New.");
        // The model name remains New; only the language header changes.
        CompilationOutcome outcome23 = compile(model23, "New");
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class, outcome23, () -> describe(outcome23));
        assertTrue(rejected.analysis().diagnostics().stream()
                .anyMatch(d -> d.severity()==guru.interlis.diagnostics.Severity.ERROR));
    }

    private static CompilationOutcome compile(String text, String modelName) {
        SourceText source = new SourceText(new SourceId("memory:/version.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(
                java.util.List.of(new ModelSelector.ByName(modelName, Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }

    private static String describe(CompilationOutcome outcome) {
        return switch (outcome) {
            case CompilationOutcome.Rejected rejected -> "rejected " + rejected.rejection()
                    + " " + rejected.analysis().diagnostics();
            case CompilationOutcome.Failed failed -> "failed " + failed.error();
            case CompilationOutcome.Cancelled cancelled -> "cancelled " + cancelled.reason();
            case CompilationOutcome.Succeeded ignored -> "succeeded";
        };
    }
}
