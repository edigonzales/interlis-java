package guru.interlis.model.query;

import guru.interlis.model.AssignmentContext;
import guru.interlis.model.BooleanType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ComparisonOperator;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.ExtensionContext;
import guru.interlis.model.NotAnalyzedReason;
import guru.interlis.model.NumericType;
import guru.interlis.model.RelationDecision;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypeRef;
import guru.interlis.model.TypeRelation;
import guru.interlis.model.UnitRelation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/** Stateless type-relation service over one immutable graph. */
public final class TypeRelations {
    private final SemanticGraph graph;
    private final DefaultSemanticQueries queries;

    /**
     * Creates an instance.
     *
     * @param graph the {@code graph} argument
     */
    public TypeRelations(SemanticGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.queries = new DefaultSemanticQueries(graph);
    }

    /**
     * Executes {@code resolveAliases}.
     *
     * @param type the {@code type} argument
     *
     * @return the result
     */
    public QueryResult<EffectiveType> resolveAliases(TypeRef type) {
        Objects.requireNonNull(type, "type");
        if (!graph.snapshotId().equals(type.snapshot())) {
            throw new guru.interlis.model.SnapshotMismatchException(graph.snapshotId(), type.snapshot());
        }
        TypeDescriptor descriptor = graph.type(type.type()).orElse(null);
        if (descriptor == null) {
            return new QueryResult.Unresolved<>(guru.interlis.model.UnresolvedReason.UNKNOWN_ID, List.of());
        }
        java.util.List<guru.interlis.model.SymbolId> aliasPath = new java.util.ArrayList<>();
        java.util.Set<TypeId> seen = new java.util.LinkedHashSet<>();
        TypeDescriptor current = descriptor;
        while (current instanceof DomainReferenceType reference) {
            if (!seen.add(current.id()) || aliasPath.size() > 64) {
                return new QueryResult.NotAnalyzed<>(NotAnalyzedReason.PENDING);
            }
            aliasPath.add(reference.targetDomain());
            current = graph.symbol(reference.targetDomain())
                    .flatMap(s -> s instanceof guru.interlis.model.DomainSymbol d
                            ? graph.type(d.typeId()) : Optional.empty())
                    .orElse(null);
            if (current == null) {
                return new QueryResult.Unresolved<>(
                        guru.interlis.model.UnresolvedReason.NAME_NOT_FOUND, List.of());
            }
        }
        return new QueryResult.Known<>(new EffectiveType(descriptor.id(), current.id(), aliasPath));
    }

    public QueryResult<TypeRelation> canAssign(TypeRef source, TypeRef target,
                                                AssignmentContext context) {
        Objects.requireNonNull(context, "context");
        QueryResult<EffectiveType> sourceResolved = resolveAliases(source);
        QueryResult<EffectiveType> targetResolved = resolveAliases(target);
        if (sourceResolved instanceof QueryResult.Invalid<EffectiveType> invalid) {
            return new QueryResult.Invalid<>(invalid.diagnosticIds());
        }
        if (sourceResolved instanceof QueryResult.NotAnalyzed<EffectiveType> pending) {
            return new QueryResult.NotAnalyzed<>(pending.reason());
        }
        if (targetResolved instanceof QueryResult.Invalid<EffectiveType> invalid) {
            return new QueryResult.Invalid<>(invalid.diagnosticIds());
        }
        if (targetResolved instanceof QueryResult.NotAnalyzed<EffectiveType> pending) {
            return new QueryResult.NotAnalyzed<>(pending.reason());
        }
        if (sourceResolved instanceof QueryResult.Unresolved<EffectiveType>
                || targetResolved instanceof QueryResult.Unresolved<EffectiveType>) {
            return new QueryResult.Unresolved<>(guru.interlis.model.UnresolvedReason.NOT_A_TYPE, List.of());
        }
        TypeDescriptor s = graph.type(sourceResolved.known().orElseThrow().effectiveType()).orElseThrow();
        TypeDescriptor t = graph.type(targetResolved.known().orElseThrow().effectiveType()).orElseThrow();
        return new QueryResult.Known<>(compare(s, t));
    }

    private TypeRelation compare(TypeDescriptor source, TypeDescriptor target) {
        if (source.id().equals(target.id())) {
            return allowed("same-type");
        }
        if (source instanceof NumericType s && target instanceof NumericType t) {
            return numericContained(s, t) ? allowed("numeric-range-contained")
                    : disallowed("numeric-range-widens");
        }
        if (source instanceof TextType s && target instanceof TextType t) {
            if (s.textKind() != t.textKind()) {
                return disallowed("text-kind-differs");
            }
            if (t.maxLength().isPresent()
                    && (s.maxLength().isEmpty() || s.maxLength().getAsInt() > t.maxLength().getAsInt())) {
                return disallowed("text-length-widens");
            }
            return allowed("text-compatible");
        }
        if (source instanceof BooleanType && target instanceof BooleanType) {
            return allowed("boolean-compatible");
        }
        if (source instanceof CollectionType s && target instanceof CollectionType t) {
            if (s.collectionKind() != t.collectionKind()) {
                return disallowed("collection-kind-differs");
            }
            TypeDescriptor se = graph.type(s.elementType()).orElseThrow();
            TypeDescriptor te = graph.type(t.elementType()).orElseThrow();
            TypeRelation element = compare(se, te);
            if (element.decision() != RelationDecision.ALLOWED) {
                return disallowed("collection-element-" + element.reason());
            }
            if (s.cardinality().lower().compareTo(t.cardinality().lower()) < 0
                    || upperCompare(s, t) > 0) {
                return disallowed("collection-cardinality-widens");
            }
            return allowed("collection-compatible");
        }
        return disallowed("incompatible-type-families");
    }

    private static int upperCompare(CollectionType s, CollectionType t) {
        if (t.cardinality().upper() instanceof guru.interlis.model.CardinalityUpper.Unbounded) {
            return 0;
        }
        if (s.cardinality().upper() instanceof guru.interlis.model.CardinalityUpper.Unbounded) {
            return 1;
        }
        var sf = (guru.interlis.model.CardinalityUpper.Finite) s.cardinality().upper();
        var tf = (guru.interlis.model.CardinalityUpper.Finite) t.cardinality().upper();
        return sf.value().compareTo(tf.value());
    }

    private static boolean numericContained(NumericType source, NumericType target) {
        return contained(source.range().minimum(), target.range().minimum(), true)
                && contained(source.range().maximum(), target.range().maximum(), false);
    }

    private static boolean contained(java.util.Optional<guru.interlis.model.DecimalLiteral> value,
                                     java.util.Optional<guru.interlis.model.DecimalLiteral> bound,
                                     boolean minimum) {
        if (bound.isEmpty()) {
            return true;
        }
        if (value.isEmpty()) {
            return false;
        }
        BigDecimal v = value.get().exactValue();
        BigDecimal b = bound.get().exactValue();
        return minimum ? v.compareTo(b) >= 0 : v.compareTo(b) <= 0;
    }

    private static TypeRelation allowed(String reason) {
        return new TypeRelation(RelationDecision.ALLOWED, reason, List.of());
    }

    private static TypeRelation disallowed(String reason) {
        return new TypeRelation(RelationDecision.DISALLOWED, reason, List.of());
    }

    public QueryResult<TypeRelation> canExtend(TypeRef derived, TypeRef base,
                                               ExtensionContext context) {
        Objects.requireNonNull(context, "context");
        return canAssign(derived, base, AssignmentContext.NORMAL);
    }

    public QueryResult<TypeRelation> areComparable(TypeRef left, TypeRef right,
                                                   ComparisonOperator operator) {
        QueryResult<EffectiveType> l = resolveAliases(left);
        QueryResult<EffectiveType> r = resolveAliases(right);
        if (l instanceof QueryResult.Known<EffectiveType> lk
                && r instanceof QueryResult.Known<EffectiveType> rk) {
            TypeDescriptor ld = graph.type(lk.value().effectiveType()).orElseThrow();
            TypeDescriptor rd = graph.type(rk.value().effectiveType()).orElseThrow();
            if (ld instanceof NumericType && rd instanceof NumericType) {
                return new QueryResult.Known<>(allowed("numeric-comparable"));
            }
            if (ld instanceof TextType && rd instanceof TextType
                    && operator == ComparisonOperator.EQUALITY) {
                return new QueryResult.Known<>(allowed("text-equality-comparable"));
            }
            if (ld instanceof BooleanType && rd instanceof BooleanType
                    && operator == ComparisonOperator.EQUALITY) {
                return new QueryResult.Known<>(allowed("boolean-equality-comparable"));
            }
            return new QueryResult.Known<>(disallowed("not-comparable"));
        }
        return new QueryResult.NotAnalyzed<>(NotAnalyzedReason.PENDING);
    }

    public UnitRelation compareUnits(guru.interlis.model.SymbolRef left,
                                     guru.interlis.model.SymbolRef right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.symbol().equals(right.symbol())) {
            return new UnitRelation(RelationDecision.ALLOWED, "same-unit");
        }
        return new UnitRelation(RelationDecision.DISALLOWED, "different-unit");
    }
}
