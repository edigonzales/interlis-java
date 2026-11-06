package guru.interlis.compiler.internal;

import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Checks topic dependencies and bound reference scopes. */
final class TopicBindings {
    private final GraphBuilder g;
    private final SemanticBuildState state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;

    TopicBindings(GraphBuilder graph, SemanticBuildState state, SemanticWork work,
                   IndexedDeclarations index, SemanticDiagnostics diagnostics) {
        this.g = graph;
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
    }

    List<SymbolId> topicDependencies(SymbolId topic) {
        Set<SymbolId> result = new LinkedHashSet<>();
        for (SymbolId id : g.inheritance.baseChain(topic)) {
            work.checkpoint();
            if (state.symbol(id) instanceof TopicSymbol owner) result.addAll(owner.dependencies());
        }
        return List.copyOf(result);
    }

    void validateTopicDependencies() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            if (!(symbol instanceof TopicSymbol topic)) continue;
            work.checkpoint();
            Set<SymbolId> seen = new HashSet<>();
            var pending = new java.util.ArrayDeque<>(topicDependencies(topic.id()));
            while (!pending.isEmpty()) {
                work.checkpoint();
                SymbolId next = pending.removeFirst();
                if (next.equals(topic.id())) {
                    diagnostics.emitRule(index.byId(topic.id()).orElseThrow(), "cyclic topic dependency", "TOPIC_DEPENDENCY_CYCLE");
                    break;
                }
                if (seen.add(next)) pending.addAll(topicDependencies(next));
            }
        }
    }

    private void validateReferenceScope(DeclInfo site, SymbolId sourceTopic, SymbolId target,
                                        boolean external, String externalRule) {
        SymbolId targetTopic = g.paths.scopeRoot(target);
        if (!(state.symbol(sourceTopic) instanceof TopicSymbol)
                || !(state.symbol(targetTopic) instanceof TopicSymbol)
                || isTopicSubtype(sourceTopic, targetTopic)) return;
        if (!external) diagnostics.emitRule(site, "cross-topic reference requires EXTERNAL", externalRule);
        if (!topicDependencies(sourceTopic).contains(targetTopic))
            diagnostics.emitRule(site, "reference target topic requires DEPENDS ON", "REFERENCE_DEPENDS_ON");
    }

    boolean shareBaseTopic(SymbolId first, SymbolId second) {
        Set<SymbolId> firstBases = new LinkedHashSet<>();
        SymbolId current = first;
        int guard = 0;
        while (current != null && guard++ < 32) {
            work.checkpoint();
            firstBases.add(current);
            current = state.symbol(current) instanceof TopicSymbol topic
                    ? topic.baseTopic().orElse(null) : null;
        }
        current = second;
        guard = 0;
        while (current != null && guard++ < 32) {
            work.checkpoint();
            if (firstBases.contains(current)) {
                return true;
            }
            current = state.symbol(current) instanceof TopicSymbol topic
                    ? topic.baseTopic().orElse(null) : null;
        }
        return false;
    }

    boolean isTopicSubtype(SymbolId candidate, SymbolId base) {
        SymbolId current = candidate;
        Set<SymbolId> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            work.checkpoint();
            if (current.equals(base)) {
                return true;
            }
            Symbol symbol = state.symbol(current);
            if (symbol instanceof TopicSymbol topic) {
                current = topic.baseTopic().orElse(null);
            } else {
                return false;
            }
        }
        return false;
    }

    void validateReferenceTargets() {
        for (var entry : state.effectiveMembersInBuildOrder()) {
            work.checkpoint();
            SymbolId sourceOwner = entry.getKey();
            while (sourceOwner != null && index.byId(sourceOwner).isEmpty()) {
                work.checkpoint();
                sourceOwner = state.symbol(sourceOwner).owner().orElse(null);
            }
            if (sourceOwner == null) continue; // Predefined declarations are checked at their source uses.
            DeclInfo owner = index.byId(sourceOwner).orElseThrow();
            SymbolId topic = g.paths.scopeRoot(owner.id());
            Set<SymbolId> seen = new HashSet<>();
            var pending = new java.util.ArrayDeque<SymbolId>();
            pending.add(entry.getKey());
            while (!pending.isEmpty()) {
                work.checkpoint();
                SymbolId container = pending.removeFirst();
                if (!seen.add(container)) continue;
                for (EffectiveMember member : state.effectiveMembers(container)) {
                    work.checkpoint();
                    Symbol declaration = state.symbol(member.declaredSymbol());
                    List<SymbolId> roots = List.of();
                    boolean external = false;
                    if (declaration instanceof RoleSymbol role) {
                        roots = role.targetRoots(); external = role.external();
                    }
                    else if (declaration instanceof AttributeSymbol attribute) {
                        TypeDescriptor type = g.types.effectiveDescriptor(attribute.declaredType());
                        while (type instanceof CollectionType collection) {
                            work.checkpoint(); type = g.types.effectiveDescriptor(collection.elementType());
                        }
                        if (type instanceof StructureValueType structure) pending.addAll(structure.targetRoots());
                        if (type instanceof ReferenceType reference) {
                            roots = reference.targetRoots(); external = reference.external();
                        }
                    }
                    DeclInfo site = declaration != null && declaration.header().owner().filter(owner.id()::equals).isPresent()
                            ? index.byId(declaration.id()).orElse(owner) : owner;
                    for (SymbolId root : roots) {
                        work.checkpoint();
                        Symbol target = state.symbol(g.paths.contextualObject(root, topic));
                        if (target == null || index.byId(target.id()).isEmpty()) continue; // ANYCLASS/unresolved: no invented OID.
                        validateReferenceScope(site, topic, target.id(), external,
                                declaration instanceof RoleSymbol ? "ROLE_EXTERNAL" : "REFERENCE_EXTERNAL");
                        boolean objectOid = target.header().oidRules().stream().anyMatch(r -> !r.basket() && g.oid.oidState(r) != 0);
                        if (target instanceof ClassSymbol && owner.language() == guru.interlis.model.LanguageVersion.ILI24 && !objectOid)
                            diagnostics.emitRule(site, "reference target " + target.name().value() + " has NO OID in INTERLIS 2.4", "REFERENCE_TARGET_NO_OID");
                        if (target instanceof AssociationSymbol) {
                            boolean identified = target.header().oidRules().isEmpty()
                                    ? g.inheritance.baseChain(target.id()).stream().map(index::byId).flatMap(Optional::stream)
                                        .anyMatch(d -> d.modifiers().contains(guru.interlis.model.Modifier.OID)) : objectOid;
                            if (!identified) diagnostics.emitRule(site, "association target " + target.name().value() + " requires its own OID", "REFERENCE_ASSOCIATION_OID");
                        }
                    }
                }
            }
        }
    }
}
