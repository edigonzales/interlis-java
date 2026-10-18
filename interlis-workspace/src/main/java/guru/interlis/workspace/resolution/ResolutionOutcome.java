package guru.interlis.workspace.resolution;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.FailureId;

import java.util.Objects;

/** Typed resolver outcome, separate from compiler validity. */
public sealed interface ResolutionOutcome permits ResolutionOutcome.Prepared,
        ResolutionOutcome.ResolutionCancelled, ResolutionOutcome.ResolutionFailed {

    record Prepared(PreparedClosure closure) implements ResolutionOutcome {
        /** Creates a validated instance. */
        public Prepared {
            Objects.requireNonNull(closure, "closure");
        }
    }

    record ResolutionCancelled(CancellationReason reason) implements ResolutionOutcome {
        /** Creates a validated instance. */
        public ResolutionCancelled {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record ResolutionFailed(FailureId failureId, Diagnostic diagnostic) implements ResolutionOutcome {
        /** Creates a validated instance. */
        public ResolutionFailed {
            Objects.requireNonNull(failureId, "failureId");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }
}
