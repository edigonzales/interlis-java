package guru.interlis.compiler;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The closed outcome distinguishes success, rejection and cancellation. */
class OutcomeContractTest {
    private static final String VALID = """
            INTERLIS 2.4;
            MODEL Outcome AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Item =
                  value : 0..9;
                END Item;
              END Data;
            END Outcome.
            """;

    @Test
    void successRejectionAndCancellationRemainDistinct() {
        SourceText valid = new SourceText(new SourceId("memory:/outcome.ili"),
                new SourceRevision(1), VALID);
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                compile(valid, CancellationToken.none()));
        assertEquals(succeeded.analysis().snapshotId(), succeeded.schema().snapshotId());

        SourceText broken = new SourceText(new SourceId("memory:/broken.ini"),
                new SourceRevision(1), VALID.replace("value : 0..9;", "value : ;"));
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class,
                compile(broken, CancellationToken.none()));
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));

        CompilationOutcome.Cancelled cancelled = assertInstanceOf(
                CompilationOutcome.Cancelled.class,
                compile(valid, CancellationToken.requested(CancellationReason.USER_REQUESTED)));
        assertEquals(CancellationReason.USER_REQUESTED, cancelled.reason());
        assertTrue(cancelled.analysis().isEmpty());
    }

    private static CompilationOutcome compile(SourceText source, CancellationToken token) {
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Outcome", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, token);
    }
}
