package guru.interlis.compiler;

import java.util.List;
import java.util.Objects;

/** A resolver's decision for a named root in the supplied source revisions. */
public sealed interface RootResolution permits RootResolution.Resolved,
        RootResolution.Missing, RootResolution.Ambiguous {
    /** The compiler verifies this exact declaration against its own syntax. */
    record Resolved(ModelLocation target) implements RootResolution {
        public Resolved {
            Objects.requireNonNull(target, "target");
        }
    }

    /** The resolver found no declaration allowed by the selection policy. */
    record Missing() implements RootResolution {}

    /** Multiple distinct candidates in resolver order. */
    record Ambiguous(List<ModelLocation> candidates) implements RootResolution {
        public Ambiguous {
            candidates = List.copyOf(candidates);
            if (candidates.size() < 2 || candidates.stream().distinct().count() != candidates.size()) {
                throw new IllegalArgumentException("ambiguous root requires distinct candidates");
            }
        }
    }
}
