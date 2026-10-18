package guru.interlis.model;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.source.SourceId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Explicit analysis coverage; an empty coverage is never implicit completeness.
 *
 * @param entries the {@code entries} component
 */
public record AnalysisCoverage(List<CoverageEntry> entries) {
    /** Creates a validated instance. */
    public AnalysisCoverage {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /**
     * Returns the {@code empty} value.
     *
     * @return the value
     */
    public static AnalysisCoverage empty() {
        return new AnalysisCoverage(List.of());
    }

    /**
     * Returns the coverage entry for a phase/source pair.
     *
     * @param phase analyzed phase
     * @param source optional source; empty denotes global coverage
     * @return matching entry or a {@code NOT_REQUESTED} entry
     */
    public CoverageEntry entry(Phase phase, Optional<SourceId> source) {
        return entries.stream()
                .filter(e -> e.phase() == phase && e.source().equals(source))
                .findFirst()
                .orElse(new CoverageEntry(phase, source, CoverageStatus.NOT_REQUESTED,
                        Optional.empty(), Optional.empty()));
    }

    /**
     * Returns whether every requested phase/source is {@code COMPLETE}.
     *
     * @param selection required closure selection
     * @return {@code true} only if all entries are complete
     */
    public boolean isCompleteFor(ClosureSelection selection) {
        Objects.requireNonNull(selection, "selection");
        for (Phase phase : selection.requiredPhases()) {
            for (SourceId source : selection.sources()) {
                CoverageStatus status = entry(phase, Optional.of(source)).status();
                if (status != CoverageStatus.COMPLETE) {
                    return false;
                }
            }
        }
        for (CoverageEntry entry : entries) {
            if (entry.source().isPresent() && !selection.sources().contains(entry.source().get())) {
                continue;
            }
            switch (entry.status()) {
                case NOT_IMPLEMENTED, CANCELLED, BUDGET_EXCEEDED, BLOCKED_BY_ERROR -> {
                    return false;
                }
                case COMPLETE, NOT_REQUESTED -> {
                    // NOT_REQUESTED for a phase that is not required is legal.
                    if (selection.requiredPhases().contains(entry.phase())
                            && entry.source().isEmpty()
                            && entry.status() == CoverageStatus.NOT_REQUESTED) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Executes {@code unsupportedFeaturesFor}.
     *
     * @param selection the {@code selection} argument
     *
     * @return the result
     */
    public Set<String> unsupportedFeaturesFor(ClosureSelection selection) {
        Set<String> features = new LinkedHashSet<>();
        for (CoverageEntry entry : entries) {
            if (entry.status() == CoverageStatus.NOT_IMPLEMENTED
                    && entry.featureId().isPresent()
                    && (entry.source().isEmpty() || selection.sources().contains(entry.source().get()))) {
                features.add(entry.featureId().get());
            }
        }
        return features;
    }

    /**
     * Returns the {@code incompleteEntries} value.
     *
     * @return the value
     */
    public List<CoverageEntry> incompleteEntries() {
        return entries.stream()
                .filter(e -> e.status() != CoverageStatus.COMPLETE
                        && e.status() != CoverageStatus.NOT_REQUESTED)
                .toList();
    }

    @Override
    public String toString() {
        return entries.stream()
                .map(e -> e.phase() + ":" + e.status())
                .collect(Collectors.joining(",", "AnalysisCoverage[", "]"));
    }

    /**
     * Immutable value.
     *
     * @param phase the {@code phase} component
     * @param source source identifier
     * @param status the {@code status} component
     * @param featureId the {@code featureId} component
     * @param diagnosticId the {@code diagnosticId} component
     */
    public record CoverageEntry(Phase phase, Optional<SourceId> source,
                                CoverageStatus status, Optional<String> featureId,
                                Optional<DiagnosticId> diagnosticId) {
        /** Creates a validated instance. */
        public CoverageEntry {
            Objects.requireNonNull(phase, "phase");
            source = Objects.requireNonNull(source, "source");
            Objects.requireNonNull(status, "status");
            featureId = Objects.requireNonNull(featureId, "featureId");
            diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
        }
    }
}
