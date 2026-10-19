package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.RejectionReason;
import guru.interlis.compiler.RejectionSummary;
import guru.interlis.compiler.WarningPolicy;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.AnalysisCoverage;
import guru.interlis.model.ClosureSelection;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.CoverageStatus;
import guru.interlis.model.SchemaFingerprint;
import guru.interlis.model.SchemaProvenance;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.ContentFingerprint;
import guru.interlis.source.SourceId;
import guru.interlis.syntax.SyntaxSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Sole producer of a checked schema view. */
final class CompilationGate {
    GateResult evaluate(RunOutput output, CompilationRequest request, boolean strictSyntax) {
        AnalysisSnapshot analysis = output.analysis();
        Closure closure = output.closure();
        RootSelection roots = output.roots();
        Set<RejectionReason> reasons = new LinkedHashSet<>(roots.reasons());
        reasons.addAll(closure.reasons());
        List<Diagnostic> relevant = new ArrayList<>();
        Set<DiagnosticId> relevantIds = new LinkedHashSet<>(roots.diagnostics());
        relevantIds.addAll(closure.diagnostics());
        for (Diagnostic diagnostic : analysis.diagnostics()) {
            boolean inClosure = diagnostic.primaryRange()
                    .map(r -> closure.sources().contains(r.source()))
                    .orElse(relevantIds.contains(diagnostic.id()));
            if (inClosure) {
                relevant.add(diagnostic);
            }
        }
        for (Diagnostic diagnostic : relevant) {
            if (diagnostic.severity() == Severity.WARNING
                    && request.options().warningPolicy() == WarningPolicy.REJECT_WARNINGS) {
                reasons.add(RejectionReason.WARNINGS_REJECTED);
            }
            if (diagnostic.severity() != Severity.ERROR) {
                continue;
            }
            switch (diagnostic.code().value()) {
                case "ILI-RES-0001" -> reasons.add(RejectionReason.MISSING_INPUT);
                case "ILI-RES-0002" -> reasons.add(RejectionReason.AMBIGUOUS_INPUT);
                case "ILI-RES-0003" -> reasons.add(RejectionReason.ENVIRONMENT_FAILURE);
                case "ILI-RES-0004" -> reasons.add(RejectionReason.MISSING_INPUT);
                case "ILI-IMPL-0001" -> reasons.add(RejectionReason.INCOMPLETE_SUPPORT);
                default -> reasons.add(RejectionReason.SOURCE_ERRORS);
            }
        }
        Set<String> unsupported = new LinkedHashSet<>();
        output.graphResult().unsupportedBySource().forEach((source, features) -> {
            if (closure.sources().contains(source)) {
                unsupported.addAll(features);
            }
        });
        for (AnalysisCoverage.CoverageEntry entry : analysis.coverage().entries()) {
            if (entry.source().filter(closure.sources()::contains).isEmpty()
                    && entry.source().isPresent()) {
                continue;
            }
            if (entry.status() == CoverageStatus.NOT_IMPLEMENTED) {
                entry.featureId().ifPresent(unsupported::add);
                reasons.add(RejectionReason.INCOMPLETE_SUPPORT);
            } else if (entry.status() == CoverageStatus.BLOCKED_BY_ERROR) {
                reasons.add(RejectionReason.SOURCE_ERRORS);
            } else if (entry.status() == CoverageStatus.BUDGET_EXCEEDED
                    || entry.status() == CoverageStatus.CANCELLED) {
                reasons.add(RejectionReason.INCOMPLETE_SUPPORT);
            }
        }
        if (!unsupported.isEmpty()) {
            reasons.add(RejectionReason.INCOMPLETE_SUPPORT);
        }
        boolean onlyEmptyDocuments = roots.roots().isEmpty()
                && !analysis.syntaxSnapshots().isEmpty()
                && analysis.syntaxSnapshots().values().stream()
                .allMatch(snapshot -> snapshot.root().models().isEmpty()
                        && snapshot.source().text().trim()
                        .matches("INTERLIS\\s+2\\.[34]\\s*;?"));
        if (roots.roots().isEmpty() && !onlyEmptyDocuments) {
            reasons.add(RejectionReason.MISSING_INPUT);
        }
        if (strictSyntax && !closure.sources().isEmpty()) {
            for (SourceId source : closure.sources()) {
                Optional<SyntaxSnapshot> snapshot = analysis.syntaxSnapshot(source);
                if (snapshot.isEmpty() || !snapshot.get().coverage().parseComplete()
                        || !snapshot.get().consumedEntireInput()
                        || snapshot.get().language().state()
                        != guru.interlis.syntax.DetectionState.DETECTED) {
                    reasons.add(RejectionReason.SOURCE_ERRORS);
                }
            }
            for (guru.interlis.model.LanguageVersion language : analysis.semanticGraph()
                    .languageVersions()) {
                if (!request.options().allowedLanguages().contains(language)) {
                    reasons.add(RejectionReason.SOURCE_ERRORS);
                }
            }
        }
        if (reasons.isEmpty() && !closure.sources().isEmpty()) {
            AnalysisCoverage coverage = analysis.coverage();
            ClosureSelection selection = new ClosureSelection(closure.sources(),
                    Set.of(Phase.LEXING, Phase.PARSING, Phase.DECLARATION_INDEXING,
                            Phase.BINDING, Phase.TYPE_CHECKING, Phase.SEMANTIC_CHECKING));
            if (!coverage.isCompleteFor(selection)) {
                reasons.add(RejectionReason.SOURCE_ERRORS);
            }
        }
        if (!reasons.isEmpty()) {
            List<DiagnosticId> ids = relevant.stream().map(Diagnostic::id)
                    .sorted(Comparator.comparing(DiagnosticId::value)).toList();
            List<String> features = unsupported.stream().sorted().toList();
            RejectionSummary summary = new RejectionSummary(reasons, ids, request.roots(), features);
            return GateResult.denied(summary);
        }
        SemanticGraph subset = analysis.semanticGraph().subsetForModels(
                new LinkedHashSet<>(output.graphResult().models()), output.graphResult().roots());
        SchemaFingerprint fingerprint = new SchemaFingerprint("SHA-256/schema-v1",
                Fingerprints.sha256("schema|" + analysis.fingerprint().value() + "|"
                        + output.graphResult().models()));
        List<SymbolRef> rootRefs = output.graphResult().roots().stream()
                .map(id -> new SymbolRef(analysis.snapshotId(), id)).toList();
        List<SymbolRef> modelRefs = output.graphResult().models().stream()
                .map(id -> new SymbolRef(analysis.snapshotId(), id)).toList();
        Map<SourceId, ContentFingerprint> sourceFingerprints = new LinkedHashMap<>();
        for (SourceId source : closure.sources()) {
            ContentFingerprint content = analysis.provenance().sourceFingerprints().get(source);
            if (content != null) {
                sourceFingerprints.put(source, content);
            }
        }
        List<SchemaProvenance.ModelSelectorView> rootViews = request.roots().stream()
                .map(CompilationGate::view).toList();
        SchemaProvenance provenance = new SchemaProvenance(Fingerprints.COMPILER_VERSION,
                Fingerprints.GRAMMAR_VERSION, Fingerprints.SEMANTIC_PROFILE, rootViews,
                sourceFingerprints, request.sources().fingerprint().value(),
                request.options().fingerprint());
        CompiledSchema schema = new DefaultCompiledSchema(subset, fingerprint, rootRefs,
                modelRefs, provenance);
        return GateResult.accepted(schema);
    }

    private static SchemaProvenance.ModelSelectorView view(ModelSelector selector) {
        return switch (selector) {
            case ModelSelector.ByName byName -> new SchemaProvenance.ModelSelectorView("byName",
                    byName.modelName(), byName.modelVersion(),
                    byName.sourceHint().map(SourceId::value));
            case ModelSelector.BySource bySource -> new SchemaProvenance.ModelSelectorView("bySource",
                    bySource.source().value(), Optional.empty(),
                    Optional.of(bySource.source().value()));
        };
    }
}
