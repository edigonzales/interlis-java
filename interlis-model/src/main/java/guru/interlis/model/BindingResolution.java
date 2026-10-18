package guru.interlis.model;

import guru.interlis.diagnostics.DiagnosticId;

import java.util.List;
import java.util.Objects;

/** Typed binding result for one reference occurrence. */
public sealed interface BindingResolution permits BindingResolution.BoundTarget,
        BindingResolution.UnresolvedTarget, BindingResolution.InvalidTarget,
        BindingResolution.NotAnalyzedTarget {

    /**
     * Reference resolved to exactly one symbol.
     *
     * @param target resolved target
     */
    record BoundTarget(SymbolId target) implements BindingResolution {
        /** Creates a validated instance. */
        public BoundTarget {
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * Reference could not be resolved.
     *
     * @param reason typed unresolved reason
     * @param candidates deterministic candidate list, possibly empty
     */
    record UnresolvedTarget(UnresolvedReason reason, List<SymbolId> candidates)
            implements BindingResolution {
        /** Creates a validated instance. */
        public UnresolvedTarget {
            Objects.requireNonNull(reason, "reason");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    /**
     * Reference resolved to a target that failed type/semantic checks.
     *
     * @param diagnosticIds at least one diagnostic explaining the invalidity
     */
    record InvalidTarget(List<DiagnosticId> diagnosticIds) implements BindingResolution {
        /** Creates a validated instance. */
        public InvalidTarget {
            diagnosticIds = List.copyOf(Objects.requireNonNull(diagnosticIds, "diagnosticIds"));
            if (diagnosticIds.isEmpty()) {
                throw new IllegalArgumentException("invalid target needs diagnostics");
            }
        }
    }

    /**
     * Reference was intentionally not analyzed.
     *
     * @param reason typed reason for the deferred analysis
     */
    record NotAnalyzedTarget(NotAnalyzedReason reason) implements BindingResolution {
        /** Creates a validated instance. */
        public NotAnalyzedTarget {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
