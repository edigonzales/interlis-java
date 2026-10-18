package guru.interlis.compiler.internal;

import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.syntax.NameSyntax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Scope-aware deterministic name resolution over the declaration index. */
final class NameResolver {
    private final IndexedDeclarations index;
    private final Map<SymbolId, List<ImportTargets.Target>> importsByModel;

    NameResolver(IndexedDeclarations index, Closure closure) {
        this.index = Objects.requireNonNull(index, "index");
        Map<SymbolId, List<ImportTargets.Target>> targets = new LinkedHashMap<>();
        for (Closure.ImportEdge edge : closure.importEdges()) {
            SymbolId importer = findModelId(edge.importer().name(), edge.importer().declarationOffset(),
                    edge.importer().sourceId()).orElse(null);
            SymbolId target = findModelId(edge.target().name(), edge.target().declarationOffset(),
                    edge.target().sourceId()).orElse(null);
            if (importer != null && target != null) {
                targets.computeIfAbsent(importer, ignored -> new ArrayList<>())
                        .add(new ImportTargets.Target(target, edge.site().unqualified()));
            }
        }
        Map<SymbolId, List<ImportTargets.Target>> frozen = new LinkedHashMap<>();
        targets.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        this.importsByModel = Map.copyOf(frozen);
    }

    private Optional<SymbolId> findModelId(String name, int offset, guru.interlis.source.SourceId source) {
        return index.models().stream()
                .filter(e -> e.model().sourceId().equals(source))
                .filter(e -> e.model().name().equals(name))
                .filter(e -> e.model().declarationOffset() == offset)
                .map(IndexedDeclarations.ModelEntry::id)
                .findFirst();
    }

    Resolution resolve(SymbolId owner, NameSyntax name, Set<SymbolKind> expected) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(expected, "expected");
        LinkedHashSet<SymbolId> candidates = new LinkedHashSet<>();
        List<String> segments = name.segments().stream()
                .map(NameSyntax.NameSegmentSyntax::text).toList();
        String joined = String.join(".", segments);
        if (segments.size() > 1) {
            candidates.addAll(index.byQualifiedName(joined).stream()
                    .filter(d -> expected.contains(d.kind()))
                    .map(DeclInfo::id).toList());
            String modelPrefix = modelQualifiedName(owner);
            if (!modelPrefix.isEmpty()) {
                candidates.addAll(index.byQualifiedName(modelPrefix + "." + joined).stream()
                        .filter(d -> expected.contains(d.kind()))
                        .map(DeclInfo::id).toList());
            }
            for (ImportTargets.Target target : importsOf(owner)) {
                String targetName = modelQualifiedName(target.target());
                candidates.addAll(index.byQualifiedName(targetName + "." + joined).stream()
                        .filter(d -> expected.contains(d.kind()))
                        .map(DeclInfo::id).toList());
            }
        } else {
            String simple = segments.getFirst();
            for (SymbolId scope : scopeChain(owner)) {
                for (DeclInfo child : index.children(scope)) {
                    if (child.name().equals(simple) && expected.contains(child.kind())) {
                        candidates.add(child.id());
                    }
                }
            }
            for (ImportTargets.Target target : importsOf(owner)) {
                if (!target.unqualified()) {
                    continue;
                }
                for (DeclInfo child : index.children(target.target())) {
                    if (child.name().equals(simple) && expected.contains(child.kind())) {
                        candidates.add(child.id());
                    }
                }
            }
        }
        List<SymbolId> ordered = candidates.stream()
                .sorted(Comparator.comparing(SymbolId::value)).toList();
        if (ordered.size() == 1) {
            return new Resolution(Status.BOUND, Optional.of(ordered.getFirst()), List.of());
        }
        if (ordered.isEmpty()) {
            return new Resolution(Status.UNRESOLVED, Optional.empty(), List.of());
        }
        return new Resolution(Status.AMBIGUOUS, Optional.empty(), ordered);
    }

    Resolution resolveModel(String modelName) {
        List<SymbolId> candidates = index.models().stream()
                .filter(e -> e.model().name().equals(modelName))
                .map(IndexedDeclarations.ModelEntry::id)
                .sorted(Comparator.comparing(SymbolId::value))
                .toList();
        if (candidates.size() == 1) {
            return new Resolution(Status.BOUND, Optional.of(candidates.getFirst()), List.of());
        }
        return candidates.isEmpty()
                ? new Resolution(Status.UNRESOLVED, Optional.empty(), List.of())
                : new Resolution(Status.AMBIGUOUS, Optional.empty(), candidates);
    }

    private List<SymbolId> scopeChain(SymbolId owner) {
        List<SymbolId> result = new ArrayList<>();
        SymbolId current = owner;
        int guard = 0;
        while (current != null && guard++ < 32) {
            result.add(current);
            Optional<DeclInfo> info = index.byId(current);
            if (info.isEmpty() || info.get().owner().isEmpty()) {
                break;
            }
            current = info.get().owner().get();
        }
        return result;
    }

    private String modelQualifiedName(SymbolId symbol) {
        SymbolId current = symbol;
        int guard = 0;
        while (current != null && guard++ < 32) {
            Optional<DeclInfo> info = index.byId(current);
            if (info.isEmpty()) {
                return "";
            }
            if (info.get().kind() == SymbolKind.MODEL) {
                return info.get().qualifiedName();
            }
            current = info.get().owner().orElse(null);
        }
        return "";
    }

    private List<ImportTargets.Target> importsOf(SymbolId owner) {
        String model = modelQualifiedName(owner);
        for (Map.Entry<SymbolId, List<ImportTargets.Target>> entry : importsByModel.entrySet()) {
            Optional<DeclInfo> info = index.byId(entry.getKey());
            if (info.isPresent() && info.get().qualifiedName().equals(model)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    enum Status {
        BOUND,
        UNRESOLVED,
        AMBIGUOUS
    }

    record Resolution(Status status, Optional<SymbolId> target, List<SymbolId> candidates) {
        Resolution {
            Objects.requireNonNull(status, "status");
            target = Objects.requireNonNull(target, "target");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    private static final class ImportTargets {
        private ImportTargets() {
        }

        record Target(SymbolId target, boolean unqualified) {
            Target {
                Objects.requireNonNull(target, "target");
            }
        }
    }
}
