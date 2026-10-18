package guru.interlis.compiler;

import java.util.List;
import java.util.Objects;

/** Typed resolver decision for one import site. */
public sealed interface ImportResolution permits ImportResolution.Resolved,
        ImportResolution.Missing, ImportResolution.Ambiguous, ImportResolution.Blocked {

    /**
     * Import resolved to one target.
     *
     * @param target resolved model location
     */
    record Resolved(ModelLocation target) implements ImportResolution {
        /** Creates a validated instance. */
        public Resolved {
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * Import target is missing.
     *
     * @param problem typed resolution problem
     */
    record Missing(ResolutionProblem problem) implements ImportResolution {
        /** Creates a validated instance. */
        public Missing {
            Objects.requireNonNull(problem, "problem");
        }
    }

    /**
     * Import target is ambiguous.
     *
     * @param candidates at least two candidate locations
     * @param problem typed resolution problem
     */
    record Ambiguous(List<ModelLocation> candidates, ResolutionProblem problem)
            implements ImportResolution {
        /** Creates a validated instance. */
        public Ambiguous {
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            if (candidates.size() < 2) {
                throw new IllegalArgumentException("ambiguous resolution needs two candidates");
            }
            Objects.requireNonNull(problem, "problem");
        }
    }

    /**
     * Import was blocked by a dependency or policy.
     *
     * @param problem typed resolution problem
     */
    record Blocked(ResolutionProblem problem) implements ImportResolution {
        /** Creates a validated instance. */
        public Blocked {
            Objects.requireNonNull(problem, "problem");
        }
    }
}
