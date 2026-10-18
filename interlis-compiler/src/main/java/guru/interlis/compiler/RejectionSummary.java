package guru.interlis.compiler;

import guru.interlis.diagnostics.DiagnosticId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Gate explanation, restricted to the requested root closure.
 *
 * @param reasons rejection reasons
 * @param diagnosticIds the {@code diagnosticIds} component
 * @param requestedRoots the {@code requestedRoots} component
 * @param unsupportedFeatureIds the {@code unsupportedFeatureIds} component
 */
public record RejectionSummary(Set<RejectionReason> reasons,
                               List<DiagnosticId> diagnosticIds,
                               List<ModelSelector> requestedRoots,
                               List<String> unsupportedFeatureIds) {
    /** Creates a validated instance. */
    public RejectionSummary {
        reasons = Set.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("rejection summary needs at least one reason");
        }
        Set<RejectionReason> ordered = new LinkedHashSet<>();
        for (RejectionReason reason : RejectionReason.values()) {
            if (reasons.contains(reason)) {
                ordered.add(reason);
            }
        }
        reasons = Collections.unmodifiableSet(ordered);
        diagnosticIds = List.copyOf(Objects.requireNonNull(diagnosticIds, "diagnosticIds"));
        requestedRoots = List.copyOf(Objects.requireNonNull(requestedRoots, "requestedRoots"));
        if (requestedRoots.isEmpty()) {
            throw new IllegalArgumentException("rejection summary needs requested roots");
        }
        unsupportedFeatureIds = List.copyOf(
                Objects.requireNonNull(unsupportedFeatureIds, "unsupportedFeatureIds"));
        if (!reasons.contains(RejectionReason.INCOMPLETE_SUPPORT)
                && !unsupportedFeatureIds.isEmpty()) {
            throw new IllegalArgumentException("feature ids require INCOMPLETE_SUPPORT");
        }
    }
}
