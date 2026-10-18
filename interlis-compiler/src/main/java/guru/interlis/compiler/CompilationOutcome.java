package guru.interlis.compiler;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.FailureId;
import guru.interlis.model.CompiledSchema;

import java.util.Objects;
import java.util.Optional;

/** Closed compiler result with four distinguishable terminal states. */
public sealed interface CompilationOutcome permits CompilationOutcome.Succeeded,
        CompilationOutcome.Rejected, CompilationOutcome.Cancelled, CompilationOutcome.Failed {

    /**
     * Successful compile with a checked schema.
     *
     * @param schema schema restricted to the requested closure
     * @param analysis analysis snapshot that produced the schema
     */
    record Succeeded(CompiledSchema schema, AnalysisSnapshot analysis)
            implements CompilationOutcome {
        /** Creates a validated instance. */
        public Succeeded {
            Objects.requireNonNull(schema, "schema");
            Objects.requireNonNull(analysis, "analysis");
            if (!schema.snapshotId().equals(analysis.snapshotId())) {
                throw new IllegalArgumentException("schema and analysis snapshot differ");
            }
        }
    }

    /**
     * Modelled rejection of the requested closure.
     *
     * @param analysis analysis that explains the rejection
     * @param rejection typed reasons and diagnostic ids
     */
    record Rejected(AnalysisSnapshot analysis, RejectionSummary rejection)
            implements CompilationOutcome {
        /** Creates a validated instance. */
        public Rejected {
            Objects.requireNonNull(analysis, "analysis");
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /**
     * Controlled cancellation; no schema is produced.
     *
     * @param analysis partial analysis if one was captured
     * @param reason typed cancellation reason
     */
    record Cancelled(Optional<AnalysisSnapshot> analysis, CancellationReason reason)
            implements CompilationOutcome {
        /** Creates a validated instance. */
        public Cancelled {
            analysis = Objects.requireNonNull(analysis, "analysis");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Internal failure with a stable failure identity.
     *
     * @param failureId stable failure id
     * @param error diagnostic describing the internal defect
     */
    record Failed(FailureId failureId, Diagnostic error) implements CompilationOutcome {
        /** Creates a validated instance. */
        public Failed {
            Objects.requireNonNull(failureId, "failureId");
            Objects.requireNonNull(error, "error");
        }
    }
}
