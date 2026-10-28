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
            if (!seen.add(current.id())) {
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
        if (current instanceof guru.interlis.model.ErrorType error) return new QueryResult.Invalid<>(error.diagnosticIds());
        if (current instanceof guru.interlis.model.PendingType pending) return new QueryResult.NotAnalyzed<>(switch(pending.reason()) {
            case NOT_ANALYZED -> NotAnalyzedReason.NOT_REQUESTED;
            case MISSING_IMPLEMENTATION -> NotAnalyzedReason.MISSING_IMPLEMENTATION;
            case CANCELLED -> NotAnalyzedReason.CANCELLED;
            case BUDGET_EXCEEDED -> NotAnalyzedReason.BUDGET_EXCEEDED;
        });
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
        if(guru.interlis.model.internal.TypeCompatibility.isUndefined(s) && requiredDomain(target.type(),targetResolved.known().orElseThrow().aliasPath()))
            return new QueryResult.Known<>(disallowed("mandatory-value-excludes-undefined"));
        if(s instanceof guru.interlis.model.AttributePathType a && t instanceof guru.interlis.model.AttributePathType b
                && !a.owner().equals(b.owner()) && (dependentOwner(a) || dependentOwner(b))) {
            var valueOnly = new guru.interlis.model.AttributePathType(b.id(),b.origin(),Optional.empty(),b.restrictions());
            var relation = assignment(a,valueOnly,context);
            if(relation.decision()==RelationDecision.DISALLOWED)return new QueryResult.Known<>(relation);
            return new QueryResult.NotAnalyzed<>(guru.interlis.model.NotAnalyzedReason.PENDING);
        }
        return new QueryResult.Known<>(assignment(s,t,context));
    }

    private boolean requiredDomain(TypeId declared,List<guru.interlis.model.SymbolId> aliases) {
        return aliases.stream().map(graph::symbol).flatMap(Optional::stream).filter(guru.interlis.model.DomainSymbol.class::isInstance)
                    .map(guru.interlis.model.DomainSymbol.class::cast).anyMatch(guru.interlis.model.DomainSymbol::mandatory)
                || graph.symbols().stream().filter(guru.interlis.model.DomainSymbol.class::isInstance).map(guru.interlis.model.DomainSymbol.class::cast)
                    .anyMatch(domain->domain.typeId().equals(declared) && domain.mandatory());
    }

    private TypeRelation assignment(TypeDescriptor s,TypeDescriptor t,AssignmentContext context) {
        return guru.interlis.model.internal.TypeCompatibility.assign(s, t,
                id -> resolveAliases(new TypeRef(graph.snapshotId(), id)).known()
                        .flatMap(e -> graph.type(e.effectiveType())).orElse(null),
                this::declarationBase,id -> graph.declaredType(id).flatMap(type->resolveAliases(new TypeRef(graph.snapshotId(),type)).known())
                        .flatMap(e->graph.type(e.effectiveType())).orElse(null), context);
    }

    private guru.interlis.model.SymbolId declarationBase(guru.interlis.model.SymbolId id) {
        return switch(graph.symbol(id).orElse(null)) {
            case guru.interlis.model.DomainSymbol x -> x.baseDomain().orElse(null);
            case guru.interlis.model.ClassSymbol x -> x.baseType().orElse(null);
            case guru.interlis.model.StructureSymbol x -> x.baseType().orElse(null);
            case guru.interlis.model.AssociationSymbol x -> x.baseAssociation().orElse(null);
            case guru.interlis.model.ViewSymbol x -> x.baseView().orElse(null);
            case guru.interlis.model.AttributeSymbol x -> x.owner().map(this::declarationBase)
                    .map(parent->graph.effectiveMembers(parent).stream().map(guru.interlis.model.EffectiveMember::declaredSymbol)
                        .map(graph::symbol).flatMap(Optional::stream).filter(guru.interlis.model.AttributeSymbol.class::isInstance)
                        .filter(member->member.name().value().equals(x.name().value())).map(guru.interlis.model.Symbol::id).findFirst().orElse(null)).orElse(null);
            case null, default -> null;
        };
    }

    private static boolean dependentOwner(guru.interlis.model.AttributePathType type) {
        return type.owner().filter(owner -> !(owner instanceof guru.interlis.model.AttributePathType.Viewable)).isPresent();
    }

    private static TypeRelation allowed(String reason) {
        return new TypeRelation(RelationDecision.ALLOWED, reason, List.of());
    }

    private static TypeRelation disallowed(String reason) {
        return new TypeRelation(RelationDecision.DISALLOWED, reason, List.of());
    }

    /**
     * Checks extension of alias-resolved types in this snapshot. Scalar numeric
     * extension additionally preserves a concrete unit or specializes an
     * abstract unit; it never treats convertibility as permission to change a
     * unit. Effective inherited units must already be present in the graph.
     * Unknown, invalid and pending type results retain the assignment query's
     * corresponding result state; mismatched snapshots are rejected.
     */
    public QueryResult<TypeRelation> canExtend(TypeRef derived, TypeRef base,
                                               ExtensionContext context, guru.interlis.model.LanguageVersion language) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(language, "language");
        QueryResult<EffectiveType> derivedResolved = resolveAliases(derived);
        QueryResult<EffectiveType> baseResolved = resolveAliases(base);
        if (derivedResolved instanceof QueryResult.Known<EffectiveType> d
                && baseResolved instanceof QueryResult.Known<EffectiveType> b)
            return new QueryResult.Known<>(guru.interlis.model.internal.TypeCompatibility.extend(
                    graph.type(d.value().effectiveType()).orElseThrow(), graph.type(b.value().effectiveType()).orElseThrow(), language,
                    id -> resolveAliases(new TypeRef(graph.snapshotId(),id)).known().flatMap(e -> graph.type(e.effectiveType())).orElse(null),
                    this::declarationBase, id -> graph.declaredType(id).flatMap(type -> resolveAliases(new TypeRef(graph.snapshotId(),type)).known())
                            .flatMap(e -> graph.type(e.effectiveType())).orElse(null),
                    id -> graph.symbol(id).filter(guru.interlis.model.UnitSymbol.class::isInstance)
                            .map(guru.interlis.model.UnitSymbol.class::cast).orElse(null)));
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
            return new QueryResult.Known<>(guru.interlis.model.internal.TypeCompatibility.compare(ld,rd,operator,
                    id -> graph.type(id).orElse(null),id -> graph.declaredType(id).flatMap(type -> resolveAliases(new TypeRef(graph.snapshotId(),type)).known())
                            .flatMap(e -> graph.type(e.effectiveType())).orElse(null)));
        }
        if (l instanceof QueryResult.Invalid<EffectiveType> invalid) return new QueryResult.Invalid<>(invalid.diagnosticIds());
        if (r instanceof QueryResult.Invalid<EffectiveType> invalid) return new QueryResult.Invalid<>(invalid.diagnosticIds());
        if (l instanceof QueryResult.Unresolved<EffectiveType> unresolved) return new QueryResult.Unresolved<>(unresolved.reason(), unresolved.candidates());
        if (r instanceof QueryResult.Unresolved<EffectiveType> unresolved) return new QueryResult.Unresolved<>(unresolved.reason(), unresolved.candidates());
        if (l instanceof QueryResult.NotAnalyzed<EffectiveType> pending) return new QueryResult.NotAnalyzed<>(pending.reason());
        if (r instanceof QueryResult.NotAnalyzed<EffectiveType> pending) return new QueryResult.NotAnalyzed<>(pending.reason());
        return new QueryResult.NotAnalyzed<>(NotAnalyzedReason.PENDING);
    }

    /** Compares identities or statically declared conversion relationships in
     * this snapshot, including composed units. No numeric factor or external
     * function is executed. Abstract ancestry alone is not a conversion.
     * Unknown IDs and non-unit symbols are Unresolved, incomplete dependency
     * graphs are NotAnalyzed, and both snapshot references are checked first. */
    public QueryResult<UnitRelation> compareUnits(guru.interlis.model.SymbolRef left,
                                                 guru.interlis.model.SymbolRef right) {
        Objects.requireNonNull(left, "left"); Objects.requireNonNull(right, "right");
        if (!graph.snapshotId().equals(left.snapshot()))
            throw new guru.interlis.model.SnapshotMismatchException(graph.snapshotId(), left.snapshot());
        if (!graph.snapshotId().equals(right.snapshot()))
            throw new guru.interlis.model.SnapshotMismatchException(graph.snapshotId(), right.snapshot());
        var l = graph.symbol(left.symbol()).orElse(null); var r = graph.symbol(right.symbol()).orElse(null);
        if (l == null || r == null) return new QueryResult.Unresolved<>(guru.interlis.model.UnresolvedReason.UNKNOWN_ID, List.of());
        if (!(l instanceof guru.interlis.model.UnitSymbol) || !(r instanceof guru.interlis.model.UnitSymbol))
            return new QueryResult.Unresolved<>(guru.interlis.model.UnresolvedReason.NOT_A_UNIT, List.of());
        var incomplete = graph.references().stream()
                .filter(reference -> !(reference.resolution() instanceof guru.interlis.model.BindingResolution.BoundTarget))
                .map(reference -> reference.originalScope().value()).collect(java.util.stream.Collectors.toSet());
        return guru.interlis.model.internal.TypeCompatibility.unitRelation(left.symbol(), right.symbol(),
                id -> incomplete.contains(id.value()) ? null : graph.symbol(id).filter(guru.interlis.model.UnitSymbol.class::isInstance)
                        .map(guru.interlis.model.UnitSymbol.class::cast).orElse(null))
                .<QueryResult<UnitRelation>>map(QueryResult.Known::new)
                .orElseGet(() -> new QueryResult.NotAnalyzed<>(NotAnalyzedReason.PENDING));
    }
}
