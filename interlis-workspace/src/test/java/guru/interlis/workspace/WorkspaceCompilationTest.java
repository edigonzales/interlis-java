package guru.interlis.workspace;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.source.SourceId;
import guru.interlis.syntax.ParseMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceCompilationTest {
    @Test
    void capturedCompilationReportsItsActualStrictInputsAndEarlyCancellation() {
        var id = new SourceId("memory:/root.ili");
        try (var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            workspace.update(new SourceUpdate(id, new SourceExpectation.Absent(), new SourceChange.Replace(
                    "INTERLIS 2.4; MODEL Root AT \"x\" VERSION \"1\" = END Root.")));
            var request = new WorkspaceCompileRequest(workspace.currentRevision(), List.of(new ModelSelector.BySource(id)));
            var contexts = RunContextFactory.defaults();
            var captured = workspace.compileCaptured(request, contexts.createRoot(), CancellationToken.none());
            var success = assertInstanceOf(CompilationOutcome.Succeeded.class, captured.outcome());
            var input = captured.capture().orElseThrow();
            assertEquals(input.request().sources().sources().get(id), success.analysis().syntaxSnapshot(id).orElseThrow().source());
            assertEquals(ParseMode.STRICT, input.reuse().previousSyntax().get(id).mode());
            assertEquals("Root", input.resolution().rootLocations().getFirst().modelName());
            var cancelled = workspace.compileCaptured(request, contexts.createRoot(),
                    CancellationToken.requested(CancellationReason.USER_REQUESTED));
            assertInstanceOf(CompilationOutcome.Cancelled.class, cancelled.outcome());
            assertTrue(cancelled.capture().isEmpty());
        }
    }
}
