package guru.interlis.compiler;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CancelledAnalysisTest {
    @Test
    void cancelledAnalysisTerminatesWithoutSchema() {
        SourceText source = new SourceText(new SourceId("memory:/cancel.ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL M AT "x" VERSION "1" =
                  TOPIC T =
                    CLASS A = value : TEXT*10; END A;
                  END T;
                END M.
                """);
        CompilationRequest request = new CompilationRequest(List.of(
                new ModelSelector.ByName("M", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        assertThrows(OperationCancelledException.class, () ->
                CompilerEngines.create().analyze(request,
                        CancellationToken.requested(CancellationReason.USER_REQUESTED)));
    }
}
