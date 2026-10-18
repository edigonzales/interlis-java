package guru.interlis.model;

import guru.interlis.diagnostics.Phase;
import guru.interlis.source.SourceId;

import java.util.Objects;
import java.util.Set;

/**
 * Exactly the sources and analysis phases for which completeness is claimed.
 *
 * @param sources source identifiers
 * @param requiredPhases required analysis phases
 */
public record ClosureSelection(Set<SourceId> sources, Set<Phase> requiredPhases) {
    /** Creates a validated instance. */
    public ClosureSelection {
        sources = Set.copyOf(Objects.requireNonNull(sources, "sources"));
        requiredPhases = Set.copyOf(Objects.requireNonNull(requiredPhases, "requiredPhases"));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("closure selection needs at least one source");
        }
        if (requiredPhases.isEmpty()) {
            throw new IllegalArgumentException("closure selection needs required phases");
        }
    }
}
