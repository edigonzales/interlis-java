package guru.interlis.projection;

import guru.interlis.execution.CancellationToken;

/** Stateless diagram projection entry. */
public interface DiagramProjector {
    /**
     * Projects a validated selection without mutating the input snapshot.
     *
     * <p>A stale or mixed snapshot selection is rejected. Output order is
     * deterministic and bounded by the options' projection budget.</p>
     *
     * @param input snapshot-bound projection input
     * @param options selection and visibility options
     * @param token cancellation signal
     * @return produced document or explicit rejection
     */
    DiagramProjectionOutcome project(DiagramInput input, DiagramOptions options,
                                     CancellationToken token);
}
