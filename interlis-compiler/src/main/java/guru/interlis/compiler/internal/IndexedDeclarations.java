package guru.interlis.compiler.internal;

import guru.interlis.model.SymbolId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic declaration index over all parsed models. */
final class IndexedDeclarations {
    private final List<ModelEntry> models;
    private final Map<SymbolId, DeclInfo> byId;
    private final Map<SymbolId, List<DeclInfo>> children;
    private final Map<String, List<DeclInfo>> byQualifiedName;
    private final Map<SymbolId, SymbolId> modelOf;

    IndexedDeclarations(List<ModelEntry> models, Map<SymbolId, DeclInfo> byId,
                        Map<SymbolId, List<DeclInfo>> children,
                        Map<String, List<DeclInfo>> byQualifiedName,
                        Map<SymbolId, SymbolId> modelOf) {
        this.models = List.copyOf(Objects.requireNonNull(models, "models"));
        this.byId = Map.copyOf(Objects.requireNonNull(byId, "byId"));
        Map<SymbolId, List<DeclInfo>> frozenChildren = new LinkedHashMap<>();
        children.forEach((key, value) -> frozenChildren.put(key, List.copyOf(value)));
        this.children = Map.copyOf(frozenChildren);
        Map<String, List<DeclInfo>> frozenNames = new LinkedHashMap<>();
        byQualifiedName.forEach((key, value) -> frozenNames.put(key, List.copyOf(value)));
        this.byQualifiedName = Map.copyOf(frozenNames);
        this.modelOf = Map.copyOf(Objects.requireNonNull(modelOf, "modelOf"));
    }

    List<ModelEntry> models() {
        return models;
    }

    Optional<DeclInfo> byId(SymbolId id) {
        return Optional.ofNullable(byId.get(id));
    }

    List<DeclInfo> children(SymbolId owner) {
        return children.getOrDefault(owner, List.of());
    }

    List<DeclInfo> byQualifiedName(String name) {
        return byQualifiedName.getOrDefault(name, List.of());
    }

    Map<String, List<DeclInfo>> qualifiedNames() {
        return byQualifiedName;
    }

    Optional<SymbolId> modelOfSymbol(SymbolId id) {
        return Optional.ofNullable(modelOf.get(id));
    }

    List<DeclInfo> all() {
        return new ArrayList<>(byId.values());
    }

    record ModelEntry(ModelDecl model, DeclInfo declaration) {
        ModelEntry {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(declaration, "declaration");
        }

        SymbolId id() {
            return declaration.id();
        }
    }
}
