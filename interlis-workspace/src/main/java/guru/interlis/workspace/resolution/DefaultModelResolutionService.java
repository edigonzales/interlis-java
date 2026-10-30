package guru.interlis.workspace.resolution;

import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.ImportSite;
import guru.interlis.compiler.ModelLocation;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.ResolutionProblem;
import guru.interlis.compiler.ResolutionReason;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.DiagnosticIds;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.FailureId;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic pure resolver over an in-memory inventory. */
public final class DefaultModelResolutionService implements ModelResolutionService {
    private static long failures;
    @Override
    public ResolutionOutcome prepare(ResolutionRequest request, SourceProvider provider,
                                     CancellationToken cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(cancellation, "cancellation");
        SourceInventory inventory = Objects.requireNonNull(provider.snapshot(), "snapshot");
        if (!request.expectedInventoryFingerprint().equals(inventory.fingerprint().value())) {
            throw new StaleInputException(request.expectedInventoryFingerprint(),
                    inventory.fingerprint().value());
        }
        try {
            cancellation.throwIfCancellationRequested();
            Work work = new Work(request.budget().maxSemanticWorkUnits(), cancellation);
            List<ModelHeader> roots = selectRoots(request, inventory, work);
            List<String> trace = new ArrayList<>();
            List<ResolutionDependency> dependencies = new ArrayList<>();
            List<ResolutionDependency> negative = new ArrayList<>();
            List<PendingSourceRequest> pending = new ArrayList<>();
            List<ImportBinding> bindings = new ArrayList<>();
            List<guru.interlis.compiler.TranslationBinding> translations = new ArrayList<>();
            for (ModelSelector root : request.roots()) {
                work.scan(inventory.headers().size());
                if (root instanceof ModelSelector.ByName name) {
                    var query = request.policy().qualify(name);
                    var found = request.policy().select(name, inventory.headers());
                    if (found.size() != 1) {
                        var dependency = new ResolutionDependency(query, List.of(),
                                found.stream().map(DefaultModelResolutionService::location).toList(),
                                found.isEmpty() ? ResolutionDependency.Kind.NEGATIVE : ResolutionDependency.Kind.AMBIGUOUS,
                                inventory.fingerprint());
                        negative.add(dependency);
                        pending.add(new PendingSourceRequest(query.modelName(), query.modelVersion(),
                                query.sourceHint().map(v -> List.of(v.value())).orElse(List.of()),
                                found.isEmpty() ? "root not found" : "root is ambiguous", List.of()));
                    }
                } else if (root instanceof ModelSelector.BySource source
                        && !inventory.sources().containsKey(source.source())) {
                    pending.add(new PendingSourceRequest("", Optional.empty(), List.of(source.source().value()),
                            "root source not found", List.of()));
                }
            }
            Set<ModelHeader> selected = new LinkedHashSet<>(roots);
            Deque<ModelHeader> queue = new ArrayDeque<>(roots);
            while (!queue.isEmpty()) {
                cancellation.throwIfCancellationRequested();
                work.scan(1);
                ModelHeader header = queue.removeFirst();
                // Reuse the captured syntax. Original models contribute dependencies,
                // but TRANSLATION OF does not make their namespaces visible as imports.
                var model = inventory.syntax().get(header.source()).root().models().stream()
                        .filter(m -> m.range().startOffset() == header.declarationOffset()).findFirst().orElseThrow();
                work.scan(request.overlays().overlays().size());
                var originalDependencies = translationDependencies(header, model, request);
                for(var originalSelector:originalDependencies) {
                    String original = originalSelector.modelName();
                    work.scan(inventory.headers().size());
                    originalSelector = request.policy().qualify(originalSelector);
                    List<ModelHeader> candidates = request.policy().select(originalSelector, inventory.headers());
                    var dependency = new ResolutionDependency(
                            originalSelector, List.of(),
                            candidates.stream().map(DefaultModelResolutionService::location).toList(),
                            candidates.size() == 1 ? ResolutionDependency.Kind.POSITIVE
                                    : candidates.isEmpty() ? ResolutionDependency.Kind.NEGATIVE : ResolutionDependency.Kind.AMBIGUOUS,
                            inventory.fingerprint());
                    if (candidates.size() == 1) {
                        dependencies.add(dependency);
                        ModelHeader target = candidates.getFirst();
                        if (originalDependencies.size() == 1) translations.add(new guru.interlis.compiler.TranslationBinding(location(header), location(target)));
                        trace.add(header.modelName() + " translates " + original + " from " + safeSource(target.source()));
                        if (selected.add(target)) queue.addLast(target);
                    } else {
                        negative.add(dependency);
                        pending.add(new PendingSourceRequest(original, originalSelector.modelVersion(), List.of(),
                                candidates.isEmpty() ? "translation original not found" : "translation original is ambiguous", List.of()));
                        trace.add(header.modelName() + " has unresolved translation original " + original);
                    }
                }
                for (var importSyntax : header.imports()) {
                    if ("INTERLIS".equals(importSyntax.modelName())) {
                        trace.add("builtin INTERLIS for " + header.modelName());
                        continue;
                    }
                    ImportSite site = new ImportSite(importSyntax.range(), header.modelName(),
                            importSyntax.modelName());
                    work.scan(inventory.headers().size());
                    var query = request.policy().qualify(new ModelSelector.ByName(importSyntax.modelName(),
                            Optional.empty(), Optional.empty()));
                    List<ModelHeader> candidates = request.policy().select(query, inventory.headers());
                    if (candidates.size() == 1) {
                        ModelHeader target = candidates.getFirst();
                        bindings.add(new ImportBinding(site, new ImportResolution.Resolved(
                                location(target))));
                        dependencies.add(new ResolutionDependency(
                                new ModelSelector.ByName(target.modelName(),
                                        Optional.of(target.modelVersion()),
                                        Optional.of(target.source())),
                                List.of(site), List.of(location(target)),
                                ResolutionDependency.Kind.POSITIVE, inventory.fingerprint()));
                        trace.add(header.modelName() + " imports " + target.modelName()
                                + " from " + safeSource(target.source()));
                        if (selected.add(target)) {
                            queue.addLast(target);
                        }
                    } else if (candidates.isEmpty()) {
                        ResolutionProblem problem = problem("ILI-RES-0001", site.nameRange(),
                                ResolutionReason.NOT_FOUND, importSyntax.modelName());
                        bindings.add(new ImportBinding(site, new ImportResolution.Missing(problem)));
                        negative.add(new ResolutionDependency(
                                query,
                                List.of(site), List.of(), ResolutionDependency.Kind.NEGATIVE,
                                inventory.fingerprint()));
                        pending.add(new PendingSourceRequest(importSyntax.modelName(),
                                query.modelVersion(), query.sourceHint().map(v -> List.of(v.value())).orElse(List.of()), "model not found", List.of(site)));
                        trace.add(header.modelName() + " misses " + importSyntax.modelName());
                    } else {
                        ResolutionProblem problem = problem("ILI-RES-0002", site.nameRange(),
                                ResolutionReason.AMBIGUOUS, importSyntax.modelName());
                        bindings.add(new ImportBinding(site, new ImportResolution.Ambiguous(
                                candidates.stream().map(DefaultModelResolutionService::location)
                                        .toList(), problem)));
                        negative.add(new ResolutionDependency(
                                query,
                                List.of(site),
                                candidates.stream().map(DefaultModelResolutionService::location)
                                        .toList(),
                                ResolutionDependency.Kind.AMBIGUOUS, inventory.fingerprint()));
                        pending.add(new PendingSourceRequest(importSyntax.modelName(),
                                query.modelVersion(), List.of(), "model name is ambiguous",
                                List.of(site)));
                        trace.add(header.modelName() + " has ambiguous import "
                                + importSyntax.modelName());
                    }
                }
            }
            dependencies.addAll(negative);
            Map<SourceId, SourceText> sources = new LinkedHashMap<>(inventory.sources());
            SourceSet sourceSet = SourceSet.of(sources, bindings, List.of(),
                    resolutionFingerprint(request, inventory), translations);
            if (pending.isEmpty() && !roots.isEmpty()) {
                return new ResolutionOutcome.Prepared(new ResolvedClosure(sourceSet,
                        new ResolutionTrace(trace), dependencies));
            }
            return new ResolutionOutcome.Prepared(new IncompleteClosure(sourceSet, pending,
                    new ResolutionTrace(trace), dependencies, negative));
        } catch (OperationCancelledException cancelled) {
            return new ResolutionOutcome.ResolutionCancelled(cancelled.reason());
        } catch (RuntimeException failure) {
            FailureId failureId = new FailureId("resolution-failure-"
                    + nextFailure());
            Diagnostic diagnostic = new Diagnostic(new DiagnosticId("diag-" + failureId.value()),
                    new DiagnosticCode("ILI-RES-0003"), Severity.ERROR,
                    Phase.SOURCE_RESOLUTION, "diagnostic.resolution.source",
                    List.of(new DiagnosticArgument.TextArgument("source", "in-memory inventory"),
                            new DiagnosticArgument.TextArgument("reason",
                                    failure.getClass().getSimpleName())),
                    Optional.empty(), List.of(), Optional.empty(), List.of(), Optional.empty(),
                    Optional.empty(), List.of());
            return new ResolutionOutcome.ResolutionFailed(failureId, diagnostic);
        }
    }

    private List<ModelSelector.ByName> translationDependencies(ModelHeader header,guru.interlis.syntax.ModelSyntax model,ResolutionRequest request) {
        var selected=new ArrayList<guru.interlis.compiler.SemanticOverlays.SemanticOverlay.TranslationOverlay>();
        for(var overlay:request.overlays().overlays())if(overlay instanceof guru.interlis.compiler.SemanticOverlays.SemanticOverlay.TranslationOverlay t
                && t.target().name().segments().size()==1 && t.target().name().last().equals(header.modelName())
                && t.target().modelVersion().filter(v->!v.equals(header.modelVersion())).isEmpty()
                && t.target().sourceHint().filter(v->!v.equals(header.source())).isEmpty())selected.add(t);
        int priority=selected.stream().mapToInt(t->t.source().priority()).max().orElse(Integer.MIN_VALUE);
        if(!selected.isEmpty())return selected.stream().filter(t->t.source().priority()==priority && t.original().name().segments().size()==1)
                .map(t->new ModelSelector.ByName(t.original().name().last(),t.original().modelVersion(),t.original().sourceHint())).distinct().toList();
        return model.translationSource().map(n->List.of(new ModelSelector.ByName(n.text(),model.translationVersion(),Optional.empty()))).orElse(List.of());
    }

    /**
     * Returns the {@code nextFailure} value.
     *
     * @return the value
     */
    private static synchronized long nextFailure() {
        return ++failures;
    }

    private List<ModelHeader> selectRoots(ResolutionRequest request, SourceInventory inventory, Work work) {
        List<ModelHeader> roots = new ArrayList<>();
        for (ModelSelector selector : request.roots()) {
            work.scan(inventory.headers().size());
            switch (selector) {
                case ModelSelector.BySource source -> roots.addAll(inventory.headers().stream()
                        .filter(h -> h.source().equals(source.source())).toList());
                case ModelSelector.ByName name -> roots.addAll(request.policy().select(name, inventory.headers()));
            }
        }
        return roots.stream().distinct().toList();
    }

    private static guru.interlis.compiler.ResolutionFingerprint resolutionFingerprint(ResolutionRequest request, SourceInventory inventory) {
        var b = new StringBuilder("resolved-input-v2");
        field(b, inventory.fingerprint().value()); field(b, request.policy().fingerprint());
        field(b, Integer.toString(request.roots().size()));
        for (var root : request.roots()) switch (root) {
            case ModelSelector.BySource source -> { field(b, "source"); field(b, source.source().value()); }
            case ModelSelector.ByName name -> {
                field(b, "name"); field(b, name.modelName());
                field(b, Boolean.toString(name.modelVersion().isPresent())); name.modelVersion().ifPresent(v -> field(b, v));
                field(b, Boolean.toString(name.sourceHint().isPresent())); name.sourceHint().ifPresent(v -> field(b, v.value()));
            }
        }
        field(b, request.overlays().fingerprint());
        return new guru.interlis.compiler.ResolutionFingerprint("sha256:" + guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digestUtf16(b.toString())));
    }
    private static void field(StringBuilder b, String text) { b.append(text.length()).append(':').append(text); }

    private static String safeSource(SourceId source) {
        // Source identities remain exact; explanatory traces omit credentials/query fragments.
        String value = source.value();
        int suffix = value.indexOf('?');
        if (suffix >= 0) value = value.substring(0, suffix);
        suffix = value.indexOf('#');
        if (suffix >= 0) value = value.substring(0, suffix);
        int authority = value.indexOf("://");
        int at = authority < 0 ? -1 : value.indexOf('@', authority + 3);
        int slash = authority < 0 ? -1 : value.indexOf('/', authority + 3);
        if (at >= 0 && (slash < 0 || at < slash)) value = value.substring(0, authority + 3) + value.substring(at + 1);
        return value;
    }

    private static final class Work {
        private long remaining;
        private final CancellationToken token;
        Work(long remaining, CancellationToken token) { this.remaining = remaining; this.token = token; }
        void scan(long count) {
            token.throwIfCancellationRequested();
            if (count > remaining) throw new OperationCancelledException(guru.interlis.execution.CancellationReason.RESOURCE_LIMIT);
            remaining -= count;
        }
    }

    private static ModelLocation location(ModelHeader header) {
        return new ModelLocation(header.source(), header.revision(), header.modelName(),
                Optional.of(header.modelVersion()), header.declarationOffset());
    }

    private static ResolutionProblem problem(String code, guru.interlis.source.SourceRange range,
                                             ResolutionReason reason, String detail) {
        DiagnosticId id = DiagnosticIds.create(new DiagnosticCode(code), Phase.SOURCE_RESOLUTION,
                Optional.of(range), "diagnostic.resolution", List.of());
        return new ResolutionProblem(id, reason, detail);
    }
}
