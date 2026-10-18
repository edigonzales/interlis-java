package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Missing imports must be visible as resolver diagnostics and block the gate. */
class ImportDiagnosticContractTest {
    @Test
    void missingImportProducesResolverDiagnosticAndNoSchema() {
        SourceText survey = new SourceText(new SourceId("memory:/missing-survey.ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL Survey AT "https://example.interlis.ch" VERSION "1.0" =
                  IMPORTS Base;
                  TOPIC Data =
                    CLASS Building =
                      height : 0..999;
                    END Building;
                  END Data;
                END Survey.
                """);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Survey", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(survey.id(), survey)), CompilerOptions.defaults());
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.MISSING_INPUT),
                rejected.rejection().reasons().toString());
        assertTrue(rejected.analysis().diagnostics().stream()
                        .anyMatch(d -> d.code().value().startsWith("ILI-RES-")),
                rejected.analysis().diagnostics().toString());
    }
}
