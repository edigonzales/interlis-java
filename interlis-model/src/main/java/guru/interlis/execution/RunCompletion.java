package guru.interlis.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Neutral terminal summary, free of compiler result classes. */
public sealed interface RunCompletion permits RunCompletion.Succeeded,
        RunCompletion.Rejected, RunCompletion.Cancelled, RunCompletion.Failed {

    boolean compiledSchemaProduced();

    int errorCount();

    record Succeeded(int diagnosticCount, int rootCount, boolean analysisComplete,
                     boolean compiledSchemaProduced)
            implements RunCompletion {
        /** Creates a validated instance. */
        public Succeeded {
            if (diagnosticCount < 0 || rootCount < 0) {
                throw new IllegalArgumentException("counts must be non-negative");
            }
            if (compiledSchemaProduced && !analysisComplete) {
                throw new IllegalArgumentException("a schema requires a complete analysis");
            }
        }

        /** Compatibility constructor for successful compiler-gate runs. */
        public Succeeded(int diagnosticCount, int rootCount, boolean analysisComplete) {
            this(diagnosticCount, rootCount, analysisComplete, true);
        }

        @Override
        public int errorCount() {
            return diagnosticCount;
        }
    }

    record Rejected(Set<String> reasons, List<String> diagnosticIds,
                    List<String> unsupportedFeatureIds, int errorCount,
                    boolean analysisComplete) implements RunCompletion {
        /** Creates a validated instance. */
        public Rejected {
            reasons = Set.copyOf(Objects.requireNonNull(reasons, "reasons"));
            diagnosticIds = List.copyOf(Objects.requireNonNull(diagnosticIds, "diagnosticIds"));
            unsupportedFeatureIds = List.copyOf(
                    Objects.requireNonNull(unsupportedFeatureIds, "unsupportedFeatureIds"));
            if (errorCount < 0) {
                throw new IllegalArgumentException("errorCount must be non-negative");
            }
        }

        @Override
        public boolean compiledSchemaProduced() {
            return false;
        }
    }

    record Cancelled(CancellationReason reason, Optional<String> snapshotId, int errorCount)
            implements RunCompletion {
        /** Creates a validated instance. */
        public Cancelled {
            Objects.requireNonNull(reason, "reason");
            snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
            if (errorCount < 0) {
                throw new IllegalArgumentException("errorCount must be non-negative");
            }
        }

        @Override
        public boolean compiledSchemaProduced() {
            return false;
        }
    }

    record Failed(FailureId failureId, int errorCount) implements RunCompletion {
        /** Creates a validated instance. */
        public Failed {
            Objects.requireNonNull(failureId, "failureId");
            if (errorCount < 0) {
                throw new IllegalArgumentException("errorCount must be non-negative");
            }
        }

        @Override
        public boolean compiledSchemaProduced() {
            return false;
        }
    }
}
