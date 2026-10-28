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
        return resolve(owner, name, expected, true);
    }

    /** Named references that require a model-qualified spelling disable relative qualifiers. */
    Resolution resolve(SymbolId owner, NameSyntax name, Set<SymbolKind> expected, boolean relativeQualifiers) {
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
                    .filter(d -> visibleModel(owner, d.id()))
                    .map(DeclInfo::id).toList());
            String modelPrefix = modelQualifiedName(owner);
            if (relativeQualifiers && !modelPrefix.isEmpty()) {
                candidates.addAll(index.byQualifiedName(modelPrefix + "." + joined).stream()
                        .filter(d -> expected.contains(d.kind()))
                        .map(DeclInfo::id).toList());
            }
            for (ImportTargets.Target target : relativeQualifiers ? importsOf(owner) : List.<ImportTargets.Target>of()) {
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
                if (!candidates.isEmpty()) break;
            }
            for (ImportTargets.Target target : candidates.isEmpty() ? importsOf(owner) : List.<ImportTargets.Target>of()) {
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
        if (candidates.isEmpty() && segments.size() > 1 && (relativeQualifiers || segments.size() > 2)) {
            var prefix = new NameSyntax(name.id(), name.range(), name.segments().subList(0, name.segments().size()-1));
            var topic = resolve(owner, prefix, Set.of(SymbolKind.TOPIC), relativeQualifiers).target();
            if (topic.isPresent()) candidates.addAll(topicMembers(topic.get(),segments.getLast(),expected,new LinkedHashSet<>()));
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

    /** Indexed lexical declarations only; predefined values remain owned by GraphBuilder. */
    Resolution resolveLocal(SymbolId owner, NameSyntax name, Set<SymbolKind> expected) {
        if(name.segments().size()!=1)return new Resolution(Status.UNRESOLVED,Optional.empty(),List.of());
        String simple=name.segments().getFirst().text();
        for(SymbolId scope:scopeChain(owner)) {
            List<SymbolId> candidates=index.byId(scope).filter(d->d.kind()==SymbolKind.TOPIC).isPresent()
                    ? topicMembers(scope,simple,expected,new LinkedHashSet<>())
                    : index.children(scope).stream().filter(d->d.name().equals(simple) && expected.contains(d.kind())).map(DeclInfo::id).toList();
            if(candidates.size()==1)return new Resolution(Status.BOUND,Optional.of(candidates.getFirst()),List.of());
            if(candidates.size()>1)return new Resolution(Status.AMBIGUOUS,Optional.empty(),candidates.stream().sorted(Comparator.comparing(SymbolId::value)).toList());
        }
        return new Resolution(Status.UNRESOLVED,Optional.empty(),List.of());
    }

    private List<SymbolId> topicMembers(SymbolId topic,String name,Set<SymbolKind> kinds,Set<SymbolId> seen) {
        if (!seen.add(topic)) return List.of();
        var local=index.children(topic).stream().filter(d->d.name().equals(name) && kinds.contains(d.kind())).map(DeclInfo::id).toList();
        if (!local.isEmpty()) return local;
        var declaration=index.byId(topic).orElseThrow();
        var base=((guru.interlis.syntax.TopicSyntax)declaration.syntax()).baseTopic()
                .flatMap(n->resolve(topic,n,Set.of(SymbolKind.TOPIC)).target());
        return base.map(id->topicMembers(id,name,kinds,seen)).orElse(List.of());
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
        return index.modelOfSymbol(owner).map(id -> importsByModel.getOrDefault(id, List.of()))
                .orElse(List.of());
    }

    private boolean visibleModel(SymbolId owner, SymbolId target) {
        var targetModel = index.modelOfSymbol(target);
        return targetModel.isPresent() && (targetModel.equals(index.modelOfSymbol(owner))
                || importsOf(owner).stream().anyMatch(i -> i.target().equals(targetModel.get())));
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
