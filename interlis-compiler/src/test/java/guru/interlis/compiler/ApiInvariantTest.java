package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SnapshotId;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiInvariantTest {
    private static final SourceText SOURCE = new SourceText(new SourceId("memory:/inv.ili"),
            new SourceRevision(1), """
            INTERLIS 2.4;
            MODEL Inv AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Item =
                  value : 0..9;
                END Item;
              END Data;
            END Inv.
            """);

    @Test
    void requestsRejectEmptyRootsAndMismatchedSourceKeys() {
        SourceSet set = SourceSet.of(Map.of(SOURCE.id(), SOURCE));
        assertThrows(IllegalArgumentException.class, () -> new CompilationRequest(
                List.of(), set, CompilerOptions.defaults()));
        assertThrows(IllegalArgumentException.class, () -> SourceSet.of(Map.of(
                new SourceId("memory:/other.ili"), SOURCE)));
    }

    @Test
    void analysisSnapshotRejectsMismatchedGraphSnapshot() {
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Inv", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(SOURCE.id(), SOURCE)), CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        var analysis = succeeded.analysis();
        assertThrows(IllegalArgumentException.class, () -> new AnalysisSnapshot(
                new SnapshotId("snap-other"), analysis.semanticGraph(),
                analysis.sourceRevisions(), analysis.syntaxSnapshots(),
                analysis.diagnostics(), analysis.coverage(), analysis.fingerprint(),
                analysis.provenance()));
    }
}
