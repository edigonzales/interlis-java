package guru.interlis.workspace;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LOG-010/LSP-009: independent workspaces do not share current state. */
class WorkspaceIsolationTest {
    @Test
    void parallelWorkspacesKeepIndependentRevisionsAndAnalyses() throws Exception {
        Workspace first = WorkspaceTestSupport.workspace();
        Workspace second = WorkspaceTestSupport.workspace();
        try {
            WorkspaceTestSupport.add(first, "memory:/first.ili", model("FirstModel"));
            WorkspaceTestSupport.add(second, "memory:/second.ili", model("SecondModel"));
            CompletableFuture<AnalysisSnapshot> firstAnalysis = CompletableFuture.supplyAsync(
                    () -> first.analyze(request(first, "FirstModel"), CancellationToken.none()));
            CompletableFuture<AnalysisSnapshot> secondAnalysis = CompletableFuture.supplyAsync(
                    () -> second.analyze(request(second, "SecondModel"), CancellationToken.none()));
            AnalysisSnapshot a = firstAnalysis.get(5, TimeUnit.SECONDS);
            AnalysisSnapshot b = secondAnalysis.get(5, TimeUnit.SECONDS);
            assertNotEquals(a.snapshotId(), b.snapshotId());
            assertTrue(a.sourceRevisions().keySet().stream()
                    .anyMatch(id -> id.value().contains("first.ili")));
            assertTrue(b.sourceRevisions().keySet().stream()
                    .anyMatch(id -> id.value().contains("second.ili")));
            assertFalse(a.sourceRevisions().keySet().stream()
                    .anyMatch(id -> id.value().contains("second.ili")));
            assertFalse(b.sourceRevisions().keySet().stream()
                    .anyMatch(id -> id.value().contains("first.ili")));
        } finally {
            first.close();
            second.close();
        }
    }

    private static AnalysisRequest request(Workspace workspace, String model) {
        return new AnalysisRequest(workspace.currentRevision(),
                List.of(new ModelSelector.ByName(model, Optional.empty(), Optional.empty())));
    }

    private static String model(String name) {
        return """
                INTERLIS 2.4;
                MODEL %s AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
                    CLASS Item =
                      value : 0..9;
                    END Item;
                  END Data;
                END %s.
                """.formatted(name, name);
    }
}
