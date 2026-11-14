package guru.interlis.compiler.internal;

import guru.interlis.compiler.ModelLocation;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.RejectionReason;
import guru.interlis.compiler.RootResolution;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolves explicit root selectors against the parsed model index. */
final class RootSelector {
    RootSelection select(List<ModelSelector> selectors, Map<ModelSelector.ByName, RootResolution> resolvedRoots,
                         ModelIndex index,
                         DiagnosticCollector collector) {
        List<ModelDecl> roots = new ArrayList<>();
        List<DiagnosticId> diagnostics = new ArrayList<>();
        Set<RejectionReason> reasons = new LinkedHashSet<>();
        for (ModelSelector selector : selectors) {
            switch (selector) {
                case ModelSelector.BySource bySource -> {
                    List<ModelDecl> candidates = index.bySource(bySource.source());
                    if (candidates.isEmpty() && !index.hasSource(bySource.source())) {
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
                    var resolution = resolvedRoots.get(byName);
                    List<ModelDecl> candidates = switch (resolution) {
                        case null -> index.byName(byName.modelName()).stream()
                                .filter(model -> matches(byName, model)).toList();
                        case RootResolution.Missing ignored -> List.of();
                        case RootResolution.Resolved resolved ->
                                atLocation(byName, resolved.target(), index).stream().toList();
                        case RootResolution.Ambiguous ambiguous -> {
                            var declarations = ambiguous.candidates().stream()
                                    .flatMap(location -> atLocation(byName, location, index).stream()).toList();
                            // Never promote an ambiguous selection if a candidate cannot be verified.
                            yield declarations.size() == ambiguous.candidates().size() ? declarations : List.of();
                        }
                    };
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

    private static Optional<ModelDecl> atLocation(ModelSelector.ByName selector, ModelLocation location, ModelIndex index) {
        return index.atLocation(location.source(), location.revision().value(), location.declarationOffset())
                .filter(model -> model.name().equals(location.modelName())
                        && location.modelVersion().map(model.version()::equals).orElse(false)
                        && matches(selector, model));
    }

    private static boolean matches(ModelSelector.ByName selector, ModelDecl model) {
        return selector.modelName().equals(model.name())
                && selector.modelVersion().map(model.version()::equals).orElse(true)
                && selector.sourceHint().map(model.sourceId()::equals).orElse(true);
    }
}
