package guru.interlis.compiler.internal;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Binds ordered generic contexts and applies their visibility rules. */
final class GenericBindings {
    private final GraphBuilder g;
    private final SemanticBuildState state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;

    GenericBindings(GraphBuilder graph, SemanticBuildState state, SemanticWork work,
                   IndexedDeclarations index, SemanticDiagnostics diagnostics) {
        this.g = graph;
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
    }

    guru.interlis.model.ContextSymbol buildContext(DeclInfo decl) {
        var syntax = (guru.interlis.syntax.ContextDeclarationSyntax) decl.syntax();
        if (index.byId(decl.owner().orElseThrow()).orElseThrow().kind() != SymbolKind.MODEL)
            diagnostics.emitRule(decl, "CONTEXT must be declared at model level", "CONTEXT_SCOPE");
        if (index.children(decl.owner().orElseThrow()).stream()
                .anyMatch(d -> !d.id().equals(decl.id()) && d.name().equals(decl.name())))
            diagnostics.emitRule(decl, "context name must be unique", "CONTEXT_NAME_UNIQUE");
        List<guru.interlis.model.ContextSymbol.Mapping> mappings = new ArrayList<>();
        Set<SymbolId> own = new HashSet<>();
        for (var mapping : syntax.mappings()) {
            work.checkpoint();
            var generic = g.names.bindTarget(decl, mapping.genericDomain(), Set.of(SymbolKind.DOMAIN),
                    ReferenceUsage.TYPE_USE, new ArrayList<>());
            List<SymbolId> alternatives = new ArrayList<>();
            for (var name : mapping.alternatives()) g.names.bindTarget(decl, name, Set.of(SymbolKind.DOMAIN),
                    ReferenceUsage.TYPE_USE, new ArrayList<>()).ifPresent(alternatives::add);
            if (generic.isEmpty()) continue;
            if (!own.add(generic.get())) diagnostics.emitRule(decl, "duplicate generic mapping in context", "CONTEXT_DUPLICATE");
            if (new HashSet<>(alternatives).size() != alternatives.size())
                diagnostics.emitRule(decl, "duplicate concrete alternative", "CONTEXT_DUPLICATE");
            mappings.add(new guru.interlis.model.ContextSymbol.Mapping(generic.get(), alternatives, mapping.genericDomain().range()));
        }
        return new guru.interlis.model.ContextSymbol(g.header(decl), mappings);
    }

    /** Bound contexts are model-local; their effects flow only along explicit import edges. */
    void validateGenericSystem() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (symbol instanceof DomainSymbol d && d.generic()
                    && !(g.types.effectiveDescriptor(d.typeId()) instanceof CoordinateType))
                diagnostics.emitRule(index.byId(d.id()).orElseThrow(), "GENERIC requires a coordinate domain", "GENERIC_TYPE");
            if (!(symbol instanceof guru.interlis.model.ContextSymbol context)) continue;
            DeclInfo decl = index.byId(context.id()).orElseThrow();
            for (var mapping : context.mappings()) {
                var generic = (DomainSymbol) state.symbol(mapping.genericDomain());
                if (!generic.generic() || !(g.types.effectiveDescriptor(generic.typeId()) instanceof CoordinateType base)) {
                    diagnostics.emitRule(decl, "CONTEXT domain must be GENERIC coordinates", "CONTEXT_GENERIC"); continue;
                }
                for (SymbolId id : mapping.alternatives()) {
                    work.checkpoint();
                    var concrete = (DomainSymbol) state.symbol(id);
                    if (concrete.generic() || concrete.header().modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                            || !(g.types.effectiveDescriptor(concrete.typeId()) instanceof CoordinateType target)
                            || target.axes().stream().anyMatch(a -> a.minimum().isEmpty() || a.maximum().isEmpty())) {
                        diagnostics.emitRule(decl, "CONTEXT alternative must be concrete coordinates", "CONTEXT_CONCRETE"); continue;
                    }
                    for (SymbolId parent : g.baseChain(concrete.id()).stream().skip(1).toList()) {
                        if (state.symbol(parent) instanceof DomainSymbol ancestor
                                && g.types.effectiveDescriptor(ancestor.typeId()) instanceof CoordinateType inherited
                                && !guru.interlis.model.internal.TypeCompatibility.coordinateRefines(target, inherited))
                            diagnostics.emitRule(decl, "CONTEXT alternative widens its declared coordinate base", "CONTEXT_ALTERNATIVE_EXTENSION");
                    }
                    if (base.dimensions() != target.dimensions() || base.multiCoordinate() != target.multiCoordinate())
                        diagnostics.emitRule(decl, "CONTEXT target dimensions differ", "GENERIC_DIMENSIONS");
                    else if (!guru.interlis.model.internal.TypeCompatibility.coordinateRefines(target, base))
                        diagnostics.emitRule(decl, "CONTEXT target must refine the generic coordinates", "GENERIC_RANGE");
                }
            }
        }
        Map<SymbolId, Map<SymbolId, TopicSymbol.GenericBinding>> contexts = new LinkedHashMap<>();
        for (var model : index.models()) modelContexts(model.id(), contexts, new HashSet<>());
        for (Symbol symbol : List.copyOf(state.symbolsInBuildOrder())) {
            work.checkpoint();
            if (!(symbol instanceof TopicSymbol topic) || index.byId(topic.id()).isEmpty()) continue;
            DeclInfo decl = index.byId(topic.id()).orElseThrow();
            SymbolId model = index.modelOfSymbol(topic.id()).orElseThrow();
            Set<SymbolId> visible = importedModels(model);
            Set<SymbolId> used = new LinkedHashSet<>();
            Set<SymbolId> viewables = new LinkedHashSet<>();
            for (SymbolId id : g.baseChain(topic.id())) for (DeclInfo child : index.children(id))
                if (Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW).contains(child.kind())) viewables.add(child.id());
            // EXTENDED replaces the inherited declaration in this topic's effective population.
            var overridden = new HashSet<SymbolId>();
            for (SymbolId id : viewables) if (state.symbol(id).header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED))
                overridden.addAll(g.baseChain(id).stream().filter(base -> !base.equals(id)).toList());
            viewables.removeAll(overridden);
            Set<TypeId> seen = new HashSet<>();
            Map<SymbolId,Set<guru.interlis.model.NumericRange>> referenceUses=new LinkedHashMap<>();
            for (SymbolId id : viewables) collectViewableGenerics(id, visible, topic.id(), used, referenceUses, seen);
            var available = contexts.getOrDefault(model, Map.of());
            Set<SymbolId> declaredDeferred = new LinkedHashSet<>();
            for (SymbolId id : g.baseChain(topic.id())) if (state.symbol(id) instanceof TopicSymbol t) declaredDeferred.addAll(t.deferredGenerics());
            for (SymbolId id : topic.deferredGenerics()) {
                if (!(state.symbol(id) instanceof DomainSymbol domain) || !domain.generic())
                    diagnostics.emitRule(decl, "DEFERRED GENERICS references a non-generic domain", "GENERIC_DEFERRED_UNKNOWN");
                if (!used.contains(id)) diagnostics.emitRule(decl, "DEFERRED GENERICS domain is unused", "GENERIC_DEFERRED_UNUSED");
                if (!available.containsKey(id)) diagnostics.emitRule(decl, "DEFERRED GENERICS domain has no CONTEXT", "GENERIC_CONTEXT_MISSING");
            }
            List<TopicSymbol.GenericBinding> bindings = new ArrayList<>();
            for (SymbolId generic : used) {
                var binding = available.get(generic);
                if(binding!=null) for(var range:referenceUses.getOrDefault(generic,Set.of())) for(var alternative:binding.alternatives()) {
                    work.checkpoint();var reference=range.referenceSystem().orElseThrow();
                    var concrete=new guru.interlis.model.NumericRange(range.minimum(),range.maximum(),range.circular(),range.direction(),range.unit(),
                            Optional.of(new guru.interlis.model.NumericRange.ReferenceSystem(alternative,true,reference.axis())));
                    // Validate known alternatives without rewriting the published generic identity.
                    g.metadata.validateRange(decl,concrete);
                }
                if (binding == null) {
                    if (topic.header().modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))
                        bindings.add(new TopicSymbol.GenericBinding(generic, List.of(), List.of(), TopicSymbol.BindingState.ABSTRACT));
                    else diagnostics.emitRule(decl, "generic domain is used without an effective CONTEXT", "GENERIC_CONTEXT_MISSING");
                } else if (binding.alternatives().size() > 1 && !declaredDeferred.contains(generic)) {
                    diagnostics.emitRule(decl, "multiple CONTEXT alternatives require DEFERRED GENERICS", "GENERIC_DEFERRED_REQUIRED");
                } else if (!binding.alternatives().isEmpty()) bindings.add(binding);
            }
            state.replaceSymbol(new TopicSymbol(topic.header(), topic.baseTopic(), topic.members(), topic.dependencies(),
                    topic.deferredGenerics(), bindings));
        }
    }

    private Map<SymbolId, TopicSymbol.GenericBinding> modelContexts(SymbolId model,
            Map<SymbolId, Map<SymbolId, TopicSymbol.GenericBinding>> completed, Set<SymbolId> active) {
        if (state.symbol(model) instanceof ModelSymbol predefined && predefined.origin() instanceof Origin.PredefinedOrigin) return Map.of();
        if (completed.containsKey(model)) return completed.get(model);
        if (!active.add(model)) return Map.of(); // ImportBinder diagnoses cycles.
        Map<SymbolId, TopicSymbol.GenericBinding> result = new LinkedHashMap<>();
        ModelSymbol owner = (ModelSymbol) state.symbol(model);
        DeclInfo decl = index.byId(model).orElseThrow();
        for (var imported : owner.imports()) if (imported.targetModel().isPresent()) {
            work.checkpoint();
            for (var entry : modelContexts(imported.targetModel().get(), completed, active).entrySet()) {
                var previous = result.get(entry.getKey()); var incoming = entry.getValue();
                if (previous == null) result.put(entry.getKey(), incoming);
                else {
                    var candidates = new LinkedHashSet<>(previous.alternatives()); candidates.addAll(incoming.alternatives());
                    candidates.removeIf(id -> previous.alternatives().stream().noneMatch(base -> g.domainExtends(id, base))
                            || incoming.alternatives().stream().noneMatch(base -> g.domainExtends(id, base)));
                    var origins = new LinkedHashSet<>(previous.contexts()); origins.addAll(incoming.contexts());
                    if (candidates.isEmpty()) diagnostics.emitRule(decl, "imported contexts have incompatible alternatives", "CONTEXT_CONFLICT");
                    result.put(entry.getKey(), genericBinding(entry.getKey(), List.copyOf(candidates), List.copyOf(origins)));
                }
            }
        }
        for (SymbolId member : owner.members()) if (state.symbol(member) instanceof guru.interlis.model.ContextSymbol context) {
            for (var mapping : context.mappings()) {
                work.checkpoint();
                var previous = result.get(mapping.genericDomain());
                if (previous != null && mapping.alternatives().stream().anyMatch(id ->
                        previous.alternatives().stream().noneMatch(base -> g.domainExtends(id, base))))
                    diagnostics.emitRule(index.byId(context.id()).orElseThrow(), "CONTEXT target must extend an existing alternative", "GENERIC_CONTEXT_EXTENDS");
                result.put(mapping.genericDomain(), genericBinding(mapping.genericDomain(), mapping.alternatives(), List.of(context.id())));
            }
        }
        active.remove(model); completed.put(model, result); return result;
    }

    private TopicSymbol.GenericBinding genericBinding(SymbolId generic, List<SymbolId> alternatives, List<SymbolId> contexts) {
        // Empty alternatives can occur only in a diagnosed partial analysis, not in a valid schema.
        return new TopicSymbol.GenericBinding(generic, alternatives, contexts, alternatives.isEmpty()
                ? TopicSymbol.BindingState.ABSTRACT : alternatives.size() == 1 ? TopicSymbol.BindingState.CONCRETE : TopicSymbol.BindingState.DEFERRED);
    }

    Set<SymbolId> importedModels(SymbolId model) {
        Set<SymbolId> result = new LinkedHashSet<>(); var pending = new java.util.ArrayDeque<SymbolId>(); pending.add(model);
        while (!pending.isEmpty()) {
            work.checkpoint(); SymbolId next = pending.removeFirst();
            if (result.add(next) && state.symbol(next) instanceof ModelSymbol m)
                m.imports().forEach(i -> i.targetModel().ifPresent(pending::add));
        }
        return result;
    }

    private void collectViewableGenerics(SymbolId viewable, Set<SymbolId> visible, SymbolId topic,
            Set<SymbolId> used, Map<SymbolId,Set<guru.interlis.model.NumericRange>> referenceUses, Set<TypeId> seen) {
        for (var member : state.effectiveMembers(viewable)) {
            work.checkpoint();
            if (state.symbol(member.declaredSymbol()) instanceof AttributeSymbol attr) collectGenericUses(attr.declaredType(), visible, topic, used, referenceUses, seen);
        }
    }

    private void collectGenericUses(TypeId type, Set<SymbolId> visible, SymbolId topic, Set<SymbolId> used,
            Map<SymbolId,Set<guru.interlis.model.NumericRange>> referenceUses, Set<TypeId> seen) {
        if (type == null || !seen.add(type)) return;
        work.checkpoint(); TypeDescriptor descriptor = state.type(type);
        if (descriptor instanceof DomainReferenceType ref && state.symbol(ref.targetDomain()) instanceof DomainSymbol domain) {
            if (domain.generic()) used.add(domain.id());
            collectGenericUses(domain.typeId(), visible, topic, used, referenceUses, seen);
        } else if (descriptor instanceof GeometryType geometry) {
            geometry.vertexDomain().map(state::symbol).filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).ifPresent(domain -> {
                if (domain.generic()) used.add(domain.id()); collectGenericUses(domain.typeId(), visible, topic, used, referenceUses, seen);
            });
        } else if (descriptor instanceof NumericType numeric) {
            collectRangeGenerics(numeric.range(),visible,topic,used,referenceUses,seen);
        } else if (descriptor instanceof CoordinateType coordinate) {
            for(var range:coordinate.axes())collectRangeGenerics(range,visible,topic,used,referenceUses,seen);
        } else if (descriptor instanceof CollectionType collection) {
            collectGenericUses(collection.elementType(), visible, topic, used, referenceUses, seen);
        } else if (descriptor instanceof StructureValueType structure) {
            for (Symbol symbol : state.symbolsInBuildOrder()) if (symbol instanceof StructureSymbol candidate
                    && visible.contains(index.modelOfSymbol(candidate.id()).orElse(null))
                    && (structure.targetRoots().isEmpty() || structure.targetRoots().stream().anyMatch(root -> g.baseChain(candidate.id()).contains(root)))
                    && (!(state.symbol(g.scopeRoot(candidate.id())) instanceof TopicSymbol) || g.baseChain(topic).contains(g.scopeRoot(candidate.id()))))
                collectViewableGenerics(candidate.id(), visible, topic, used, referenceUses, seen);
        }
    }

    private void collectRangeGenerics(guru.interlis.model.NumericRange range,Set<SymbolId> visible,SymbolId topic,
            Set<SymbolId> used,Map<SymbolId,Set<guru.interlis.model.NumericRange>> referenceUses,Set<TypeId> seen) {
        work.checkpoint();var reference=range.referenceSystem().orElse(null);
        if(reference==null||!reference.domain())return;
        if(state.symbol(reference.target()) instanceof DomainSymbol domain&&domain.generic()) {
            used.add(domain.id());referenceUses.computeIfAbsent(domain.id(),ignored->new LinkedHashSet<>()).add(range);
            collectGenericUses(domain.typeId(),visible,topic,used,referenceUses,seen);
        }
    }

    private String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }
}
