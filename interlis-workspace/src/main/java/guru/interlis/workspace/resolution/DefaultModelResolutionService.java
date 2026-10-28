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
            List<ModelHeader> roots = selectRoots(request.roots(), inventory);
            List<String> trace = new ArrayList<>();
            List<ResolutionDependency> dependencies = new ArrayList<>();
            List<ResolutionDependency> negative = new ArrayList<>();
            List<PendingSourceRequest> pending = new ArrayList<>();
            List<ImportBinding> bindings = new ArrayList<>();
            Set<ModelHeader> selected = new LinkedHashSet<>(roots);
            Deque<ModelHeader> queue = new ArrayDeque<>(roots);
            while (!queue.isEmpty()) {
                cancellation.throwIfCancellationRequested();
                ModelHeader header = queue.removeFirst();
                // Reuse the captured syntax. Original models contribute dependencies,
                // but TRANSLATION OF does not make their namespaces visible as imports.
                var model = inventory.syntax().get(header.source()).root().models().stream()
                        .filter(m -> m.range().startOffset() == header.declarationOffset()).findFirst().orElseThrow();
                if (model.translationSource().isPresent()) {
                    String original = model.translationSource().orElseThrow().text();
                    List<ModelHeader> candidates = inventory.headers().stream()
                            .filter(h -> h.modelName().equals(original))
                            .filter(h -> model.translationVersion().isEmpty()
                                    || model.translationVersion().orElseThrow().equals(h.modelVersion())).toList();
                    var dependency = new ResolutionDependency(
                            new ModelSelector.ByName(original, model.translationVersion(), Optional.empty()), List.of(),
                            candidates.stream().map(DefaultModelResolutionService::location).toList(),
                            candidates.size() == 1 ? ResolutionDependency.Kind.POSITIVE
                                    : candidates.isEmpty() ? ResolutionDependency.Kind.NEGATIVE : ResolutionDependency.Kind.AMBIGUOUS,
                            inventory.fingerprint());
                    if (candidates.size() == 1) {
                        dependencies.add(dependency);
                        ModelHeader target = candidates.getFirst();
                        trace.add(header.modelName() + " translates " + original + " from " + target.source().value());
                        if (selected.add(target)) queue.addLast(target);
                    } else {
                        negative.add(dependency);
                        pending.add(new PendingSourceRequest(original, model.translationVersion(), List.of(),
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
                    List<ModelHeader> candidates = inventory.headers().stream()
                            .filter(h -> h.modelName().equals(importSyntax.modelName()))
                            .sorted(Comparator.comparing((ModelHeader h) -> h.source().value())
                                    .thenComparingInt(ModelHeader::declarationOffset))
                            .toList();
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
                                + " from " + target.source().value());
                        if (selected.add(target)) {
                            queue.addLast(target);
                        }
                    } else if (candidates.isEmpty()) {
                        ResolutionProblem problem = problem("ILI-RES-0001", site.nameRange(),
                                ResolutionReason.NOT_FOUND, importSyntax.modelName());
                        bindings.add(new ImportBinding(site, new ImportResolution.Missing(problem)));
                        negative.add(new ResolutionDependency(
                                new ModelSelector.ByName(importSyntax.modelName(),
                                        Optional.empty(), Optional.empty()),
                                List.of(site), List.of(), ResolutionDependency.Kind.NEGATIVE,
                                inventory.fingerprint()));
                        pending.add(new PendingSourceRequest(importSyntax.modelName(),
                                Optional.empty(), List.of(), "model not found", List.of(site)));
                        trace.add(header.modelName() + " misses " + importSyntax.modelName());
                    } else {
                        ResolutionProblem problem = problem("ILI-RES-0002", site.nameRange(),
                                ResolutionReason.AMBIGUOUS, importSyntax.modelName());
                        bindings.add(new ImportBinding(site, new ImportResolution.Ambiguous(
                                candidates.stream().map(DefaultModelResolutionService::location)
                                        .toList(), problem)));
                        negative.add(new ResolutionDependency(
                                new ModelSelector.ByName(importSyntax.modelName(),
                                        Optional.empty(), Optional.empty()),
                                List.of(site),
                                candidates.stream().map(DefaultModelResolutionService::location)
                                        .toList(),
                                ResolutionDependency.Kind.AMBIGUOUS, inventory.fingerprint()));
                        pending.add(new PendingSourceRequest(importSyntax.modelName(),
                                Optional.empty(), List.of(), "model name is ambiguous",
                                List.of(site)));
                        trace.add(header.modelName() + " has ambiguous import "
                                + importSyntax.modelName());
                    }
                }
            }
            Map<SourceId, SourceText> sources = new LinkedHashMap<>(inventory.sources());
            SourceSet sourceSet = SourceSet.of(sources, bindings, List.of(),
                    inventory.fingerprint());
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

    /**
     * Returns the {@code nextFailure} value.
     *
     * @return the value
     */
    private static synchronized long nextFailure() {
        return ++failures;
    }

    private List<ModelHeader> selectRoots(List<ModelSelector> selectors, SourceInventory inventory) {
        List<ModelHeader> roots = new ArrayList<>();
        for (ModelSelector selector : selectors) {
            switch (selector) {
                case ModelSelector.BySource bySource -> roots.addAll(inventory.headers().stream()
                        .filter(h -> h.source().equals(bySource.source())).toList());
                case ModelSelector.ByName byName -> roots.addAll(inventory.headers().stream()
                        .filter(h -> h.modelName().equals(byName.modelName()))
                        .filter(h -> byName.modelVersion().isEmpty()
                                || byName.modelVersion().get().equals(h.modelVersion()))
                        .filter(h -> byName.sourceHint().isEmpty()
                                || byName.sourceHint().get().equals(h.source()))
                        .toList());
            }
        }
        return roots.stream().distinct().toList();
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
