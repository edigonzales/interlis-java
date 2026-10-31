package guru.interlis.compiler;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.model.AnalysisCoverage;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.query.DefaultSemanticQueries;
import guru.interlis.model.query.SemanticQueries;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.syntax.SyntaxSnapshot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, possibly partial analysis of one frozen input state.
 *
 * <p>Contains the complete diagnostic list, explicit coverage, syntax snapshots
 * and provenance. It is not a {@link CompiledSchema}; only the gate may create
 * a compiled schema from an analysis.</p>
 */
public final class AnalysisSnapshot {
    private final SnapshotId snapshotId;
    private final SemanticGraph semanticGraph;
    private final SemanticQueries queries;
    private final Map<SourceId, SourceRevision> sourceRevisions;
    private final Map<SourceId, SyntaxSnapshot> syntaxSnapshots;
    private final java.util.List<Diagnostic> diagnostics;
    private final java.util.List<Diagnostic> requestDiagnostics;
    private final AnalysisCoverage coverage;
    private final CompilationFingerprint fingerprint;
    private final AnalysisProvenance provenance;

    /**
     * Creates an immutable analysis snapshot.
     *
     * @param snapshotId snapshot identity
     * @param semanticGraph graph with the same snapshot identity
     * @param sourceRevisions captured source revisions
     * @param syntaxSnapshots captured syntax snapshots
     * @param diagnostics complete canonical diagnostic list
     * @param requestDiagnostics canonical ordered subset for this snapshot’s roots/options/revisions
     * @param coverage explicit analysis coverage
     * @param fingerprint canonical request fingerprint
     * @param provenance mode/reuse provenance
     */
    public AnalysisSnapshot(SnapshotId snapshotId, SemanticGraph semanticGraph,
                            Map<SourceId, SourceRevision> sourceRevisions,
                            Map<SourceId, SyntaxSnapshot> syntaxSnapshots,
                            java.util.List<Diagnostic> diagnostics, java.util.List<Diagnostic> requestDiagnostics,
                            AnalysisCoverage coverage,
                            CompilationFingerprint fingerprint, AnalysisProvenance provenance) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.semanticGraph = Objects.requireNonNull(semanticGraph, "semanticGraph");
        if (!snapshotId.equals(semanticGraph.snapshotId())) {
            throw new IllegalArgumentException("graph snapshot does not match analysis snapshot");
        }
        this.queries = new DefaultSemanticQueries(semanticGraph);
        this.sourceRevisions = Map.copyOf(
                Objects.requireNonNull(sourceRevisions, "sourceRevisions"));
        this.syntaxSnapshots = Map.copyOf(
                Objects.requireNonNull(syntaxSnapshots, "syntaxSnapshots"));
        this.diagnostics = java.util.List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        this.requestDiagnostics = java.util.List.copyOf(Objects.requireNonNull(requestDiagnostics, "requestDiagnostics"));
        int next = 0;
        for (Diagnostic diagnostic : this.requestDiagnostics) {
            while (next < this.diagnostics.size() && !this.diagnostics.get(next).equals(diagnostic)) next++;
            if (next == this.diagnostics.size())
                throw new IllegalArgumentException("request diagnostics must be an ordered subset of complete diagnostics");
            next++;
        }
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    /** Returns the immutable snapshot identity. */
    public SnapshotId snapshotId() {
        return snapshotId;
    }

    /** Returns the immutable semantic graph. */
    public SemanticGraph semanticGraph() {
        return semanticGraph;
    }

    /** Returns read-only queries bound to this snapshot. */
    public SemanticQueries queries() {
        return queries;
    }

    /** Returns source revisions captured by the analysis. */
    public Map<SourceId, SourceRevision> sourceRevisions() {
        return sourceRevisions;
    }

    /** Returns the immutable syntax snapshots by source. */
    public Map<SourceId, SyntaxSnapshot> syntaxSnapshots() {
        return syntaxSnapshots;
    }

    /** Returns the syntax snapshot for one source, if present. */
    public Optional<SyntaxSnapshot> syntaxSnapshot(SourceId source) {
        return Optional.ofNullable(syntaxSnapshots.get(Objects.requireNonNull(source, "source")));
    }

    /** Returns the complete canonical diagnostic list. */
    public java.util.List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * Returns the immutable canonical diagnostic subset for exactly this snapshot's
     * roots, options, source revisions and fingerprint. Values/IDs are shared with
     * {@link #diagnostics()}; warnings and unlocated request problems are retained.
     * This is the gate's selection, not presentation suppression. No IO is performed.
     */
    public java.util.List<Diagnostic> requestDiagnostics() {
        return requestDiagnostics;
    }

    /** Returns the explicit analysis coverage. */
    public AnalysisCoverage coverage() {
        return coverage;
    }

    /** Returns the canonical request fingerprint. */
    public CompilationFingerprint fingerprint() {
        return fingerprint;
    }

    /** Returns mode/reuse provenance. */
    public AnalysisProvenance provenance() {
        return provenance;
    }
}
