package guru.interlis.compiler.internal;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.RejectionReason;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves explicit root selectors against the parsed model index. */
final class RootSelector {
    RootSelection select(List<ModelSelector> selectors, ModelIndex index,
                         DiagnosticCollector collector) {
        List<ModelDecl> roots = new ArrayList<>();
        List<DiagnosticId> diagnostics = new ArrayList<>();
        Set<RejectionReason> reasons = new LinkedHashSet<>();
        for (ModelSelector selector : selectors) {
            switch (selector) {
                case ModelSelector.BySource bySource -> {
                    List<ModelDecl> candidates = index.bySource(bySource.source());
                    if (candidates.isEmpty()) {
                        DiagnosticId id = collector.emit(DiagnosticDraft.of(
                                        new DiagnosticCode("ILI-RES-0001"), Severity.ERROR,
                                        Phase.SOURCE_RESOLUTION, "diagnostic.resolution.missing")
                                .argument(new DiagnosticArgument.IdentifierArgument("model",
                                        bySource.source().value())));
                        diagnostics.add(id);
                        reasons.add(RejectionReason.MISSING_INPUT);
                    } else {
                        roots.addAll(candidates);
                    }
                }
                case ModelSelector.ByName byName -> {
                    List<ModelDecl> candidates = index.byName(byName.modelName()).stream()
                            .filter(m -> byName.modelVersion().isEmpty()
                                    || byName.modelVersion().get().equals(m.version()))
                            .filter(m -> byName.sourceHint().isEmpty()
                                    || byName.sourceHint().get().equals(m.sourceId()))
                            .toList();
                    if (candidates.isEmpty()) {
                        DiagnosticId id = collector.emit(DiagnosticDraft.of(
                                        new DiagnosticCode("ILI-RES-0001"), Severity.ERROR,
                                        Phase.SOURCE_RESOLUTION, "diagnostic.resolution.missing")
                                .argument(new DiagnosticArgument.IdentifierArgument("model",
                                        byName.modelName())));
                        diagnostics.add(id);
                        reasons.add(RejectionReason.MISSING_INPUT);
                    } else if (candidates.size() > 1) {
                        DiagnosticId id = collector.emit(DiagnosticDraft.of(
                                        new DiagnosticCode("ILI-RES-0002"), Severity.ERROR,
                                        Phase.SOURCE_RESOLUTION, "diagnostic.resolution.ambiguous")
                                .argument(new DiagnosticArgument.IdentifierArgument("model",
                                        byName.modelName()))
                                .argument(new DiagnosticArgument.IdentifierListArgument("candidates",
                                        candidates.stream().map(ModelDecl::sourceId)
                                                .map(guru.interlis.source.SourceId::value).toList())));
                        diagnostics.add(id);
                        reasons.add(RejectionReason.AMBIGUOUS_INPUT);
                    } else {
                        roots.add(candidates.getFirst());
                    }
                }
            }
        }
        LinkedHashSet<ModelDecl> unique = new LinkedHashSet<>(roots);
        return new RootSelection(List.copyOf(unique), diagnostics, reasons);
    }
}
