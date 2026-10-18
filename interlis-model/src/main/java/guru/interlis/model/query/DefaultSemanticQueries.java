package guru.interlis.model.query;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.BindingResolution;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.ExpressionRef;
import guru.interlis.model.LookupKind;
import guru.interlis.model.NamePrefix;
import guru.interlis.model.NameReference;
import guru.interlis.model.NotAnalyzedReason;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.ScopeRef;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.UnresolvedReason;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Default read-only query implementation over an immutable graph. */
public final class DefaultSemanticQueries implements SemanticQueries {
    private final SemanticGraph graph;

    /**
     * Creates an instance.
     *
     * @param graph the {@code graph} argument
     */
    public DefaultSemanticQueries(SemanticGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    @Override
    public SnapshotId snapshotId() {
        return graph.snapshotId();
    }

    @Override
    public SemanticGraph graph() {
        return graph;
    }

    private void check(SnapshotId snapshot) {
        if (!graph.snapshotId().equals(snapshot)) {
            throw new SnapshotMismatchException(graph.snapshotId(), snapshot);
        }
    }

    @Override
    public QueryResult<Symbol> symbol(SymbolRef ref) {
        Objects.requireNonNull(ref, "ref");
        check(ref.snapshot());
        return graph.symbol(ref.symbol())
                .<QueryResult<Symbol>>map(QueryResult.Known::new)
                .orElseGet(() -> new QueryResult.Unresolved<>(UnresolvedReason.UNKNOWN_ID, List.of()));
    }

    @Override
    public QueryResult<TypeDescriptor> type(TypeRef ref) {
        Objects.requireNonNull(ref, "ref");
        check(ref.snapshot());
        return graph.type(ref.type())
                .<QueryResult<TypeDescriptor>>map(QueryResult.Known::new)
                .orElseGet(() -> new QueryResult.Unresolved<>(UnresolvedReason.UNKNOWN_ID, List.of()));
    }

    @Override
    public QueryResult<TypedExpression> expression(ExpressionRef ref) {
        Objects.requireNonNull(ref, "ref");
        check(ref.snapshot());
        return graph.expression(ref.expression())
                .<QueryResult<TypedExpression>>map(QueryResult.Known::new)
                .orElseGet(() -> new QueryResult.Unresolved<>(UnresolvedReason.UNKNOWN_ID, List.of()));
    }

    @Override
    public QueryResult<TypeDescriptor> declaredType(SymbolRef ref) {
        Objects.requireNonNull(ref, "ref");
        check(ref.snapshot());
        return graph.declaredType(ref.symbol())
                .flatMap(graph::type)
                .<QueryResult<TypeDescriptor>>map(QueryResult.Known::new)
                .orElseGet(() -> new QueryResult.Unresolved<>(UnresolvedReason.NOT_A_TYPE, List.of()));
    }

    @Override
    public QueryResult<EffectiveType> effectiveType(SymbolRef ref) {
        Objects.requireNonNull(ref, "ref");
        check(ref.snapshot());
        return graph.effectiveType(ref.symbol())
                .<QueryResult<EffectiveType>>map(QueryResult.Known::new)
                .orElseGet(() -> new QueryResult.Unresolved<>(UnresolvedReason.NOT_A_TYPE, List.of()));
    }

    @Override
    public QueryResult<List<EffectiveMember>> effectiveMembers(SymbolRef viewable) {
        Objects.requireNonNull(viewable, "viewable");
        check(viewable.snapshot());
        if (graph.symbol(viewable.symbol()).isEmpty()) {
            return new QueryResult.Unresolved<>(UnresolvedReason.UNKNOWN_ID, List.of());
        }
        if (!graph.hasEffectiveMembers(viewable.symbol())) {
            return new QueryResult.NotAnalyzed<>(NotAnalyzedReason.PENDING);
        }
        return new QueryResult.Known<>(graph.effectiveMembers(viewable.symbol()));
    }

    @Override
    public QueryResult<SymbolRef> resolveReference(ReferenceId reference) {
        Objects.requireNonNull(reference, "reference");
        Optional<NameReference> maybe = graph.reference(reference);
        if (maybe.isEmpty()) {
            return new QueryResult.Unresolved<>(UnresolvedReason.UNKNOWN_ID, List.of());
        }
        BindingResolution resolution = maybe.get().resolution();
        return switch (resolution) {
            case BindingResolution.BoundTarget bound -> new QueryResult.Known<>(
                    new SymbolRef(graph.snapshotId(), bound.target()));
            case BindingResolution.UnresolvedTarget unresolved -> new QueryResult.Unresolved<>(
                    unresolved.reason(), unresolved.candidates().stream()
                    .map(id -> new SymbolRef(graph.snapshotId(), id)).toList());
            case BindingResolution.InvalidTarget invalid -> new QueryResult.Invalid<>(
                    invalid.diagnosticIds());
            case BindingResolution.NotAnalyzedTarget notAnalyzed -> new QueryResult.NotAnalyzed<>(
                    notAnalyzed.reason());
        };
    }

    @Override
    public QueryResult<List<SymbolRef>> visibleSymbols(ScopeRef scope, NamePrefix prefix) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(prefix, "prefix");
        check(scope.snapshot());
        List<SymbolRef> result = graph.symbols().stream()
                .filter(s -> s.name().value().startsWith(prefix.value()))
                .sorted(Comparator.comparingInt(Symbol::declarationOrder)
                        .thenComparing(s -> s.id().value()))
                .map(s -> new SymbolRef(graph.snapshotId(), s.id()))
                .toList();
        return new QueryResult.Known<>(result);
    }

    @Override
    public List<SymbolRef> findQualifiedName(QualifiedName name) {
        Objects.requireNonNull(name, "name");
        return graph.qualifiedNames().getOrDefault(name, List.of()).stream()
                .map(id -> new SymbolRef(graph.snapshotId(), id))
                .toList();
    }

    @Override
    public Optional<SymbolRef> rebindSymbol(SymbolId id) {
        Objects.requireNonNull(id, "id");
        return graph.symbol(id).map(s -> new SymbolRef(graph.snapshotId(), s.id()));
    }

    @Override
    public List<NameReference> referencesTo(SymbolRef target) {
        Objects.requireNonNull(target, "target");
        check(target.snapshot());
        return graph.references().stream()
                .filter(ref -> ref.segments().stream()
                        .anyMatch(segment -> segment.target().filter(target.symbol()::equals).isPresent()))
                .sorted(Comparator.comparing((NameReference r) -> r.range().source().value())
                        .thenComparingInt(r -> r.range().startOffset()))
                .toList();
    }

    @Override
    public List<SymbolRef> symbolsWithPrefix(NamePrefix prefix, LookupKind kind) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(kind, "kind");
        return graph.symbols().stream()
                .filter(s -> s.name().value().startsWith(prefix.value()))
                .filter(s -> matches(kind, s.kind()))
                .sorted(Comparator.comparing((Symbol s) -> s.name().value())
                        .thenComparing(s -> s.id().value()))
                .map(s -> new SymbolRef(graph.snapshotId(), s.id()))
                .toList();
    }

    private static boolean matches(LookupKind kind, SymbolKind symbolKind) {
        return switch (kind) {
            case MODEL -> symbolKind == SymbolKind.MODEL;
            case TOPIC -> symbolKind == SymbolKind.TOPIC;
            case TYPE, SCOPE -> symbolKind == SymbolKind.CLASS || symbolKind == SymbolKind.STRUCTURE
                    || symbolKind == SymbolKind.DOMAIN || symbolKind == SymbolKind.ASSOCIATION;
            case MEMBER -> symbolKind == SymbolKind.ATTRIBUTE || symbolKind == SymbolKind.ROLE;
            case VALUE -> true;
        };
    }
}
