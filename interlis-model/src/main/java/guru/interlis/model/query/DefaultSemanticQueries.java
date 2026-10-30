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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

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
        Symbol owner = graph.symbol(new SymbolId(scope.scope().value())).orElse(null);
        if (owner == null) return new QueryResult.Unresolved<>(guru.interlis.model.UnresolvedReason.UNKNOWN_ID, List.of());
        var result = new ArrayList<SymbolRef>();
        var names = new java.util.HashSet<String>();
        var seen = new java.util.HashSet<SymbolId>();
        Symbol current = owner;
        guru.interlis.model.ModelSymbol model = null;
        while (current != null && seen.add(current.id())) {
            addVisible(result, names, scopeMembers(current), prefix);
            if (current instanceof guru.interlis.model.ModelSymbol selected) model = selected;
            current = current.owner().flatMap(graph::symbol).orElse(null);
        }
        if (model != null) {
            var importedMembers = new ArrayList<Symbol>();
            var qualifiers = new ArrayList<Symbol>(); qualifiers.add(model);
            for (var imported : model.imports()) {
                imported.targetModel().flatMap(graph::symbol).ifPresent(target -> {
                    qualifiers.add(target);
                    if (imported.unqualified()) importedMembers.addAll(scopeMembers(target));
                });
            }
            addVisible(result, names, importedMembers, prefix);
            addVisible(result, names, qualifiers, prefix);
        }
        var language = owner.languageVersion();
        addVisible(result, names, graph.symbols().stream()
                .filter(symbol -> symbol.origin() instanceof guru.interlis.model.Origin.PredefinedOrigin)
                .filter(symbol -> symbol.owner().isEmpty() && symbol.languageVersion() == language)
                .filter(symbol -> symbol.kind() != SymbolKind.ENUM_ELEMENT).toList(), prefix);
        return new QueryResult.Known<>(List.copyOf(result));
    }

    private List<Symbol> scopeMembers(Symbol owner) {
        var result = new LinkedHashMap<SymbolId,Symbol>();
        graph.symbols().stream().filter(symbol -> symbol.owner().filter(owner.id()::equals).isPresent())
                .forEach(symbol -> result.put(symbol.id(), symbol));
        if (graph.hasEffectiveMembers(owner.id())) for (var member : graph.effectiveMembers(owner.id()))
            graph.symbol(member.declaredSymbol()).ifPresent(symbol -> result.putIfAbsent(symbol.id(), symbol));
        var seen = new java.util.HashSet<SymbolId>();
        Symbol current = owner;
        while (current instanceof guru.interlis.model.TopicSymbol topic && topic.baseTopic().isPresent()
                && seen.add(current.id())) {
            current = graph.symbol(topic.baseTopic().orElseThrow()).orElse(null);
            if (current == null) break;
            SymbolId base = current.id();
            var occupied = result.values().stream().map(symbol -> namespace(symbol.kind()) + ":" + symbol.name().value())
                    .collect(java.util.stream.Collectors.toSet());
            graph.symbols().stream().filter(symbol -> symbol.owner().filter(base::equals).isPresent())
                    .filter(symbol -> !occupied.contains(namespace(symbol.kind()) + ":" + symbol.name().value()))
                    .forEach(symbol -> result.putIfAbsent(symbol.id(), symbol));
        }
        return List.copyOf(result.values());
    }

    @Override
    public QueryResult<List<SymbolRef>> membersOf(SymbolRef owner) {
        return symbol(owner).map(value -> scopeMembers(value).stream()
                .filter(member -> !member.name().synthetic())
                .map(member -> new SymbolRef(graph.snapshotId(), member.id())).toList());
    }

    private void addVisible(List<SymbolRef> result, Set<String> seen, List<Symbol> candidates,
                            NamePrefix prefix) {
        var local = new java.util.HashSet<String>();
        candidates.stream().filter(symbol -> !symbol.name().synthetic() && symbol.name().value().startsWith(prefix.value()))
                .sorted(Comparator.comparing((Symbol symbol) -> symbol.name().value()).thenComparing(symbol -> symbol.id().value()))
                .forEach(symbol -> {
                    String key = namespace(symbol.kind()) + ":" + symbol.name().value();
                    if (!seen.contains(key)) {
                        result.add(new SymbolRef(graph.snapshotId(), symbol.id())); local.add(key);
                    }
                });
        seen.addAll(local);
    }

    private static String namespace(SymbolKind kind) {
        return switch (kind) {
            case CLASS, STRUCTURE, ASSOCIATION, DOMAIN, VIEW -> "TYPE";
            case ATTRIBUTE, ROLE -> "MEMBER";
            default -> kind.name();
        };
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
