package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import guru.interlis.syntax.*;
import java.util.*;

/** View construction within one GraphBuilder run; reuses its type, path and reference binders. */
final class ViewBuilder {
    private final GraphBuilder g;
    private final Map<String, SegmentContextualizer> segmentContexts = new HashMap<>();
    /** Semantic roles of wrappers created below; these are not source names. */
    enum GeometryElementRole { LINE_GEOMETRY, SURFACE_EDGE, SURFACE_BOUNDARY }
    record GeometryContext(GeometryType geometry, SegmentContextualizer segments) {}
    private final Map<SymbolId, GeometryContext> geometryContexts = new LinkedHashMap<>();
    private final Map<SegmentContextualizer, EnumMap<GeometryElementRole, SymbolId>> geometryWrappers = new LinkedHashMap<>();

    GeometryContext geometryContext(SymbolId symbol) { return geometryContexts.get(symbol); }

    Map<GeometryElementRole, SymbolId> geometryWrappers(GeometryContext context) {
        var values = geometryWrappers.get(context.segments());
        return values == null ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(values));
    }

    private void registerGeometryElement(SymbolId symbol, GeometryType geometry,
            SegmentContextualizer segments, GeometryElementRole role) {
        var binding = new GeometryContext(geometry, segments);
        geometryContexts.put(symbol, binding);
        var structure = (StructureSymbol)g.symbols.get(symbol);
        for (SymbolId member : structure.members()) geometryContexts.put(member, binding);
        for (SymbolId constraint : structure.constraints()) geometryContexts.put(constraint, binding);
        for (SymbolId copied : segments.contextualSymbols().values()) geometryContexts.put(copied, binding);
        geometryWrappers.computeIfAbsent(segments, ignored -> new EnumMap<>(GeometryElementRole.class)).put(role, symbol);
    }
    private final Map<SymbolId, SymbolId> projections = new LinkedHashMap<>();
    private final Map<SymbolId, ViewSymbol.Inspection> inspections = new LinkedHashMap<>();
    private final Map<SymbolId, SymbolId> inspectedTargets = new LinkedHashMap<>();
    private final Map<SymbolId, Set<SymbolId>> factorSources = new LinkedHashMap<>();
    private final Set<SymbolId> building = new HashSet<>();
    private final Map<SymbolId, List<SymbolId>> members = new LinkedHashMap<>();
    ViewBuilder(GraphBuilder graph) { g = graph; }

    void contextualMembers(SymbolId owner, List<SymbolId> attributes) { members.put(owner, List.copyOf(attributes)); }

    ViewSymbol.Base projectionBase(SymbolId view) { return (ViewSymbol.Base)g.symbols.get(projections.get(view)); }

    ViewSymbol.Inspection inspection(SymbolId view) { return inspections.get(view); }
    SymbolId basisTarget(ViewSymbol.Base base) { return inspectedTargets.getOrDefault(base.id(), base.target()); }

    Optional<SymbolId> member(SymbolId owner, String name) {
        return members.getOrDefault(owner, List.of()).stream()
                .filter(id -> g.symbols.get(id).name().value().equals(name)).findFirst();
    }

    Symbol buildBase(DeclInfo decl) {
        NameSyntax targetName = (NameSyntax) decl.syntax();
        var resolution = g.resolver.resolve(decl.id(), targetName,
                Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE, SymbolKind.ASSOCIATION, SymbolKind.VIEW), false);
        if (resolution.target().isEmpty()) {
            g.unresolved(targetName);
            // An invalid basis remains explicitly unsupported in partial analysis.
            return new UnsupportedSymbol(new SymbolHeader(decl.id(), SymbolKind.UNSUPPORTED,
                    DeclaredName.declared(decl.name()), decl.owner(), decl.order(), decl.origin(), decl.nameRange(),
                    decl.language(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of()), "VIEW_BASE", targetName.text());
        }
        SymbolId target = resolution.target().orElseThrow();
        DeclInfo targetDecl = g.index.byId(target).orElseThrow();
        if (!building.contains(target)) g.buildSymbol(targetDecl);
        else g.emitRule(decl, "cyclic view formation", "VIEW_CYCLE");
        g.recordReference(decl, targetName, target, ReferenceUsage.EXPRESSION);
        var ownerSyntax = g.index.byId(decl.owner().orElseThrow()).orElseThrow().syntax();
        boolean nullable = ownerSyntax instanceof ViewDeclarationSyntax syntax && syntax.formation().stream().flatMap(f -> f.bases().stream())
                .anyMatch(b -> b.target().range().equals(targetName.range()) && b.orNull());
        return new ViewSymbol.Base(g.header(decl), target, nullable);
    }

    ViewSymbol build(DeclInfo decl) {
        var syntax = (ViewDeclarationSyntax) decl.syntax();
        if (!building.add(decl.id())) {
            g.emitRule(decl, "cyclic view inheritance", "VIEW_CYCLE");
            return new ViewSymbol(g.header(decl), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        try {
            List<SymbolId> bases = new ArrayList<>();
            List<SymbolId> formationBases = new ArrayList<>();
            List<SymbolId> result = new ArrayList<>();
            List<ExpressionId> selections = new ArrayList<>();
            List<ViewSymbol.BaseExtension> extensionsByBase = new ArrayList<>();
            Optional<SymbolId> parent = Optional.empty();
            Optional<ViewSymbol.Formation> formation = Optional.empty();
            if (syntax.baseView().isPresent() || syntax.modifiers().contains(Modifier.EXTENDED)) {
                var name = syntax.baseView().orElse(null);
                parent = name == null ? g.baseTopicMember(decl.id(), decl.name(), Set.of(SymbolKind.VIEW))
                        : g.resolver.resolve(decl.id(), name, Set.of(SymbolKind.VIEW), false).target();
                if (parent.isEmpty()) { if (name != null) g.unresolved(name); else g.emitRule(decl, "EXTENDED view needs an inherited view", "EXTENDED_BASE"); }
                else {
                    if (name != null) g.recordReference(decl, name, parent.orElseThrow(), ReferenceUsage.EXPRESSION);
                    var base = (ViewSymbol) g.buildSymbol(g.index.byId(parent.orElseThrow()).orElseThrow());
                    g.baseOf.put(decl.id(), base.id());
                    if (base.header().has(Modifier.FINAL)) g.emitRule(decl, "cannot extend FINAL view", "VIEW_FINAL");
                    bases.addAll(base.bases()); result.addAll(base.members()); selections.addAll(base.selections()); extensionsByBase.addAll(base.baseExtensions()); formation = base.formation();
                    if (formation.orElse(null) instanceof ViewSymbol.Projection projected) projections.put(decl.id(), projected.base());
                    if (formation.orElse(null) instanceof ViewSymbol.Inspection inspected) inspections.put(decl.id(), inspected);
                }
            }
            members.put(decl.id(), result);
            for (DeclInfo child : g.index.children(decl.id())) if (child.kind() == SymbolKind.VIEW_BASE) {
                g.checkWork();
                var bound = g.buildSymbol(child);
                if (bound instanceof ViewSymbol.Base) {
                    if (syntax.formation().stream().flatMap(f -> f.bases().stream()).anyMatch(b -> b.target() == child.syntax())) formationBases.add(child.id());
                    if (bases.stream().anyMatch(id -> g.symbols.get(id).name().equals(bound.name())))
                        g.emitRule(child, "duplicate view basis", "VIEW_ALIAS_CONFLICT");
                    bases.add(child.id());
                }
            }
            // Basis aliases participate in paths even before a ViewSymbol is published.
            List<SymbolId> scope = new ArrayList<>(bases); scope.addAll(result); members.put(decl.id(), scope);
            for (var extension : syntax.baseExtensions()) {
                var baseId = member(decl.id(), extension.base().text());
                if (baseId.isEmpty() || !(g.symbols.get(baseId.orElseThrow()) instanceof ViewSymbol.Base original)) {
                    g.unresolved(extension.base()); continue;
                }
                g.recordReference(decl, extension.base(), original.id(), ReferenceUsage.EXPRESSION);
                List<SymbolId> extensions = new ArrayList<>();
                for (var candidate : extension.extensions()) {
                    var local = member(decl.id(), candidate.alias().text());
                    if (local.isEmpty() || !(g.symbols.get(local.orElseThrow()) instanceof ViewSymbol.Base target)) continue;
                    SymbolId current = target.target(); Set<SymbolId> seen = new HashSet<>();
                    while (current != null && !current.equals(original.target()) && seen.add(current)) {
                        g.checkWork(); current = g.objectInheritanceBase(current).orElse(null);
                    }
                    if (current == null || !current.equals(original.target()))
                        g.emitRule(decl, "view base extension must specialize its base", "VIEW_BASE_EXTENSION");
                    extensions.add(target.id());
                }
                extensionsByBase.add(new ViewSymbol.BaseExtension(original.id(), extensions));
            }
            if (parent.isPresent() && syntax.formation().isPresent()) g.emitRule(decl, "extended view cannot change formation", "VIEW_FORMATION_OVERRIDE");
            if (syntax.formation().isPresent()) {
                var f = syntax.formation().orElseThrow();
                if (!formationBases.isEmpty()) {
                    switch (f.kind()) {
                        case PROJECTION -> {
                            formation = Optional.of(new ViewSymbol.Projection(formationBases.getFirst()));
                            projections.put(decl.id(), formationBases.getFirst());
                        }
                        case JOIN -> formation = Optional.of(new ViewSymbol.Join(formationBases));
                        case UNION -> formation = Optional.of(new ViewSymbol.Union(formationBases));
                        case AGGREGATION -> {
                            List<ExpressionId> equal = new ArrayList<>();
                            for (var path : f.paths()) equal.add(g.buildExpression(path, decl.id()).id());
                            formation = Optional.of(new ViewSymbol.Aggregation(formationBases.getFirst(), equal));
                            var basis = (ViewSymbol.Base)g.symbols.get(formationBases.getFirst());
                            TypeId element = g.nextTypeId();
                            g.types.put(element, new StructureValueType(element, decl.origin(), Optional.of(basis.target()), List.of()));
                            TypeId bag = g.nextTypeId();
                            g.types.put(bag, new CollectionType(bag, decl.origin(), CollectionKind.BAG,
                                    new Cardinality(java.math.BigInteger.ZERO, new CardinalityUpper.Unbounded()), element));
                            SymbolId aggregateId = new SymbolId(decl.id().value() + ":AGGREGATES");
                            var h = new SymbolHeader(aggregateId, SymbolKind.ATTRIBUTE, DeclaredName.synthetic("AGGREGATES"),
                                    Optional.of(decl.id()), 0, new Origin.DerivedOrigin(basis.id(), DerivationKind.ALIAS), Optional.empty(),
                                    decl.language(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(Modifier.TRANSIENT), List.of());
                            g.symbols.put(aggregateId, new AttributeSymbol(h, bag, false, true, false, false, Optional.empty(), List.of()));
                            g.declaredTypes.put(aggregateId, bag); scope.add(aggregateId);
                        }
                        case INSPECTION, AREA_INSPECTION -> {
                            var basis = (ViewSymbol.Base)g.symbols.get(formationBases.getFirst());
                            var bound = bindInspection(decl, basis.target(), f.paths().getFirst(),
                                    f.kind() == ViewDeclarationSyntax.FormationKind.AREA_INSPECTION);
                            if (bound != null) {
                                var inspected = new ViewSymbol.Inspection(basis.id(), bound.path(), bound.element(), bound.parent(), bound.area());
                                inspectedTargets.put(basis.id(), bound.element()); inspections.put(decl.id(), inspected); formation = Optional.of(inspected);
                            }
                        }
                    }
                }
            } else if (parent.isEmpty()) g.emitRule(decl, "view needs a formation or inherited view", "VIEW_FORMATION");
            for (var item : syntax.members()) {
                g.checkWork();
                if (item instanceof ViewDeclarationSyntax.AllOf all) {
                    if (formation.orElse(null) instanceof ViewSymbol.Union
                            || syntax.formation().map(f -> f.kind() == ViewDeclarationSyntax.FormationKind.AREA_INSPECTION).orElse(false))
                        g.emitRule(decl, "ALL OF is not allowed in UNION or AREA INSPECTION", "VIEW_ALL_OF");
                    var base = member(decl.id(), all.base().text()).map(g.symbols::get).orElse(null);
                    if (!(base instanceof ViewSymbol.Base basis)) { g.unresolved(all.base()); continue; }
                    g.recordReference(decl, all.base(), basis.nameRange().isPresent() ? basis.id() : basis.target(), ReferenceUsage.EXPRESSION);
                    List<SymbolId> inherited;
                    if (g.symbols.get(basisTarget(basis)) instanceof ViewSymbol view) inherited = view.members();
                    else {
                        g.index.byId(basisTarget(basis)).ifPresent(g::collectEffective);
                        inherited = g.effectiveMembers.getOrDefault(basisTarget(basis), List.of()).stream().map(EffectiveMember::declaredSymbol).toList();
                    }
                    for (SymbolId id : inherited) {
                        g.checkWork();
                        if (!(g.symbols.get(id) instanceof AttributeSymbol attribute)) continue;
                        var copyId = new SymbolId(decl.id().value() + ":all:" + basis.id().value() + ":" + id.value());
                        var h = new SymbolHeader(copyId, SymbolKind.ATTRIBUTE, attribute.name(), Optional.of(decl.id()), result.size(),
                                new Origin.DerivedOrigin(id, DerivationKind.ALIAS), Optional.empty(), decl.language(),
                                attribute.header().documentation(), attribute.header().metaAttributes(), attribute.header().modifiers(), List.of());
                        var expressionId = new ExpressionId(copyId.value() + ":expression");
                        g.expressions.put(expressionId, new PathExpression(expressionId, attribute.declaredType(), ExpressionTypeState.RESOLVED,
                                all.base().range(), List.of(), new ObjectPath(List.of(new PathStep.ViewBaseStep(basis.id(), basisTarget(basis), all.base().range()),
                                new PathStep.AttributeStep(attribute.name().value(), attribute.id(), all.base().range())))));
                        var copy = new AttributeSymbol(h, attribute.declaredType(), attribute.mandatory() && !basis.orNull(),
                                attribute.transientAttribute(), attribute.continuous(), attribute.subdivision(), Optional.empty(), List.of(expressionId));
                        add(decl, copy, result, scope);
                    }
                } else {
                    var attributeSyntax = ((ViewDeclarationSyntax.Attribute)item).declaration();
                    var child = g.index.children(decl.id()).stream().filter(d -> d.syntax() == attributeSyntax).findFirst().orElseThrow();
                    var attribute = (AttributeSymbol) g.buildSymbol(child);
                    var previous = result.stream().filter(id -> g.symbols.get(id).name().equals(attribute.name())).findFirst();
                    if (previous.isPresent() && attribute.header().has(Modifier.EXTENDED)) {
                        var old = (AttributeSymbol)g.symbols.get(previous.orElseThrow());
                        if (old.owner().filter(decl.id()::equals).isPresent())
                            g.emitRule(child, "EXTENDED requires an inherited attribute", "EXTENDED_BASE");
                        g.validateAttributeOverrideProperties(child, attribute, old);
                        if (old.header().has(Modifier.FINAL)) g.emitRule(child, "cannot override FINAL attribute", "VIEW_FINAL");
                        if (!g.assignable(attribute.declaredType(), old.declaredType()))
                            g.emitRule(child, "view override must specialize the inherited type", "VIEW_ATTRIBUTE_TYPE");
                        result.set(result.indexOf(old.id()), attribute.id()); scope.remove(old.id()); scope.add(attribute.id());
                        g.symbols.put(attribute.id(), new AttributeSymbol(attribute.header(), attribute.declaredType(), attribute.mandatory(),
                                attribute.transientAttribute(), attribute.continuous(), attribute.subdivision(), Optional.of(old.id()), attribute.derivations()));
                    } else {
                        if (attribute.header().has(Modifier.EXTENDED))
                            g.emitRule(child, "EXTENDED attribute needs an inherited member", "EXTENDED_BASE");
                        add(decl, attribute, result, scope);
                    }
                }
            }
            for (var selection : syntax.selections()) {
                var expression = g.buildExpression(selection, decl.id()); selections.add(expression.id());
                if (!decl.modifiers().contains(Modifier.ABSTRACT)) validateConcreteExpression(decl, expression.id(), new HashSet<>());
                if (!expression.error() && !g.isBoolean(expression.typeId()))
                    g.emitTypeMismatch(decl.sourceId(), selection.range(), g.typeName(expression.typeId()), "BOOLEAN");
            }
            List<SymbolId> constraints = new ArrayList<>();
            for (var child : g.index.children(decl.id())) if (child.kind() == SymbolKind.CONSTRAINT) { g.buildSymbol(child); constraints.add(child.id()); }
            if (syntax.endName().isPresent() && !syntax.name().equals(syntax.endName().orElseThrow()))
                g.emitRule(decl, "view end name mismatch", "VIEW_END_NAME");
            List<EffectiveMember> effective = new ArrayList<>();
            for (var id : result) {
                var attribute = (AttributeSymbol) g.symbols.get(id);
                if (!decl.modifiers().contains(Modifier.ABSTRACT) && attribute.header().has(Modifier.ABSTRACT))
                    g.emitRule(decl, "concrete view contains abstract attribute", "VIEW_ABSTRACT_ATTRIBUTE");
                if (attribute.mandatory()) {
                    var used = new HashSet<SymbolId>();
                    for (var expression : effectiveDerivations(attribute)) collectBases(expression, used, new HashSet<>());
                    if (used.stream().anyMatch(base -> ((ViewSymbol.Base)g.symbols.get(base)).orNull()))
                        g.emitRule(decl, "mandatory view attribute uses an optional join basis", "VIEW_NULLABLE_ATTRIBUTE");
                    if (formation.orElse(null) instanceof ViewSymbol.Union union && !used.containsAll(union.bases()))
                        g.emitRule(decl, "mandatory union attribute needs every basis", "VIEW_UNION_ATTRIBUTE");
                }
                if (!decl.modifiers().contains(Modifier.ABSTRACT)) {
                    for (var expression : effectiveDerivations(attribute)) validateConcreteExpression(decl, expression, new HashSet<>());
                }
                boolean local = attribute.owner().filter(decl.id()::equals).isPresent();
                List<SymbolId> path = new ArrayList<>();
                if (!local && parent.isPresent()) {
                    g.effectiveMembers.getOrDefault(parent.orElseThrow(), List.of()).stream()
                            .filter(member -> member.declaredSymbol().equals(id)).findFirst()
                            .ifPresent(member -> path.addAll(member.inheritancePath()));
                }
                path.add(decl.id());
                effective.add(new EffectiveMember(id, decl.id(), path, effective.size(),
                        local ? (attribute.overrides().isPresent() ? EffectiveMemberOrigin.OVERRIDDEN : EffectiveMemberOrigin.LOCAL)
                                : EffectiveMemberOrigin.INHERITED, attribute.overrides().isPresent()));
            }
            g.effectiveMembers.put(decl.id(), List.copyOf(effective));
            return new ViewSymbol(g.header(decl), parent, formation, bases, extensionsByBase, selections, result, constraints);
        } finally { building.remove(decl.id()); }
    }

    /** Inherited alternatives precede local alternatives; the public override chain retains both. */
    private List<ExpressionId> effectiveDerivations(AttributeSymbol attribute) {
        List<AttributeSymbol> chain = new ArrayList<>();
        Set<SymbolId> seen = new HashSet<>();
        while (attribute != null && seen.add(attribute.id())) {
            g.checkWork(); chain.add(attribute);
            attribute = attribute.overrides().map(g.symbols::get).filter(AttributeSymbol.class::isInstance)
                    .map(AttributeSymbol.class::cast).orElse(null);
        }
        List<ExpressionId> result = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) result.addAll(chain.get(i).derivations());
        return result;
    }

    record Inspected(ExpressionId path, SymbolId element, SymbolId parent, boolean area) {}

    /** The same decomposition binding is used by view formations and factors. */
    Inspected bindInspection(DeclInfo decl, SymbolId source, PathExpressionSyntax path, boolean area) {
        var expression = g.inspectionPath(path, decl.id(), source);
        if (expression.error()) return null;
        TypeDescriptor descriptor = g.effectiveDescriptor(expression.typeId());
        SymbolId parent = source;
        if (path.segments().size() > 1) {
            var prefix = new PathExpressionSyntax(path.id(), path.range(), path.segments().subList(0, path.segments().size()-1));
            var result = g.inspectionPath(prefix, decl.id(), source);
            if (result.error()) return null;
            var type = g.effectiveDescriptor(result.typeId());
            if (type instanceof CollectionType c) type = g.effectiveDescriptor(c.elementType());
            if (type instanceof StructureValueType st) parent = g.structures.target(st);
            else if (type instanceof GeometryType geometry) parent = geometryElement(decl, geometry, false);
        }
        SymbolId element = null;
        if (descriptor instanceof CollectionType collection) descriptor = g.effectiveDescriptor(collection.elementType());
        if (descriptor instanceof StructureValueType structure && !area) element = g.structures.target(structure);
        else if (descriptor instanceof GeometryType geometry) {
            if (!area || geometry.geometryKind() == GeometryKind.AREA || geometry.geometryKind() == GeometryKind.MULTIAREA)
                element = geometryElement(decl, geometry, area);
        }
        if (element == null) {
            g.emitRule(decl, "inspection requires a structure/geometry attribute, AREA requires an area", "VIEW_INSPECTION_TYPE");
            return null;
        }
        return new Inspected(expression.id(), element, parent, area);
    }

    GraphBuilder.ExpressionResult factor(ExpressionId id, InspectionExpressionSyntax syntax, SymbolId owner) {
        DeclInfo decl = g.index.byId(owner).orElseThrow();
        Optional<SymbolId> view = Optional.empty(); SymbolId source = null; Inspected inspected = null;
        if (syntax.view().isPresent()) {
            var name = syntax.view().orElseThrow();
            var resolution = g.resolver.resolve(owner, name, Set.of(SymbolKind.VIEW), false);
            if (resolution.target().isEmpty()) g.unresolved(name);
            else {
                var target = resolution.target().orElseThrow();
                g.recordReference(decl, name, target, ReferenceUsage.EXPRESSION);
                if (building.contains(target)) g.emitRule(decl, "cyclic inspection factor", "VIEW_CYCLE");
                else {
                    var bound = (ViewSymbol)g.buildSymbol(g.index.byId(target).orElseThrow());
                    if (bound.formation().orElse(null) instanceof ViewSymbol.Inspection i) {
                        view = Optional.of(target); source = ((ViewSymbol.Base)g.symbols.get(i.base())).target();
                        inspected = new Inspected(i.path(), i.element(), i.parent(), i.area());
                    }
                }
            }
        } else {
            var f = syntax.formation().orElseThrow(); var name = f.bases().getFirst().target();
            var resolution = g.resolver.resolve(owner, name, Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE, SymbolKind.VIEW, SymbolKind.ASSOCIATION), false);
            if (resolution.target().isEmpty()) g.unresolved(name);
            else {
                source = resolution.target().orElseThrow();
                g.recordReference(decl, name, source, ReferenceUsage.EXPRESSION);
                if (building.contains(source)) g.emitRule(decl, "cyclic inspection factor", "VIEW_CYCLE");
                else {
                    if (g.index.byId(source).orElseThrow().kind() == SymbolKind.VIEW)
                        g.buildSymbol(g.index.byId(source).orElseThrow());
                    inspected = bindInspection(decl, source, f.paths().getFirst(), f.kind() == ViewDeclarationSyntax.FormationKind.AREA_INSPECTION);
                }
            }
        }
        var restriction = syntax.restriction().map(r -> g.buildExpression(r, owner));
        if (inspected == null) {
            var diagnostic = g.emitRule(decl, "factor requires a bound inspection", "INSPECTION_FACTOR_TARGET");
            TypeId type = g.nextTypeId(); g.types.put(type, new ErrorType(type, new Origin.DeclaredOrigin(syntax.range()), List.of(diagnostic)));
            g.expressions.put(id, new ErrorExpression(id, type, ExpressionTypeState.ERROR, syntax.range(), restriction.stream().map(GraphBuilder.ExpressionResult::id).toList(), List.of(diagnostic)));
            return new GraphBuilder.ExpressionResult(id, type, true);
        }
        factorSources.computeIfAbsent(decl.id(), ignored -> new LinkedHashSet<>()).add(view.orElse(source));
        boolean error = restriction.map(GraphBuilder.ExpressionResult::error).orElse(false);
        if (restriction.isPresent() && !error) {
            TypeId expected = g.nextTypeId();
            g.types.put(expected, new ObjectArgumentType(expected, decl.origin(), Optional.of(source), List.of(), false));
            if (!g.assignable(restriction.orElseThrow().typeId(), expected)) {
                g.emitRule(decl, "inspection restriction requires one object specializing its source", "INSPECTION_FACTOR_RESTRICTION");
                error = true;
            }
        }
        TypeId element = g.nextTypeId(); g.types.put(element, new StructureValueType(element, decl.origin(), Optional.of(inspected.element()), List.of()));
        TypeId bag = g.nextTypeId(); g.types.put(bag, new CollectionType(bag, decl.origin(), CollectionKind.BAG, Cardinality.unboundedFrom(0), element));
        g.expressions.put(id, new InspectionExpression(id, bag, error ? ExpressionTypeState.ERROR : ExpressionTypeState.RESOLVED,
                syntax.range(), view, source, inspected.element(), inspected.path(), restriction.map(GraphBuilder.ExpressionResult::id), inspected.area()));
        return new GraphBuilder.ExpressionResult(id, bag, error);
    }

    private void requireDependency(DeclInfo decl, SymbolId source) {
        SymbolId topic = g.scopeRoot(decl.id()), target = g.scopeRoot(source);
        if (g.index.byId(topic).map(d -> d.kind() == SymbolKind.TOPIC).orElse(false)
                && g.index.byId(target).map(d -> d.kind() == SymbolKind.TOPIC).orElse(false)
                && !g.isTopicSubtype(topic, target) && !g.topicDependencies(topic).contains(target))
            g.emitRule(decl, "inspection source topic requires DEPENDS ON", "VIEW_TOPIC_DEPENDENCY");
    }

    private void add(DeclInfo owner, AttributeSymbol attribute, List<SymbolId> result, List<SymbolId> scope) {
        if (scope.stream().anyMatch(id -> g.symbols.get(id).name().equals(attribute.name())))
            g.emitRule(owner, "duplicate view attribute or basis name", "VIEW_ATTRIBUTE_DUPLICATE");
        g.symbols.put(attribute.id(), attribute); g.declaredTypes.put(attribute.id(), attribute.declaredType());
        result.add(attribute.id()); scope.add(attribute.id());
    }

    SymbolId geometryElement(DeclInfo owner, GeometryType geometry, boolean area) {
        TypeId vertex = geometry.vertexDomain().map(id -> {
            g.index.byId(id).ifPresent(g::buildSymbol); return g.declaredTypes.get(id);
        }).orElse(null);
        if (vertex == null) {
            g.emitUnsupported(owner.sourceId(), owner.syntax().range(), "INSPECTION_GEOMETRY_VERTEX", "geometry inspection needs a bound vertex type");
            return syntheticStructure(owner, geometry, "LineSegment", Map.of());
        }
        var context = segmentContexts.computeIfAbsent(owner.id().value() + ":" + geometry.id().value(),
                key -> new SegmentContextualizer(g, this, owner, geometry));
        var segment = context.symbol(g.lines.predefined(owner, "INTERLIS.LineSegment", Set.of(SymbolKind.STRUCTURE)).orElseThrow());
        var start = context.symbol(g.lines.predefined(owner, "INTERLIS.StartSegment", Set.of(SymbolKind.STRUCTURE)).orElseThrow());
        TypeId segments = collection(owner, segment);
        var lineGeometry = syntheticStructure(owner, geometry, "LineGeometry", Map.of("Segments", segments));
        g.lines.startConstraint(owner, lineGeometry, segment, start, owner.syntax().range());
        registerGeometryElement(lineGeometry, geometry, context, GeometryElementRole.LINE_GEOMETRY);
        if (geometry.geometryKind() == GeometryKind.POLYLINE || geometry.geometryKind() == GeometryKind.MULTIPOLYLINE) return lineGeometry;
        TypeId polyline = g.nextTypeId();
        g.types.put(polyline, new GeometryType(polyline, geometry.origin(), GeometryKind.POLYLINE, geometry.lineForms(), geometry.vertexDomain(), geometry.lineAttributes(), true, geometry.withoutOverlaps(), geometry.overlap()));
        Map<String, TypeId> fields = new LinkedHashMap<>(); fields.put("Geometry", polyline);
        if (owner.language() == LanguageVersion.ILI23) {
            TypeId lineAttrs = g.nextTypeId();
            if (geometry.lineAttributes().isPresent()) {
                SymbolId target = geometry.lineAttributes().orElseThrow(); g.index.byId(target).ifPresent(g::buildSymbol);
                g.types.put(lineAttrs, new StructureValueType(lineAttrs, geometry.origin(), Optional.of(target), List.of()));
            } else g.types.put(lineAttrs, new GenericType(lineAttrs, geometry.origin(), "ANYSTRUCTURE"));
            fields.put("LineAttrs", lineAttrs);
        }
        var edge = syntheticStructure(owner, geometry, "SurfaceEdge", fields);
        registerGeometryElement(edge, geometry, context, GeometryElementRole.SURFACE_EDGE);
        if (area) return edge;
        var boundary = syntheticStructure(owner, geometry, "SurfaceBoundary", Map.of("Lines", collection(owner, edge)));
        registerGeometryElement(boundary, geometry, context, GeometryElementRole.SURFACE_BOUNDARY);
        return boundary;
    }

    private TypeId collection(DeclInfo owner, SymbolId structure) {
        TypeId element = g.nextTypeId(); g.types.put(element, new StructureValueType(element, owner.origin(), Optional.of(structure), List.of()));
        TypeId list = g.nextTypeId(); g.types.put(list, new CollectionType(list, owner.origin(), CollectionKind.LIST,
                Cardinality.unboundedFrom(0), element)); return list;
    }

    private SymbolId syntheticStructure(DeclInfo owner, GeometryType geometry, String name, Map<String, TypeId> fields) {
        SymbolId id = new SymbolId(owner.id().value() + ":inspection:" + geometry.id().value() + ":" + name);
        if (g.symbols.containsKey(id)) return id;
        var canonical = g.lines.predefined(owner, "INTERLIS." + name, Set.of(SymbolKind.STRUCTURE)).orElseThrow();
        var header = new SymbolHeader(id, SymbolKind.STRUCTURE, DeclaredName.synthetic(name), Optional.of(owner.id()), 0,
                new Origin.DerivedOrigin(canonical, DerivationKind.CONTEXTUALIZATION), Optional.empty(), owner.language(),
                Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of());
        List<SymbolId> attributes = new ArrayList<>(); List<EffectiveMember> effective = new ArrayList<>();
        for (var field : fields.entrySet()) {
            SymbolId attr = new SymbolId(id.value() + ":" + field.getKey());
            var template = (StructureSymbol)g.symbols.get(canonical);
            var original = template.members().stream().filter(member -> g.symbols.get(member).name().value().equals(field.getKey())).findFirst().orElseThrow();
            var h = new SymbolHeader(attr, SymbolKind.ATTRIBUTE, DeclaredName.synthetic(field.getKey()), Optional.of(id), attributes.size(),
                    new Origin.DerivedOrigin(original, DerivationKind.CONTEXTUALIZATION), Optional.empty(), owner.language(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of());
            g.symbols.put(attr, new AttributeSymbol(h, field.getValue(), field.getKey().equals("SegmentEndPoint") || field.getKey().equals("ArcPoint"), false, false, false, Optional.empty(), List.of()));
            g.declaredTypes.put(attr, field.getValue()); attributes.add(attr);
            effective.add(new EffectiveMember(attr, id, List.of(id), effective.size(), EffectiveMemberOrigin.LOCAL, false));
        }
        members.put(id, attributes); g.effectiveMembers.put(id, effective);
        g.symbols.put(id, new StructureSymbol(header, Optional.empty(), attributes, List.of())); return id;
    }

    private void collectBases(ExpressionId id, Set<SymbolId> bases, Set<ExpressionId> seen) {
        if (!seen.add(id)) return;
        g.checkWork(); var expression = g.expressions.get(id);
        if (expression instanceof PathExpression path) for (var step : path.path().steps())
            if (step instanceof PathStep.ViewBaseStep base) bases.add(base.base());
        if(expression instanceof FunctionCallExpression call)call.argumentPaths().values().forEach(p->p.binding().ifPresent(binding->collectBases(binding,bases,seen)));
        expression.children().forEach(child -> collectBases(child, bases, seen));
    }

    private void validateConcreteExpression(DeclInfo owner, ExpressionId id, Set<ExpressionId> seen) {
        if (!seen.add(id)) return;
        g.checkWork(); var expression = g.expressions.get(id);
        if (expression instanceof PathExpression path) for (var step : path.path().steps()) {
            if (step instanceof PathStep.AttributeStep attribute && g.symbols.get(attribute.target()).header().has(Modifier.ABSTRACT))
                g.emitRule(owner, "concrete view refers to an abstract attribute", "VIEW_ABSTRACT_ATTRIBUTE");
        }
        if(expression instanceof FunctionCallExpression call)call.argumentPaths().values().forEach(p->p.binding().ifPresent(binding->validateConcreteExpression(owner,binding,seen)));
        expression.children().forEach(child -> validateConcreteExpression(owner, child, seen));
    }

    void validateDependencies() {
        for (var use : factorSources.entrySet()) {
            g.checkWork();
            var decl = g.index.byId(use.getKey()).orElseThrow();
            for (var target : use.getValue()) { g.checkWork(); requireDependency(decl, target); }
        }
        for (Symbol symbol : g.symbols.values()) if (symbol instanceof ViewSymbol view) {
            var decl = g.index.byId(view.id()).orElseThrow();
            SymbolId topic = g.scopeRoot(view.id());
            if (!(g.symbols.get(topic) instanceof TopicSymbol)) continue;
            for (var id : view.bases()) {
                var base = (ViewSymbol.Base)g.symbols.get(id);
                SymbolId target = g.scopeRoot(base.target());
                if (g.symbols.get(target) instanceof TopicSymbol && !g.isTopicSubtype(topic, target)
                        && !g.topicDependencies(topic).contains(target))
                    g.emitRule(decl, "view basis topic requires DEPENDS ON", "VIEW_TOPIC_DEPENDENCY");
            }
        }
    }
}
