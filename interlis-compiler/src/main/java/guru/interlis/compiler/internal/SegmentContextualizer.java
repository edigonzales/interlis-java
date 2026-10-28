package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import java.util.*;

/** One geometry's substitution of INTERLIS.LineCoord. Canonical declarations and
 * foreign targets remain unchanged; only dependent structures/domains are copied.
 * State belongs to one ViewBuilder run, never to a published graph or global cache. */
final class SegmentContextualizer {
    private final GraphBuilder g;
    private final ViewBuilder views;
    private final DeclInfo owner;
    private final String prefix;
    private final SymbolId placeholder;
    private final TypeId placeholderType;
    private final Map<SymbolId, Set<SymbolId>> dependencies = new LinkedHashMap<>();
    private final Map<SymbolId, SymbolId> symbols = new LinkedHashMap<>();
    private final Map<TypeId, TypeId> types = new HashMap<>();
    private final Map<ExpressionId, ExpressionId> expressions = new HashMap<>();

    SegmentContextualizer(GraphBuilder graph, ViewBuilder views, DeclInfo owner, GeometryType geometry) {
        g = graph; this.views = views; this.owner = owner;
        prefix = owner.id().value() + ":inspection:" + geometry.id().value() + ":context:";
        placeholder = g.lines.predefined(owner, "INTERLIS.LineCoord", Set.of(SymbolKind.DOMAIN)).orElseThrow();
        SymbolId vertex = geometry.vertexDomain().orElseThrow();
        placeholderType = g.declaredTypes.get(placeholder);
        symbols.put(placeholder, vertex); types.put(placeholderType, g.declaredTypes.get(vertex));
        List<SymbolId> roots = new ArrayList<>();
        roots.add(g.lines.predefined(owner, "INTERLIS.LineSegment", Set.of(SymbolKind.STRUCTURE)).orElseThrow());
        roots.add(g.lines.predefined(owner, "INTERLIS.StartSegment", Set.of(SymbolKind.STRUCTURE)).orElseThrow());
        for (SymbolId form : geometry.lineForms()) {
            g.index.byId(form).ifPresent(g::buildSymbol);
            if (g.symbols.get(form) instanceof LineFormSymbol line) line.segmentStructure().ifPresent(roots::add);
        }
        roots.forEach(this::discover);
        // Monotonic closure also handles recursive nested structures without a false
        // memoized "unaffected" result. Source order does not affect this decision.
        Set<SymbolId> affected = new LinkedHashSet<>(); affected.add(placeholder);
        boolean changed;
        do {
            changed = false;
            for (var entry : dependencies.entrySet()) {
                g.checkWork();
                if (!affected.contains(entry.getKey()) && entry.getValue().stream().anyMatch(affected::contains))
                    changed |= affected.add(entry.getKey());
            }
        } while (changed);
        for (SymbolId id : affected) if (!id.equals(placeholder)) {
            symbols.put(id, new SymbolId(prefix + id.value()));
            if (g.symbols.get(id) instanceof StructureSymbol s) {
                s.members().forEach(member -> symbols.put(member, new SymbolId(prefix + member.value())));
                s.constraints().forEach(member -> symbols.put(member, new SymbolId(prefix + member.value())));
            } else if (g.symbols.get(id) instanceof DomainSymbol) {
                g.index.children(id).stream().filter(d -> d.kind() == SymbolKind.CONSTRAINT)
                        .forEach(d -> symbols.put(d.id(), new SymbolId(prefix + d.id().value())));
            }
        }
        // Allocate identities before copying expressions/types with self references.
        for (SymbolId id : List.copyOf(symbols.keySet())) if (!id.equals(placeholder)) copySymbol(g.symbols.get(id));
        for (SymbolId id : affected) if (g.symbols.get(id) instanceof StructureSymbol) {
            var effective = g.effectiveMembers.getOrDefault(id, List.of()).stream().map(m -> new EffectiveMember(
                    symbol(m.declaredSymbol()), symbol(m.effectiveOwner()), m.inheritancePath().stream().map(this::symbol).toList(),
                    m.position(), m.origin(), m.overridden())).toList();
            g.effectiveMembers.put(symbol(id), effective);
            views.contextualMembers(symbol(id), effective.stream().map(EffectiveMember::declaredSymbol).toList());
        }
    }

    SymbolId symbol(SymbolId original) { return symbols.getOrDefault(original, original); }

    /** Exact copied declarations in original order. The LineCoord-to-vertex substitution
     * is intentionally excluded: that declared target is checked by the geometry type.
     * The immutable map belongs to this run and retains no published graph mutation. */
    SymbolId contextOwner() { return owner.id(); }

    Map<SymbolId, SymbolId> contextualSymbols() {
        var result = new LinkedHashMap<SymbolId, SymbolId>();
        symbols.forEach((original, contextual) -> {
            if (!original.equals(placeholder)) result.put(original, contextual);
        });
        return Collections.unmodifiableMap(result);
    }

    private void discover(SymbolId id) {
        g.checkWork();
        if (dependencies.containsKey(id)) return;
        g.index.byId(id).ifPresent(g::buildSymbol);
        var value = g.symbols.get(id);
        if (!(value instanceof StructureSymbol) && !(value instanceof DomainSymbol)) return;
        Set<SymbolId> refs = new LinkedHashSet<>(); dependencies.put(id, refs);
        if (value instanceof StructureSymbol s) {
            g.index.byId(id).ifPresent(g::collectEffective);
            s.baseType().ifPresent(refs::add);
            for (SymbolId member : s.members()) {
                g.checkWork();
                typeDependencies(g.declaredTypes.get(member), refs, new HashSet<>());
                if (g.symbols.get(member) instanceof AttributeSymbol a)
                    a.derivations().forEach(e -> expressionDependencies(e, refs, new HashSet<>()));
            }
            s.constraints().forEach(c -> constraintExpressions(((ConstraintSymbol)g.symbols.get(c)).definition())
                    .forEach(e -> expressionDependencies(e, refs, new HashSet<>())));
        } else if (value instanceof DomainSymbol d) typeDependencies(d.typeId(), refs, new HashSet<>());
        for (SymbolId target : List.copyOf(refs)) discover(target);
    }

    private void typeDependencies(TypeId id, Set<SymbolId> refs, Set<TypeId> seen) {
        if (id == null || !seen.add(id)) return;
        if (id.equals(placeholderType)) { refs.add(placeholder); return; }
        g.checkWork();
        switch (g.types.get(id)) {
            case DomainReferenceType t -> refs.add(t.targetDomain());
            case StructureValueType t -> { t.target().ifPresent(refs::add); refs.addAll(t.restrictions()); }
            case CollectionType t -> typeDependencies(t.elementType(), refs, seen);
            case ClassValueType t -> refs.addAll(t.restrictions());
            case ObjectArgumentType t -> { t.base().ifPresent(refs::add); refs.addAll(t.restrictions()); }
            case ReferenceType t -> { t.target().ifPresent(refs::add); refs.addAll(t.restrictions()); }
            case FormattedType t -> { t.structure().ifPresent(refs::add); t.baseDomain().ifPresent(refs::add);
                t.parts().forEach(p -> p.format().ifPresent(refs::add)); }
            case GeometryType t -> { t.vertexDomain().ifPresent(refs::add); t.lineAttributes().ifPresent(refs::add); }
            case AttributePathType t -> t.restrictions().forEach(r -> typeDependencies(r, refs, seen));
            default -> { }
        }
    }

    private void expressionDependencies(ExpressionId id, Set<SymbolId> refs, Set<ExpressionId> seen) {
        if (!seen.add(id)) return;
        g.checkWork(); var e = g.expressions.get(id);
        typeDependencies(e.typeId(), refs, new HashSet<>());
        if (e instanceof SymbolValueExpression s) refs.add(s.target());
        if(e instanceof FunctionCallExpression call) for(var path:call.argumentPaths().values()) {
            typeDependencies(path.expectedType(),refs,new HashSet<>());
            path.binding().ifPresent(binding->expressionDependencies(binding,refs,seen));
        }
        e.children().forEach(child -> expressionDependencies(child, refs, seen));
    }

    private SymbolHeader header(Symbol original) {
        var h = original.header();
        return new SymbolHeader(symbol(h.id()), h.kind(), DeclaredName.synthetic(h.origin() instanceof Origin.PredefinedOrigin && h.name().value().startsWith("INTERLIS.")
                        ? h.name().value().substring(9) : h.name().value()),
                Optional.of(h.owner().map(this::symbol).filter(symbols::containsValue).orElse(owner.id())), h.declarationOrder(),
                new Origin.DerivedOrigin(h.id(), DerivationKind.CONTEXTUALIZATION), Optional.empty(), h.languageVersion(),
                h.documentation(), h.metaAttributes(), h.modifiers(), h.oidRules());
    }

    private void copySymbol(Symbol original) {
        g.checkWork(); Symbol result = switch (original) {
            case StructureSymbol s -> {
                s.baseType().ifPresent(base -> g.baseOf.put(symbol(s.id()), symbol(base)));
                yield new StructureSymbol(header(s), s.baseType().map(this::symbol),
                        s.members().stream().map(this::symbol).toList(), s.constraints().stream().map(this::symbol).toList());
            }
            case DomainSymbol d -> new DomainSymbol(header(d), type(d.typeId()), d.baseDomain().map(this::symbol), d.generic());
            case AttributeSymbol a -> new AttributeSymbol(header(a), type(a.declaredType()), a.mandatory(), a.transientAttribute(),
                    a.continuous(), a.subdivision(), a.overrides().map(this::symbol), a.derivations().stream().map(this::expression).toList());
            case ParameterSymbol p -> new ParameterSymbol(header(p), type(p.typeId()), p.mandatory(), p.overrides().map(this::symbol));
            case ConstraintSymbol c -> new ConstraintSymbol(header(c), constraint(c.definition()), c.target().map(this::symbol));
            default -> throw new IllegalStateException("unexpected segment member: " + original.kind());
        };
        g.symbols.put(result.id(), result);
        TypeId declared = g.declaredTypes.get(original.id());
        if (declared != null) g.declaredTypes.put(result.id(), type(declared));
    }

    private TypeId type(TypeId original) {
        if (types.containsKey(original)) return types.get(original);
        Set<SymbolId> refs = new HashSet<>(); typeDependencies(original, refs, new HashSet<>());
        if (refs.stream().noneMatch(symbols::containsKey)) return original;
        g.checkWork(); TypeId id = g.nextTypeId(); types.put(original, id);
        var origin = g.types.get(original).origin();
        TypeDescriptor result = switch (g.types.get(original)) {
            case DomainReferenceType t -> new DomainReferenceType(id, origin, symbol(t.targetDomain()));
            case StructureValueType t -> new StructureValueType(id, origin, t.target().map(this::symbol), t.restrictions().stream().map(this::symbol).toList());
            case CollectionType t -> new CollectionType(id, origin, t.collectionKind(), t.cardinality(), type(t.elementType()));
            case ClassValueType t -> new ClassValueType(id, origin, t.structureValue(), t.restrictions().stream().map(this::symbol).toList());
            case ObjectArgumentType t -> new ObjectArgumentType(id, origin, t.base().map(this::symbol), t.restrictions().stream().map(this::symbol).toList(), t.objects());
            case ReferenceType t -> new ReferenceType(id, origin, t.target().map(this::symbol), t.restrictions().stream().map(this::symbol).toList(), t.external());
            case FormattedType t -> new FormattedType(id, origin, t.formatName(), t.predefined(), t.baseDomain().map(this::symbol), t.structure().map(this::symbol),
                    t.parts().stream().map(p -> new FormattedType.Part(p.attribute().map(this::symbol), p.width(), p.format().map(this::symbol), p.literal(), p.origin())).toList(), t.minimum(), t.maximum());
            case GeometryType t -> new GeometryType(id, origin, t.geometryKind(), t.lineForms(), t.vertexDomain().map(this::symbol), t.lineAttributes().map(this::symbol), t.directed(), t.withoutOverlaps(), t.overlap());
            case AttributePathType t -> new AttributePathType(id, origin, t.owner().map(o -> switch (o) {
                case AttributePathType.ClassAttribute a -> new AttributePathType.ClassAttribute(expression(a.path()));
                case AttributePathType.Viewable v -> new AttributePathType.Viewable(symbol(v.target()));
                case AttributePathType.Argument a -> a;
            }), t.restrictions().stream().map(this::type).toList());
            default -> throw new IllegalStateException("unhandled segment type: " + g.types.get(original).kind());
        };
        g.types.put(id, result); return id;
    }

    private ExpressionId expression(ExpressionId original) {
        if (expressions.containsKey(original)) return expressions.get(original);
        g.checkWork(); var e = g.expressions.get(original);
        var id = new ExpressionId(prefix + original.value()); expressions.put(original, id);
        var type = type(e.typeId()); var state = e.typeState(); var range = e.range();
        var children = e.children().stream().map(this::expression).toList();
        TypedExpression result = switch (e) {
            case LiteralExpression x -> new LiteralExpression(id, type, state, range, children, x.literalKind(), x.lexeme(), x.decimal(), x.textValue());
            case PathExpression x -> new PathExpression(id, type, state, range, children, new ObjectPath(x.path().steps().stream().map(this::step).toList()));
            case BinaryExpression x -> new BinaryExpression(id, type, state, range, children, x.operator(), expression(x.left()), expression(x.right()));
            case UnaryExpression x -> new UnaryExpression(id, type, state, range, children, x.operator(), expression(x.operand()));
            case DefinedExpression x -> new DefinedExpression(id, type, state, range, children, expression(x.operand()));
            case FunctionCallExpression x -> new FunctionCallExpression(id, type, state, range, children, x.functionName(), x.targetFunction().map(this::symbol), x.argumentPaths().entrySet().stream().collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e2->new FunctionCallExpression.ArgumentPath(e2.getValue().steps(),e2.getValue().rootArgument(),type(e2.getValue().expectedType()),e2.getValue().binding().map(this::expression)))));
            case SymbolValueExpression x -> new SymbolValueExpression(id, type, state, range, symbol(x.target()));
            case ConstantExpression x -> new ConstantExpression(id, type, state, range, children, x.constantName());
            case ObjectSetExpression x -> new ObjectSetExpression(id, type, state, range);
            case InspectionExpression x -> new InspectionExpression(id, type, state, range, x.view().map(this::symbol), symbol(x.source()), symbol(x.element()), expression(x.path()), x.restriction().map(this::expression), x.area());
            case UndefinedExpression x -> new UndefinedExpression(id, type, state, range, children);
            case ErrorExpression x -> new ErrorExpression(id, type, state, range, children, x.causes());
        };
        g.expressions.put(id, result); return id;
    }

    private PathStep step(PathStep step) {
        return switch (step) {
            case PathStep.AttributeStep s -> new PathStep.AttributeStep(s.name(), symbol(s.target()), s.range());
            case PathStep.RoleStep s -> new PathStep.RoleStep(s.name(), symbol(s.target()), s.range());
            case PathStep.IndexStep s -> new PathStep.IndexStep(s.index(), type(s.resultType()), s.range());
            case PathStep.ViewBaseStep s -> new PathStep.ViewBaseStep(symbol(s.base()), symbol(s.target()), s.range());
            case PathStep.InspectionStep s -> new PathStep.InspectionStep(s.kind(), symbol(s.view()), symbol(s.target()), s.range());
            case PathStep.AssociationAccessStep s -> new PathStep.AssociationAccessStep(s.name(), symbol(s.association()), symbol(s.sourceRole()), symbol(s.targetRole()), s.associationObject(), s.cardinality(), type(s.resultType()), s.range());
            case PathStep.AssociationSourceStep s -> new PathStep.AssociationSourceStep(s.name(), symbol(s.association()), symbol(s.sourceRole()), s.cardinality(), type(s.resultType()), s.range());
            default -> step;
        };
    }

    private static List<ExpressionId> constraintExpressions(ConstraintDefinition definition) {
        List<ExpressionId> result = new ArrayList<>();
        switch (definition) {
            case MandatoryConstraint c -> result.add(c.condition());
            case PlausibilityConstraint c -> result.add(c.condition());
            case DomainConstraint c -> result.add(c.condition());
            case SetConstraint c -> { c.where().ifPresent(result::add); result.add(c.condition()); }
            case ExistenceConstraint c -> { result.add(c.source()); c.targets().forEach(t -> result.add(t.path())); }
            case UniquenessConstraint c -> { c.where().ifPresent(result::add); result.addAll(c.keys()); c.localKeys().forEach(k -> { result.add(k.prefix()); result.addAll(k.fields()); }); }
        }
        return result;
    }

    private ConstraintDefinition constraint(ConstraintDefinition definition) {
        return switch (definition) {
            case MandatoryConstraint c -> new MandatoryConstraint(c.name(), expression(c.condition()));
            case PlausibilityConstraint c -> new PlausibilityConstraint(c.name(), c.percentage(), c.direction(), expression(c.condition()));
            case DomainConstraint c -> new DomainConstraint(expression(c.condition()));
            case SetConstraint c -> new SetConstraint(c.name(), c.scope(), c.where().map(this::expression), expression(c.condition()));
            case ExistenceConstraint c -> new ExistenceConstraint(c.name(), expression(c.source()), c.targets().stream().map(t -> new ExistenceConstraint.ExistenceTarget(symbol(t.viewable()), expression(t.path()))).toList());
            case UniquenessConstraint c -> new UniquenessConstraint(c.name(), c.basket(), c.where().map(this::expression), c.keys().stream().map(this::expression).toList(), c.localKeys().stream().map(k -> new UniquenessConstraint.LocalUniqueness(expression(k.prefix()), k.fields().stream().map(this::expression).toList())).toList());
        };
    }
}
