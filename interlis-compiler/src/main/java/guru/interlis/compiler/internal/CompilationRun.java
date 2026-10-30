package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisProvenance;
import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationFingerprint;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerServices;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.Phase;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunLifecycle;
import guru.interlis.model.SnapshotId;
import guru.interlis.source.ContentFingerprint;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.syntax.SyntaxSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One run-local compiler execution; mutable state is never published. */
final class CompilationRun {
    private final CompilationRequest request;
    private final AnalysisReuse reuse;
    private final CompilerServices services;
    private final CancellationToken token;
    private final DiagnosticCollector collector;
    private final Map<SourceId, SyntaxSnapshot> syntax = new LinkedHashMap<>();
    CompilationRun(CompilationRequest request, AnalysisReuse reuse, CompilerServices services,
                   CancellationToken token) {
        this.request = Objects.requireNonNull(request, "request");
        this.reuse = Objects.requireNonNull(reuse, "reuse");
        this.services = Objects.requireNonNull(services, "services");
        this.token = Objects.requireNonNull(token, "token");
        this.collector = new DiagnosticCollector(guru.interlis.diagnostics.DiagnosticCatalog.initial(),
                request.options().budget().maxDiagnostics());
    }

    RunOutput execute(ParseMode mode, RunLifecycle lifecycle) {
        SourceSet sourceSet = request.sources();
        verifySourceSet(sourceSet, lifecycle);
        parseSources(sourceSet, mode, lifecycle);
        token.throwIfCancellationRequested();
        IndexedDeclarations declarations = new DeclarationIndexer().index(syntax.values().stream().toList(), reuse);
        var overlays = new OverlayBindings(declarations,request.options().overlays(),collector,token,request.options().budget());
        ModelIndex modelIndex = new ModelIndex(syntax.values().stream().toList());
        RootSelection roots;
        Closure closure;
        var indexPhase = lifecycle.beginPhase(Phase.DECLARATION_INDEXING,
                new guru.interlis.execution.RunEvent.PhaseMetadata(modelIndex.all().size()));
        try {
            RootSelector rootSelector = new RootSelector();
            roots = rootSelector.select(request.roots(), modelIndex, collector);
            ImportBinder importBinder = new ImportBinder();
            closure = importBinder.bind(roots.roots(), modelIndex, sourceSet, collector,overlays);
            if (roots.roots().isEmpty()) {
                closure = new Closure(List.of(), List.of(), java.util.Set.of(), List.of(),
                        closure.diagnostics(), new java.util.LinkedHashSet<>(closure.reasons()));
            }
        } finally {
            indexPhase.finish(guru.interlis.execution.PhaseStatus.SUCCEEDED);
        }

        CompilationFingerprinter fingerprinter = new CompilationFingerprinter();
        CompilationFingerprint fingerprint = fingerprinter.fingerprint(request, reuse);
        SnapshotId snapshotId = new SnapshotId("snap-" + Fingerprints.sha256(fingerprint.value()+"|snapshot-v2|"+mode+"|"
                +reuse.previousAnalysis().map(a->a.snapshotId().value()).orElse("none")));
        token.throwIfCancellationRequested();

        Map<SourceId, java.util.Set<String>> syntaxUnsupported = new LinkedHashMap<>();
        syntax.forEach((id, snapshot) -> {
            var unmatched = new java.util.LinkedHashSet<>(snapshot.coverage().unsupportedFeatures());
            var nodes = new java.util.ArrayDeque<guru.interlis.syntax.SyntaxNode>();
            nodes.add(snapshot.root());
            while (!nodes.isEmpty()) {
                token.throwIfCancellationRequested();
                var node = nodes.removeFirst();
                switch (node) {
                    case guru.interlis.syntax.UnsupportedDeclarationSyntax unsupported -> unmatched.remove(unsupported.featureId());
                    case guru.interlis.syntax.UnsupportedTypeSyntax unsupported -> unmatched.remove(unsupported.featureId());
                    case guru.interlis.syntax.UnsupportedExpressionSyntax unsupported -> unmatched.remove(unsupported.featureId());
                    default -> { }
                }
                nodes.addAll(node.children());
            }
            syntaxUnsupported.put(id, unmatched);
        });
        GraphBuilder builder = new GraphBuilder(declarations, sourceSet, closure, collector,
                snapshotId, syntaxUnsupported, token, request.options().budget(),overlays);
        var bindingPhase = lifecycle.beginPhase(Phase.BINDING,
                new guru.interlis.execution.RunEvent.PhaseMetadata(declarations.all().size()));
        GraphBuilder.GraphResult graphResult;
        try {
            graphResult = builder.build();
        } finally {
            bindingPhase.finish(guru.interlis.execution.PhaseStatus.SUCCEEDED);
        }
        token.throwIfCancellationRequested();
        List<Diagnostic> diagnostics = collector.freeze();
        AnalysisProvenance provenance = new AnalysisProvenance(Fingerprints.COMPILER_VERSION,
                Fingerprints.GRAMMAR_VERSION, Fingerprints.SEMANTIC_PROFILE,
                sourceSet.fingerprint().value(), request.options().fingerprint(),
                sourceFingerprints(sourceSet));
        AnalysisSnapshot analysis = new AnalysisSnapshot(snapshotId, graphResult.graph(),
                revisions(sourceSet), syntax, diagnostics, graphResult.coverage(),
                fingerprint, provenance);
        return new RunOutput(analysis, closure, roots, graphResult);
    }

    private void verifySourceSet(SourceSet sourceSet, RunLifecycle lifecycle) {
        var phase = lifecycle.beginPhase(Phase.SOURCE_RESOLUTION,
                new guru.interlis.execution.RunEvent.PhaseMetadata(sourceSet.sources().size()));
        try {
            long total = 0;
            for (SourceText text : sourceSet.sources().values()) {
                total = Math.addExact(total, text.length());
            }
            if (total > request.options().budget().maxTotalSourceUtf16Length()) {
                throw new AnalysisBudgetExceededException("maxTotalSourceUtf16Length",
                        request.options().budget().maxTotalSourceUtf16Length());
            }
            new SourceSetVerifier().verify(sourceSet, collector);
            collector.importAll(sourceSet.resolutionDiagnostics());
        } finally {
            phase.finish(guru.interlis.execution.PhaseStatus.SUCCEEDED);
        }
    }

    private void parseSources(SourceSet sourceSet, ParseMode mode, RunLifecycle lifecycle) {
        var phase = lifecycle.beginPhase(Phase.LEXING,
                new guru.interlis.execution.RunEvent.PhaseMetadata(sourceSet.sources().size()));
        try {
            List<SourceId> ids = new ArrayList<>(sourceSet.sources().keySet());
            ids.sort(Comparator.comparing(SourceId::value));
            for (SourceId id : ids) {
                token.throwIfCancellationRequested();
                SourceText source = sourceSet.sources().get(id);
                SyntaxSnapshot reused = reuse.previousSyntax().get(id);
                SyntaxSnapshot snapshot;
                if (reused != null
                        && reused.source().revision().equals(source.revision())
                        && reused.source().contentFingerprint().equals(source.contentFingerprint())
                        && reused.options().equals(new ParseOptions(mode, java.util.Optional.empty(),
                                request.options().budget()))) {
                    snapshot = reused;
                } else {
                    snapshot = SyntaxServices.create().parse(source,
                            new ParseOptions(mode, java.util.Optional.empty(),
                                    request.options().budget()), token);
                }
                if(snapshot.coverage().budgetExceeded())throw new guru.interlis.execution.OperationCancelledException(
                        guru.interlis.execution.CancellationReason.RESOURCE_LIMIT);
                syntax.put(id, snapshot);
                collector.importAll(snapshot.diagnostics());
            }
            long tokenCount = syntax.values().stream().mapToLong(s -> s.tokens().size()).sum();
            if (tokenCount > request.options().budget().maxTokens()) {
                throw new AnalysisBudgetExceededException("maxTokens",
                        request.options().budget().maxTokens());
            }
        } finally {
            phase.finish(guru.interlis.execution.PhaseStatus.SUCCEEDED);
        }
    }

    private Map<SourceId, ContentFingerprint> sourceFingerprints(SourceSet sourceSet) {
        Map<SourceId, ContentFingerprint> result = new LinkedHashMap<>();
        sourceSet.sources().forEach((id, text) -> result.put(id, text.contentFingerprint()));
        return result;
    }

    private Map<SourceId, guru.interlis.source.SourceRevision> revisions(SourceSet sourceSet) {
        Map<SourceId, guru.interlis.source.SourceRevision> result = new LinkedHashMap<>();
        sourceSet.sources().forEach((id, text) -> result.put(id, text.revision()));
        return result;
    }

    DiagnosticCollector collector() {
        return collector;
    }
}
