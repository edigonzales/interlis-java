package guru.interlis.documentation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;

import java.util.*;
import java.util.stream.Collectors;

/** Bounded display descriptions from typed semantic values, never AST Java class names. */
final class SemanticDescriptions {
    private final SemanticGraph graph;
    private final CancellationToken token;

    SemanticDescriptions(SemanticGraph graph, CancellationToken token) {
        this.graph = graph;
        this.token = token;
    }

    String name(SymbolId id) {
        var parts = new ArrayList<String>();
        var seen = new HashSet<SymbolId>();
        var s = graph.symbol(id).orElse(null);
        while (s != null && seen.add(s.id()) && parts.size() < 64) {
            if (s.origin() instanceof Origin.PredefinedOrigin predefined) {
                parts.addFirst(predefined.key());
                return String.join(".", parts);
            }
            parts.addFirst(s.name().value());
            s = s.owner().flatMap(graph::symbol).orElse(null);
        }
        return String.join(".", parts);
    }

    String base(Symbol s) {
        Optional<SymbolId> id =
                switch (s) {
                    case ClassSymbol c -> c.baseType();
                    case StructureSymbol c -> c.baseType();
                    case AssociationSymbol c -> c.baseAssociation();
                    case DomainSymbol d -> d.baseDomain();
                    case TopicSymbol t -> t.baseTopic();
                    default -> Optional.empty();
                };
        return id.map(this::name).orElse("");
    }

    String symbolType(Symbol s) {
        return switch (s) {
            case AttributeSymbol a ->
                    (a.mandatory() ? "MANDATORY " : "") + type(a.declaredType(), new HashSet<>());
            case DomainSymbol d ->
                    (d.mandatory() ? "MANDATORY " : "") + type(d.typeId(), new HashSet<>());
            case RoleSymbol r ->
                    r.aggregation()
                            + " "
                            + r.targetRoots().stream()
                                    .map(this::name)
                                    .collect(Collectors.joining(" OR "));
            default -> graph.declaredType(s.id()).map(id -> type(id, new HashSet<>())).orElse("");
        };
    }

    String cardinality(Symbol s) {
        if (s instanceof RoleSymbol r) return card(r.cardinality());
        if (s instanceof AttributeSymbol a) {
            var t = graph.type(a.declaredType()).orElse(null);
            return t instanceof CollectionType c
                    ? card(c.cardinality())
                    : a.mandatory() ? "1" : "0..1";
        }
        return "";
    }

    static String card(Cardinality c) {
        String upper = c.upper() instanceof CardinalityUpper.Finite f ? f.value().toString() : "*";
        return c.lower().toString().equals(upper) ? upper : c.lower() + ".." + upper;
    }

    List<String> references(Symbol s) {
        var out = new LinkedHashSet<String>();
        if (s instanceof RoleSymbol r) r.targetRoots().forEach(id -> out.add(name(id)));
        graph.declaredType(s.id()).ifPresent(id -> refs(id, new HashSet<>(), out));
        return List.copyOf(out);
    }

    void refs(TypeId id, Set<TypeId> seen, Set<String> out) {
        token.throwIfCancellationRequested();
        if (!seen.add(id) || seen.size() > 64) return;
        switch (graph.type(id).orElse(null)) {
            case DomainReferenceType d -> out.add(name(d.targetDomain()));
            case StructureValueType v -> v.targetRoots().forEach(s -> out.add(name(s)));
            case ReferenceType v -> v.targetRoots().forEach(s -> out.add(name(s)));
            case CollectionType c -> refs(c.elementType(), seen, out);
            case null, default -> {}
        }
    }

    String range(NumericRange r) {
        return r.minimum().map(DecimalLiteral::lexeme).orElse("*")
                + ".."
                + r.maximum().map(DecimalLiteral::lexeme).orElse("*")
                + r.unit().map(u -> " [" + name(u) + "]").orElse("")
                + (r.circular() ? " CIRCULAR" : "")
                + (r.direction() == NumericDirection.NONE ? "" : " " + r.direction())
                + r.referenceSystem()
                        .map(
                                ref ->
                                        " REFERENCE SYSTEM "
                                                + name(ref.target())
                                                + (ref.domain() ? " DOMAIN" : " OBJECT")
                                                + (ref.axis().isPresent()
                                                        ? " AXIS " + ref.axis().getAsInt()
                                                        : ""))
                        .orElse("");
    }

    String type(TypeId id, Set<TypeId> seen) {
        token.throwIfCancellationRequested();
        if (!seen.add(id) || seen.size() > 64) return "[type reference]";
        var d = graph.type(id).orElseThrow();
        return switch (d) {
            case TextType t ->
                    t.textKind().name()
                            + (t.maxLength().isPresent() ? "*" + t.maxLength().getAsInt() : "");
            case NumericType n -> range(n.range());
            case BooleanType b -> "BOOLEAN";
            case AlignmentType a -> a.alignment().name();
            case DomainReferenceType r -> name(r.targetDomain());
            case CollectionType c ->
                    c.collectionKind()
                            + " {"
                            + card(c.cardinality())
                            + "} OF "
                            + type(c.elementType(), seen);
            case StructureValueType s ->
                    s.target().map(this::name).orElse("ANYSTRUCTURE")
                            + restriction(s.restrictions());
            case ReferenceType r ->
                    "REFERENCE TO "
                            + r.target().map(this::name).orElse("ANYCLASS")
                            + restriction(r.restrictions())
                            + (r.external() ? " EXTERNAL" : "");
            case EnumerationType e ->
                    "("
                            + e.values().stream()
                                    .map(EnumerationValue::path)
                                    .collect(Collectors.joining(", "))
                            + ")"
                            + (e.treeValues() ? " ALL OF TREE" : "")
                            + (e.ordered() ? " ORDERED" : "")
                            + (e.circular() ? " CIRCULAR" : "")
                            + (e.finalSubtrees().isEmpty()
                                    ? ""
                                    : " FINAL "
                                            + String.join(", ", new TreeSet<>(e.finalSubtrees())));
            case CoordinateType c ->
                    (c.multiCoordinate() ? "MULTICOORD " : "COORD ")
                            + c.axes().stream().map(this::range).collect(Collectors.joining(", "))
                            + c.referenceSystem().map(s -> " " + s).orElse("")
                            + c.rotation()
                                    .map(
                                            r ->
                                                    " ROTATION "
                                                            + r.zeroAxis()
                                                            + " -> "
                                                            + r.quarterTurnAxis())
                                    .orElse("");
            case GeometryType g ->
                    (g.directed() ? "DIRECTED " : "")
                            + g.geometryKind()
                            + " WITH ("
                            + g.lineForms().stream()
                                    .map(this::name)
                                    .collect(Collectors.joining(", "))
                            + ")"
                            + g.vertexDomain().map(s -> " VERTEX " + name(s)).orElse("")
                            + g.lineAttributes().map(s -> " LINE ATTRIBUTES " + name(s)).orElse("")
                            + (g.withoutOverlaps() ? " WITHOUT OVERLAPS" : "")
                            + g.overlap()
                                    .map(
                                            t ->
                                                    switch (t) {
                                                        case GeometryType.Explicit e ->
                                                                " > " + e.value().lexeme();
                                                        case GeometryType.TrackWidth w ->
                                                                " > trackWidth("
                                                                        + name(w.coordinateDomain())
                                                                        + ")";
                                                    })
                                    .orElse("");
            case FormattedType f ->
                    f.predefined()
                                    .map(Enum::name)
                                    .orElse(
                                            "FORMAT "
                                                    + f.structure()
                                                            .map(this::name)
                                                            .orElseGet(
                                                                    () ->
                                                                            f.baseDomain()
                                                                                    .map(this::name)
                                                                                    .orElse(
                                                                                            f
                                                                                                    .formatName())))
                            + " "
                            + f.parts().stream()
                                    .map(
                                            p ->
                                                    p.attribute()
                                                                    .map(this::name)
                                                                    .orElse(
                                                                            "\""
                                                                                    + p.literal()
                                                                                    + "\"")
                                                            + p.width().map(w -> "/" + w).orElse("")
                                                            + p.format()
                                                                    .map(s -> "/" + name(s))
                                                                    .orElse(""))
                                    .collect(Collectors.joining(" "))
                            + f.minimum().map(s -> " \"" + s + "\"").orElse("")
                            + f.maximum().map(s -> "..\"" + s + "\"").orElse("");
            case OidType o ->
                    o.oidKind().name() + o.valueType().map(t -> " " + type(t, seen)).orElse("");
            case BlackboxType b -> "BLACKBOX " + b.blackboxKind();
            case ClassValueType c ->
                    (c.structureValue() ? "STRUCTURE" : "CLASS") + restriction(c.restrictions());
            case GenericType g -> g.name();
            case AttributePathType a ->
                    "ATTRIBUTE PATH "
                            + a.owner()
                                    .map(
                                            owner ->
                                                    switch (owner) {
                                                        case AttributePathType.Viewable v ->
                                                                "OF " + name(v.target()) + " ";
                                                        case AttributePathType.Argument argument ->
                                                                "OF ARGUMENT "
                                                                        + (argument.index() + 1)
                                                                        + " OF "
                                                                        + name(argument.function())
                                                                        + " ";
                                                        case AttributePathType.ClassAttribute
                                                                        attribute ->
                                                                "OF "
                                                                        + expression(
                                                                                attribute.path())
                                                                        + " ";
                                                    })
                                    .orElse("")
                            + a.restrictions().stream()
                                    .map(t -> type(t, new HashSet<>(seen)))
                                    .collect(Collectors.joining("; "));
            case EnumArgumentType e -> e.treeValues() ? "ENUMTREEVAL" : "ENUMVAL";
            case ObjectArgumentType o ->
                    (o.objects() ? "OBJECTS OF " : "OBJECT OF ")
                            + o.base().map(this::name).orElse("ANYCLASS")
                            + restriction(o.restrictions());
            case MetaObjectType m ->
                    "METAOBJECT" + m.target().map(s -> " OF " + name(s)).orElse("");
            case DeferredTypeBinding deferred -> "DEFERRED: " + deferred.condition();
            case ErrorType e ->
                    throw new IllegalArgumentException("checked catalogue contains an error type");
            case PendingType p ->
                    throw new IllegalArgumentException("checked catalogue contains a pending type");
        };
    }

    String restriction(List<SymbolId> ids) {
        return ids.isEmpty()
                ? ""
                : " RESTRICTION("
                        + ids.stream().map(this::name).collect(Collectors.joining("; "))
                        + ")";
    }

    String expression(ExpressionId id) {
        return expression(id, new HashSet<>());
    }

    String expression(ExpressionId id, Set<ExpressionId> seen) {
        token.throwIfCancellationRequested();
        if (!seen.add(id) || seen.size() > 64) return "[expression reference]";
        var e = graph.expression(id).orElseThrow();
        return switch (e) {
            case LiteralExpression l -> l.lexeme();
            case SymbolValueExpression s -> name(s.target());
            case BinaryExpression b ->
                    "("
                            + expression(b.left(), new HashSet<>(seen))
                            + " "
                            + operator(b.operator().name())
                            + " "
                            + expression(b.right(), new HashSet<>(seen))
                            + ")";
            case UnaryExpression u ->
                    (u.operator() == UnaryOperator.NEGATE ? "-" : "NOT ")
                            + "("
                            + expression(u.operand(), seen)
                            + ")";
            case DefinedExpression d -> "DEFINED(" + expression(d.operand(), seen) + ")";
            case FunctionCallExpression f ->
                    f.functionName()
                            + "("
                            + f.children().stream()
                                    .map(c -> expression(c, new HashSet<>(seen)))
                                    .collect(Collectors.joining(", "))
                            + ")";
            case PathExpression p -> {
                var path = new StringBuilder();
                for (var part : p.path().steps()) {
                    if (!path.isEmpty() && !(part instanceof PathStep.IndexStep)) path.append("->");
                    path.append(step(part));
                }
                yield path.toString();
            }
            case UndefinedExpression u -> "UNDEFINED";
            case ErrorExpression x -> "[invalid expression]";
            case ConstantExpression c -> c.constantName();
            case ObjectSetExpression o -> "ALL " + type(o.typeId(), new HashSet<>());
            case InspectionExpression i ->
                    "INSPECTION OF "
                            + name(i.source())
                            + "->"
                            + name(i.element())
                            + ": "
                            + expression(i.path(), seen)
                            + i.restriction()
                                    .map(r -> " WHERE " + expression(r, new HashSet<>(seen)))
                                    .orElse("");
        };
    }

    String step(PathStep s) {
        return switch (s) {
            case PathStep.AttributeStep a -> a.name();
            case PathStep.RoleStep r -> r.name();
            case PathStep.ThisStep t -> "THIS";
            case PathStep.ParentStep p -> "PARENT";
            case PathStep.InspectionStep i -> i.kind().name();
            case PathStep.ViewBaseStep b -> name(b.base());
            case PathStep.AssociationAccessStep a -> (a.associationObject() ? "\\" : "") + a.name();
            case PathStep.AssociationSourceStep a -> a.name() + "[" + name(a.association()) + "]";
            case PathStep.UnresolvedStep u -> u.name();
            case PathStep.IndexStep i ->
                    "["
                            + i.index()
                                    .position()
                                    .map(Object::toString)
                                    .orElse(i.index().kind().name())
                            + "]";
        };
    }

    String operator(String op) {
        return switch (op) {
            case "EQ" -> "==";
            case "NEQ" -> "<>";
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            case "LTE" -> "<=";
            case "PLUS" -> "+";
            case "MINUS" -> "-";
            case "MUL" -> "*";
            case "DIV" -> "/";
            case "POW" -> "**";
            default -> op;
        };
    }

    String constraint(ConstraintDefinition c) {
        return switch (c) {
            case MandatoryConstraint m ->
                    "MANDATORY CONSTRAINT "
                            + m.name().map(n -> n + ": ").orElse("")
                            + expression(m.condition());
            case DomainConstraint d -> "CONSTRAINT " + expression(d.condition());
            case PlausibilityConstraint p ->
                    "CONSTRAINT "
                            + operator(p.direction().name())
                            + " "
                            + p.percentage().toPlainString()
                            + " % "
                            + expression(p.condition());
            case UniquenessConstraint u ->
                    "UNIQUE "
                            + u.name().map(n -> n + ": ").orElse("")
                            + (u.basket() ? "(BASKET) " : "")
                            + u.where().map(e -> "WHERE " + expression(e) + ": ").orElse("")
                            + u.keys().stream()
                                    .map(this::expression)
                                    .collect(Collectors.joining(", "))
                            + u.localKeys().stream()
                                    .map(
                                            k ->
                                                    "(LOCAL) "
                                                            + expression(k.prefix())
                                                            + ": "
                                                            + k.fields().stream()
                                                                    .map(this::expression)
                                                                    .collect(
                                                                            Collectors.joining(
                                                                                    ", ")))
                                    .collect(Collectors.joining("; "));
            case SetConstraint s ->
                    "SET CONSTRAINT "
                            + s.name().map(n -> n + ": ").orElse("")
                            + s.scope()
                            + " "
                            + s.where().map(e -> "WHERE " + expression(e) + ": ").orElse("")
                            + expression(s.condition());
            case ExistenceConstraint e ->
                    "EXISTENCE CONSTRAINT "
                            + e.name().map(n -> n + ": ").orElse("")
                            + expression(e.source())
                            + " REQUIRED IN "
                            + e.targets().stream()
                                    .map(t -> name(t.viewable()) + ": " + expression(t.path()))
                                    .collect(Collectors.joining(" OR "));
        };
    }
}
