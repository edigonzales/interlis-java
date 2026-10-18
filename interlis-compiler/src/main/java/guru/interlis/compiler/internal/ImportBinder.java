package guru.interlis.compiler.internal;

import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.RejectionReason;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.source.SourceId;
import guru.interlis.syntax.ImportSyntax;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Verifies resolver bindings and walks the import closure. */
final class ImportBinder {
    Closure bind(List<ModelDecl> roots, ModelIndex index, SourceSet sourceSet,
                 DiagnosticCollector collector) {
        Set<ModelDecl> models = new LinkedHashSet<>();
        Set<SourceId> sources = new LinkedHashSet<>();
        List<Closure.ImportEdge> edges = new ArrayList<>();
        List<DiagnosticId> diagnostics = new ArrayList<>();
        Set<RejectionReason> reasons = new LinkedHashSet<>();
        Map<String, ImportBinding> bindings = bindingsBySite(sourceSet);
        Deque<ModelDecl> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            ModelDecl model = queue.removeFirst();
            if (!models.add(model)) {
                continue;
            }
            sources.add(model.sourceId());
            for (ImportSyntax site : model.syntax().imports()) {
                if ("INTERLIS".equals(site.modelName())) {
                    continue;
                }
                ImportBinding binding = bindings.get(siteKey(model, site));
                if (binding == null) {
                    DiagnosticId id = missing(site, collector);
                    diagnostics.add(id);
                    reasons.add(RejectionReason.MISSING_INPUT);
                    continue;
                }
                switch (binding.resolution()) {
                    case ImportResolution.Resolved resolved -> {
                        Optional<ModelDecl> target = index.atLocation(
                                resolved.target().source(),
                                resolved.target().revision().value(),
                                resolved.target().modelName(),
                                resolved.target().modelVersion(),
                                resolved.target().declarationOffset());
                        if (target.isEmpty()) {
                            DiagnosticId id = collector.emit(DiagnosticDraft.of(
                                            new DiagnosticCode("ILI-RES-0004"), Severity.ERROR,
                                            Phase.SOURCE_RESOLUTION,
                                            "diagnostic.resolution.identity")
                                    .at(site.range())
                                    .argument(new DiagnosticArgument.IdentifierArgument("expected",
                                            site.modelName()))
                                    .argument(new DiagnosticArgument.IdentifierListArgument("actual",
                                            List.of(resolved.target().source().value()
                                                    + "@" + resolved.target().revision().value()
                                                    + ":" + resolved.target().modelName()))));
                            diagnostics.add(id);
                            reasons.add(RejectionReason.MISSING_INPUT);
                        } else {
                            edges.add(new Closure.ImportEdge(model, site, target.get()));
                            queue.addLast(target.get());
                        }
                    }
                    case ImportResolution.Missing missing -> {
                        DiagnosticId id = missing(site, collector);
                        diagnostics.add(id);
                        reasons.add(RejectionReason.MISSING_INPUT);
                    }
                    case ImportResolution.Ambiguous ambiguous -> {
                        DiagnosticId id = collector.emit(DiagnosticDraft.of(
                                        new DiagnosticCode("ILI-RES-0002"), Severity.ERROR,
                                        Phase.SOURCE_RESOLUTION,
                                        "diagnostic.resolution.ambiguous")
                                .at(site.range())
                                .argument(new DiagnosticArgument.IdentifierArgument("model",
                                        site.modelName()))
                                .argument(new DiagnosticArgument.IdentifierListArgument("candidates",
                                        ambiguous.candidates().stream()
                                                .map(c -> c.source().value() + ":" + c.modelName())
                                                .toList())));
                        diagnostics.add(id);
                        reasons.add(RejectionReason.AMBIGUOUS_INPUT);
                    }
                    case ImportResolution.Blocked blocked -> {
                        DiagnosticId id = collector.emit(DiagnosticDraft.of(
                                        new DiagnosticCode("ILI-RES-0003"), Severity.ERROR,
                                        Phase.SOURCE_RESOLUTION, "diagnostic.resolution.source")
                                .at(site.range())
                                .argument(new DiagnosticArgument.TextArgument("source",
                                        blocked.problem().safeDetail()))
                                .argument(new DiagnosticArgument.TextArgument("reason",
                                        blocked.problem().reason().name())));
                        diagnostics.add(id);
                        reasons.add(RejectionReason.ENVIRONMENT_FAILURE);
                    }
                }
            }
        }
        List<ModelDecl> ordered = models.stream()
                .sorted(java.util.Comparator.comparing((ModelDecl m) -> m.sourceId().value())
                        .thenComparingInt(ModelDecl::declarationOffset))
                .toList();
        return new Closure(roots, ordered, sources, edges, diagnostics, reasons);
    }

    private DiagnosticId missing(ImportSyntax site, DiagnosticCollector collector) {
        return collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-RES-0001"),
                        Severity.ERROR, Phase.SOURCE_RESOLUTION, "diagnostic.resolution.missing")
                .at(site.range())
                .argument(new DiagnosticArgument.IdentifierArgument("model", site.modelName())));
    }

    private Map<String, ImportBinding> bindingsBySite(SourceSet sourceSet) {
        Map<String, ImportBinding> result = new LinkedHashMap<>();
        for (ImportBinding binding : sourceSet.imports()) {
            String key = key(binding.site().nameRange(), binding.site().importedModel());
            result.put(key, binding);
        }
        return result;
    }

    private String siteKey(ModelDecl model, ImportSyntax site) {
        return key(site.range(), site.modelName());
    }

    private String key(guru.interlis.source.SourceRange range, String importedModel) {
        return range.source().value() + "@" + range.revision().value() + ":"
                + range.startOffset() + ":" + range.endOffset() + ":" + importedModel;
    }
}
