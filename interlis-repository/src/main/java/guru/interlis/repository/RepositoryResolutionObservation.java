package guru.interlis.repository;

import java.util.Objects;

/**
 * Immutable operational observations of one resolution invocation, independent of
 * compiler diagnostics and gate decisions. Delivery is synchronous in actual
 * occurrence order; historical lock steps are never replayed. No source text,
 * response headers or credentials are included.
 */
public sealed interface RepositoryResolutionObservation {
    /** A current, non-null search decision with sanitized repository/source addresses. */
    record SearchStep(RepositorySearchStep step) implements RepositoryResolutionObservation {
        public SearchStep { Objects.requireNonNull(step, "step"); }
    }

    /** Emitted once before a nonempty online completion check, never for an offline invocation. */
    record VerificationStarted() implements RepositoryResolutionObservation { }
}
