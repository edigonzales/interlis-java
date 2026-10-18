package guru.interlis.projection;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
import guru.interlis.projection.ProjectionNoticeCode;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSnapshotReferenceTest {
    @Test
    void staleSelectionIsRejected() {
        AnalysisSnapshot first = analyze("First");
        AnalysisSnapshot second = analyze("Second");
        SymbolRef stale = new SymbolRef(first.snapshotId(), first.semanticGraph().models().getFirst());
        DiagramOptions options = DiagramOptions.defaults(new DiagramSelection(List.of(stale)));
        DiagramProjectionOutcome outcome = DiagramProjectors.create().project(
                new DiagramInput.FromAnalysis(second), options, CancellationToken.none());
        assertInstanceOf(DiagramProjectionOutcome.Rejected.class, outcome);
        assertTrue(((DiagramProjectionOutcome.Rejected) outcome).reasons().stream()
                .anyMatch(n -> n.code() == ProjectionNoticeCode.STALE_SELECTION));
    }

    private static AnalysisSnapshot analyze(String modelName) {
        SourceText source = new SourceText(new SourceId("memory:/" + modelName + ".ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL %s AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
                    CLASS Item =
                      value : 0..9;
                    END Item;
                  END Data;
                END %s.
                """.formatted(modelName, modelName));
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName(modelName, Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        return succeeded.analysis();
    }
}
