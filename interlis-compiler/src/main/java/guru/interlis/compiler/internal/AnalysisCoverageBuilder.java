package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AnalysisCoverage;
import guru.interlis.model.CoverageStatus;
import guru.interlis.model.ErrorType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.SyntaxCoverage;
import guru.interlis.syntax.SyntaxSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reports coverage of the requested analysis, using the same diagnostic selection as the snapshot. */
final class AnalysisCoverageBuilder {
    private static final List<Phase> SEMANTIC_PHASES = List.of(
            Phase.DECLARATION_INDEXING, Phase.BINDING, Phase.TYPE_CHECKING, Phase.SEMANTIC_CHECKING);

    private AnalysisCoverageBuilder() { }

    private record Location(Phase phase, Optional<SourceId> source) { }

    static AnalysisCoverage build(Closure closure, Set<SourceId> syntaxSources,
                                  Map<SourceId, SyntaxSnapshot> syntax, List<Diagnostic> diagnostics,
                                  Set<DiagnosticId> resolutionDiagnostics, GraphBuilder.GraphResult graph,
                                  CancellationToken token) {
        var modeledSources = new HashSet<SourceId>();
        closure.models().forEach(model -> modeledSources.add(model.sourceId()));
        var typeFailures = new HashSet<>(graph.fragmentTypeFailures());
        for (var type : graph.graph().types()) {
            token.throwIfCancellationRequested();
            if (type instanceof ErrorType error) typeFailures.addAll(error.diagnosticIds());
        }

        var blockers = new HashMap<Location, DiagnosticId>();
        var unsupportedSources = new HashSet<SourceId>();
        var unsupportedEntries = new ArrayList<AnalysisCoverage.CoverageEntry>();
        for (var diagnostic : diagnostics) {
            token.throwIfCancellationRequested();
            if (diagnostic.severity() != Severity.ERROR || resolutionDiagnostics.contains(diagnostic.id())) continue;
            if (diagnostic.primaryRange().isPresent() && !closure.contains(diagnostic.primaryRange().orElseThrow())) continue;
            var feature = graph.unsupportedFeatures().get(diagnostic.id());
            if (feature != null) {
                unsupportedSources.add(feature.source());
                unsupportedEntries.add(new AnalysisCoverage.CoverageEntry(Phase.SEMANTIC_CHECKING,
                        Optional.of(feature.source()), CoverageStatus.NOT_IMPLEMENTED,
                        Optional.of(feature.feature()), Optional.of(diagnostic.id())));
                continue;
            }
            var source = diagnostic.primaryRange().map(SourceRange::source);
            if (SEMANTIC_PHASES.contains(diagnostic.phase()))
                blockers.putIfAbsent(new Location(diagnostic.phase(), source), diagnostic.id());
            // A generic semantic-rule or binding diagnostic may also have prevented an actual type result.
            if (typeFailures.contains(diagnostic.id()))
                blockers.putIfAbsent(new Location(Phase.TYPE_CHECKING, source), diagnostic.id());
        }

        var entries = new ArrayList<AnalysisCoverage.CoverageEntry>();
        for (var source : syntaxSources) {
            token.throwIfCancellationRequested();
            var snapshot = syntax.get(source);
            if (snapshot == null) continue;
            var coverage = snapshot.coverage();
            var location = Optional.of(source);
            entries.add(entry(Phase.LEXING, location, syntaxStatus(snapshot, Phase.LEXING, coverage.lexicallyComplete())));
            boolean parsed = coverage.parseComplete() && coverage.consumedEntireInput();
            entries.add(entry(Phase.PARSING, location, syntaxStatus(snapshot, Phase.PARSING, parsed)));
            if (!modeledSources.contains(source)) continue;
            for (var phase : SEMANTIC_PHASES) {
                var status = incompleteStatus(coverage);
                if (status == CoverageStatus.COMPLETE) {
                    if (!parsed || !coverage.lexicallyComplete() || blockers.containsKey(new Location(phase, location)))
                        status = CoverageStatus.BLOCKED_BY_ERROR;
                    else if (phase == Phase.SEMANTIC_CHECKING && unsupportedSources.contains(source))
                        status = CoverageStatus.NOT_IMPLEMENTED;
                }
                entries.add(entry(phase, location, status));
            }
        }
        for (var phase : SEMANTIC_PHASES) {
            var diagnostic = blockers.get(new Location(phase, Optional.empty()));
            if (diagnostic != null)
                entries.add(new AnalysisCoverage.CoverageEntry(phase, Optional.empty(), CoverageStatus.BLOCKED_BY_ERROR,
                        Optional.empty(), Optional.of(diagnostic)));
        }
        entries.addAll(unsupportedEntries);
        return new AnalysisCoverage(entries);
    }

    private static CoverageStatus syntaxStatus(SyntaxSnapshot snapshot, Phase phase, boolean structurallyComplete) {
        var status = incompleteStatus(snapshot.coverage());
        if (status != CoverageStatus.COMPLETE) return status;
        boolean error = snapshot.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR && diagnostic.phase() == phase);
        return structurallyComplete && !error ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR;
    }

    private static CoverageStatus incompleteStatus(SyntaxCoverage coverage) {
        if (coverage.cancelled()) return CoverageStatus.CANCELLED;
        if (coverage.budgetExceeded()) return CoverageStatus.BUDGET_EXCEEDED;
        return CoverageStatus.COMPLETE;
    }

    private static AnalysisCoverage.CoverageEntry entry(Phase phase, Optional<SourceId> source, CoverageStatus status) {
        return new AnalysisCoverage.CoverageEntry(phase, source, status, Optional.empty(), Optional.empty());
    }
}
