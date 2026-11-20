package guru.interlis.compiler.internal;

import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CollectionType;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.Modifier;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Checks topic dependencies and bound reference scopes. */
final class TopicBindings {
    private final SemanticGraphRead state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final Supplier<InheritanceBindings> inheritance;
    private final Supplier<ObjectPathBindings> paths;
    private final OidBindings oid;
    private final Function<SymbolId, Set<SymbolId>> visibleModels;
    private final Function<TypeId, TypeDescriptor> effectiveDescriptor;

    TopicBindings(SemanticGraphRead state,
                  SemanticWork work,
                  IndexedDeclarations index,
                  SemanticDiagnostics diagnostics,
                  Supplier<InheritanceBindings> inheritance,
                  Supplier<ObjectPathBindings> paths,
                  OidBindings oid,
                  Function<SymbolId, Set<SymbolId>> visibleModels,
                  Function<TypeId, TypeDescriptor> effectiveDescriptor) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.inheritance = inheritance;
        this.paths = paths;
        this.oid = oid;
        this.visibleModels = visibleModels;
        this.effectiveDescriptor = effectiveDescriptor;
    }

    List<SymbolId> topicDependencies(SymbolId topic) {
        Set<SymbolId> result = new LinkedHashSet<>();
        for (SymbolId id : inheritance.get().baseChain(topic)) {
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
        SymbolId targetTopic = paths.get().scopeRoot(target);
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
            SymbolId topic = paths.get().scopeRoot(owner.id());
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
                    boolean needsConcreteTarget = false;
                    if (declaration instanceof RoleSymbol role) {
                        roots = role.targetRoots(); external = role.external();
                    }
                    else if (declaration instanceof AttributeSymbol attribute) {
                        TypeDescriptor type = effectiveDescriptor.apply(attribute.declaredType());
                        while (type instanceof CollectionType collection) {
                            work.checkpoint(); type = effectiveDescriptor.apply(collection.elementType());
                        }
                        if (type instanceof StructureValueType structure) pending.addAll(structure.targetRoots());
                        if (type instanceof ReferenceType reference) {
                            roots = reference.targetRoots();
                            external = reference.external();
                            needsConcreteTarget = !attribute.header().has(Modifier.ABSTRACT) && !roots.isEmpty();
                        }
                    }
                    DeclInfo site = declaration != null && declaration.header().owner().filter(owner.id()::equals).isPresent()
                            ? index.byId(declaration.id()).orElse(owner) : owner;
                    List<SymbolId> contextualRoots = roots.stream().map(root -> paths.get().contextualObject(root, topic)).toList();
                    if (needsConcreteTarget && !hasConcreteTarget(contextualRoots, owner, topic, external)) {
                        diagnostics.emitRule(site, "reference attribute requires a permitted concrete target",
                                "REFERENCE_CONCRETE_TARGET");
                    }
                    for (SymbolId root : contextualRoots) {
                        work.checkpoint();
                        Symbol target = state.symbol(root);
                        if (target == null || index.byId(target.id()).isEmpty()) continue; // ANYCLASS/unresolved: no invented OID.
                        validateReferenceScope(site, topic, target.id(), external,
                                declaration instanceof RoleSymbol ? "ROLE_EXTERNAL" : "REFERENCE_EXTERNAL");
                        if (target instanceof AssociationSymbol association && !hasAssociationOid(association)) {
                            diagnostics.emitRule(site, "association target " + target.name().value() + " requires its own OID",
                                    "REFERENCE_ASSOCIATION_OID");
                        }
                    }
                }
            }
        }
    }

    private boolean hasConcreteTarget(List<SymbolId> roots, DeclInfo owner, SymbolId scope, boolean external) {
        boolean unresolved = false;
        for (SymbolId root : roots) {
            work.checkpoint();
            Symbol target = state.symbol(root);
            if (target == null || target.origin() instanceof Origin.SyntheticOrigin) {
                unresolved = true;
            } else if (concreteObject(target)) {
                // The ordinary reference scope and OID checks diagnose rejected roots.
                return true;
            }
        }
        if (unresolved) return true;

        Set<SymbolId> models = visibleModels.apply(index.modelOfSymbol(owner.id()).orElseThrow());
        List<SymbolId> dependencies = topicDependencies(scope);
        for (Symbol candidate : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (!concreteObject(candidate) || candidate.origin() instanceof Origin.SyntheticOrigin) continue;
            if (!(candidate.origin() instanceof Origin.PredefinedOrigin)
                    && index.modelOfSymbol(candidate.id()).filter(models::contains).isEmpty()) continue;
            SymbolId candidateScope = paths.get().scopeRoot(candidate.id());
            if (state.symbol(scope) instanceof TopicSymbol && state.symbol(candidateScope) instanceof TopicSymbol
                    && !isTopicSubtype(scope, candidateScope) && !(external && dependencies.contains(candidateScope))) continue;
            if (candidate instanceof AssociationSymbol association && !hasAssociationOid(association)) continue;
            for (SymbolId root : roots) {
                work.checkpoint();
                if (inheritance.get().targetSpecializes(candidate.id(), root)) return true;
            }
        }
        return false;
    }

    private boolean hasAssociationOid(AssociationSymbol association) {
        return association.header().oidRules().isEmpty()
                ? inheritance.get().baseChain(association.id()).stream().map(index::byId).flatMap(Optional::stream)
                        .anyMatch(declaration -> declaration.modifiers().contains(Modifier.OID))
                : association.header().oidRules().stream().anyMatch(rule -> !rule.basket() && oid.oidState(rule) != 0);
    }

    private static boolean concreteObject(Symbol symbol) {
        return (symbol instanceof ClassSymbol || symbol instanceof AssociationSymbol)
                && !symbol.header().has(Modifier.ABSTRACT);
    }
}
