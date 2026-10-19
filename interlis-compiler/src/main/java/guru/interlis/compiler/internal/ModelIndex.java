package guru.interlis.compiler.internal;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ModelSyntax;
import guru.interlis.syntax.SyntaxSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;

/** Deterministic index of all parsed models. */
final class ModelIndex {
    private final List<ModelDecl> models;
    private final Map<String, List<ModelDecl>> byName;
    private final Map<SourceId, List<ModelDecl>> bySource;
    private final Set<SourceId> knownSources;

    ModelIndex(List<SyntaxSnapshot> snapshots) {
        List<ModelDecl> all = new ArrayList<>();
        Map<String, List<ModelDecl>> names = new LinkedHashMap<>();
        Map<SourceId, List<ModelDecl>> sources = new LinkedHashMap<>();
        List<SyntaxSnapshot> sorted = new ArrayList<>(Objects.requireNonNull(snapshots, "snapshots"));
        sorted.sort(Comparator.comparing(s -> s.source().id().value()));
        for (SyntaxSnapshot snapshot : sorted) {
            SourceText source = snapshot.source();
            for (ModelSyntax model : snapshot.root().models()) {
                ModelDecl declaration = new ModelDecl(model, source);
                all.add(declaration);
                names.computeIfAbsent(model.name(), ignored -> new ArrayList<>()).add(declaration);
                sources.computeIfAbsent(source.id(), ignored -> new ArrayList<>()).add(declaration);
            }
        }
        all.sort(Comparator.comparing((ModelDecl m) -> m.sourceId().value())
                .thenComparingInt(ModelDecl::declarationOffset));
        this.models = List.copyOf(all);
        Map<String, List<ModelDecl>> frozenNames = new LinkedHashMap<>();
        names.forEach((name, values) -> frozenNames.put(name, List.copyOf(values)));
        this.byName = Map.copyOf(frozenNames);
        Map<SourceId, List<ModelDecl>> frozenSources = new LinkedHashMap<>();
        sources.forEach((id, values) -> frozenSources.put(id, List.copyOf(values)));
        this.bySource = Map.copyOf(frozenSources);
        this.knownSources = Set.copyOf(sorted.stream().map(snapshot -> snapshot.source().id()).toList());
    }

    List<ModelDecl> all() {
        return models;
    }

    List<ModelDecl> byName(String name) {
        return byName.getOrDefault(name, List.of());
    }

    boolean hasSource(SourceId source) {
        return knownSources.contains(source);
    }

    List<ModelDecl> bySource(SourceId source) {
        return bySource.getOrDefault(source, List.of());
    }

    Optional<ModelDecl> atLocation(SourceId source, long revision, String name,
                                   Optional<String> version, int declarationOffset) {
        for (ModelDecl model : bySource(source)) {
            if (model.source().revision().value() != revision
                    || !model.name().equals(name)
                    || model.declarationOffset() != declarationOffset) {
                continue;
            }
            if (version.isPresent() && !version.get().equals(model.version())) {
                continue;
            }
            return Optional.of(model);
        }
        return Optional.empty();
    }
}
