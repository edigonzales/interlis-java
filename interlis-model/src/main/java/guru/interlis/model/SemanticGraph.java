package guru.interlis.model;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable semantic graph. All collections are defensive unmodifiable copies;
 * IDs are the only graph edges, so equals() never walks a cyclic object graph.
 */
public final class SemanticGraph {
    private final SnapshotId snapshotId;
    private final Map<SymbolId, Symbol> symbols;
    private final Map<TypeId, TypeDescriptor> types;
    private final Map<ExpressionId, TypedExpression> expressions;
    private final Map<ReferenceId, NameReference> references;
    private final Map<SymbolId, TypeId> declaredTypes;
    private final Map<SymbolId, EffectiveType> effectiveTypes;
    private final Map<SymbolId, List<EffectiveMember>> effectiveMembers;
    private final Map<QualifiedName, List<SymbolId>> qualifiedNames;
    private final List<SymbolId> rootModels;
    private final List<SymbolId> models;
    private final Set<LanguageVersion> languageVersions;
    private final Map<SourceId, SourceRevision> sourceRevisions;

    public SemanticGraph(SnapshotId snapshotId, Collection<Symbol> symbols,
                         Collection<TypeDescriptor> types,
                         Collection<TypedExpression> expressions,
                         Collection<NameReference> references,
                         Map<SymbolId, TypeId> declaredTypes,
                         Map<SymbolId, EffectiveType> effectiveTypes,
                         Map<SymbolId, List<EffectiveMember>> effectiveMembers,
                         List<SymbolId> rootModels, List<SymbolId> models,
                         Set<LanguageVersion> languageVersions,
                         Map<SourceId, SourceRevision> sourceRevisions) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.symbols = index(symbols, Symbol::id, "symbol");
        this.types = index(types, TypeDescriptor::id, "type");
        this.expressions = index(expressions, TypedExpression::id, "expression");
        this.references = index(references, NameReference::id, "reference");
        this.declaredTypes = immutableMap(declaredTypes, "declaredTypes");
        this.effectiveTypes = immutableMap(effectiveTypes, "effectiveTypes");
        this.rootModels = List.copyOf(Objects.requireNonNull(rootModels, "rootModels"));
        this.models = List.copyOf(Objects.requireNonNull(models, "models"));
        this.languageVersions = Set.copyOf(Objects.requireNonNull(languageVersions, "languageVersions"));
        this.sourceRevisions = immutableMap(sourceRevisions, "sourceRevisions");
        Map<SymbolId, List<EffectiveMember>> members = new LinkedHashMap<>();
        Objects.requireNonNull(effectiveMembers, "effectiveMembers")
                .forEach((key, value) -> members.put(key, List.copyOf(value)));
        this.effectiveMembers = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        this.qualifiedNames = buildQualifiedNames(this.symbols);
        validate();
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source, String name) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(source, name)));
    }

    private static <K, V> Map<K, V> index(Collection<V> values,
                                          java.util.function.Function<V, K> key,
                                          String category) {
        Map<K, V> map = new LinkedHashMap<>();
        for (V value : Objects.requireNonNull(values, category)) {
            if (map.put(key.apply(value), value) != null) {
                throw new IllegalArgumentException("duplicate " + category + " id");
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private static Map<QualifiedName, List<SymbolId>> buildQualifiedNames(Map<SymbolId, Symbol> symbols) {
        Map<QualifiedName, List<SymbolId>> result = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            QualifiedName name = qualifiedName(symbol, symbols);
            if (name != null) {
                result.computeIfAbsent(name, ignored -> new ArrayList<>()).add(symbol.id());
            }
        }
        Map<QualifiedName, List<SymbolId>> frozen = new LinkedHashMap<>();
        result.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(new LinkedHashMap<>(frozen));
    }

    private static QualifiedName qualifiedName(Symbol symbol, Map<SymbolId, Symbol> symbols) {
        if (symbol.name().synthetic()) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        Symbol current = symbol;
        int guard = 0;
        while (current != null && guard++ < 64) {
            segments.addFirst(current.name().value());
            Optional<SymbolId> owner = current.owner();
            current = owner.map(symbols::get).orElse(null);
        }
        return segments.isEmpty() ? null : new QualifiedName(segments);
    }

    /**
     * Returns the {@code validate} value.
     *
     * @return the value
     */
    private void validate() {
        for (Symbol symbol : symbols.values()) {
            symbol.owner().ifPresent(owner -> {
                if (!symbols.containsKey(owner)) {
                    throw new IllegalArgumentException("missing owner " + owner + " for " + symbol.id());
                }
            });
            switch (symbol) {
                case ClassSymbol c -> {
                    c.baseType().ifPresent(base -> requireKind(base, SymbolKind.CLASS));
                    c.members().forEach(this::requireSymbol);
                }
                case StructureSymbol s -> {
                    s.baseType().ifPresent(base -> requireKind(base, SymbolKind.STRUCTURE));
                    s.members().forEach(this::requireSymbol);
                }
                case TopicSymbol t -> {
                    t.baseTopic().ifPresent(base -> requireKind(base, SymbolKind.TOPIC));
                    t.members().forEach(this::requireSymbol);
                }
                case AttributeSymbol a -> requireType(a.declaredType());
                case RoleSymbol r -> requireKind(r.target(), SymbolKind.CLASS);
                case DomainSymbol d -> requireType(d.typeId());
                case AssociationSymbol a -> {
                    a.roles().forEach(id -> requireKind(id, SymbolKind.ROLE));
                    a.attributes().forEach(id -> requireKind(id, SymbolKind.ATTRIBUTE));
                }
                case ModelSymbol m -> m.members().forEach(this::requireSymbol);
                case ViewSymbol v -> v.members().forEach(this::requireSymbol);
                default -> {
                }
            }
        }
        for (TypeDescriptor type : types.values()) {
            switch (type) {
                case DomainReferenceType d -> requireSymbol(d.targetDomain());
                case ReferenceType r -> requireSymbol(r.target());
                case StructureValueType s -> requireSymbol(s.target());
                case CollectionType c -> requireType(c.elementType());
                case ErrorType e -> {
                    if (e.diagnosticIds().isEmpty()) {
                        throw new IllegalArgumentException("error type without diagnostic");
                    }
                }
                case PendingType ignored -> {
                }
                default -> {
                }
            }
        }
        for (TypedExpression expression : expressions.values()) {
            requireType(expression.typeId());
            expression.children().forEach(child -> {
                if (!expressions.containsKey(child)) {
                    throw new IllegalArgumentException("missing expression child " + child);
                }
            });
        }
        for (NameReference reference : references.values()) {
            reference.segments().forEach(segment -> segment.target().ifPresent(this::requireSymbol));
        }
        for (Map.Entry<SymbolId, TypeId> entry : declaredTypes.entrySet()) {
            requireSymbol(entry.getKey());
            requireType(entry.getValue());
        }
        for (Map.Entry<SymbolId, EffectiveType> entry : effectiveTypes.entrySet()) {
            requireSymbol(entry.getKey());
            requireType(entry.getValue().declaredType());
            requireType(entry.getValue().effectiveType());
        }
        for (Map.Entry<SymbolId, List<EffectiveMember>> entry : effectiveMembers.entrySet()) {
            requireSymbol(entry.getKey());
            for (EffectiveMember member : entry.getValue()) {
                requireSymbol(member.declaredSymbol());
                requireSymbol(member.effectiveOwner());
            }
        }
        rootModels.forEach(this::requireSymbol);
        models.forEach(this::requireSymbol);
    }

    private void requireSymbol(SymbolId id) {
        if (!symbols.containsKey(id)) {
            throw new IllegalArgumentException("missing symbol " + id);
        }
    }

    private void requireType(TypeId id) {
        if (!types.containsKey(id)) {
            throw new IllegalArgumentException("missing type " + id);
        }
    }

    private void requireKind(SymbolId id, SymbolKind kind) {
        Symbol symbol = symbols.get(id);
        if (symbol == null || symbol.kind() != kind) {
            throw new IllegalArgumentException("symbol " + id + " is not " + kind);
        }
    }


    /**
     * Returns a graph view restricted to the given model subtrees. Shared
     * immutable symbols/types/expressions are reused, not re-analyzed.
     */
    public SemanticGraph subsetForModels(Set<SymbolId> allowedModels,
                                         List<SymbolId> schemaRoots) {
        Objects.requireNonNull(allowedModels, "allowedModels");
        Set<SymbolId> selected = new LinkedHashSet<>();
        for (Symbol symbol : symbols.values()) {
            SymbolId current = symbol.id();
            int guard = 0;
            while (current != null && guard++ < 64) {
                if (allowedModels.contains(current)) {
                    selected.add(symbol.id());
                    break;
                }
                Optional<SymbolId> owner = symbols.get(current) == null
                        ? Optional.empty() : symbols.get(current).owner();
                current = owner.orElse(null);
            }
        }
        selected.retainAll(symbols.keySet());
        List<Symbol> selectedSymbols = selected.stream().map(symbols::get).toList();
        Set<TypeId> selectedTypes = new LinkedHashSet<>();
        Set<ExpressionId> selectedExpressions = new LinkedHashSet<>();
        for (Symbol symbol : selectedSymbols) {
            TypeId declared = declaredTypes.get(symbol.id());
            if (declared != null) {
                collectType(declared, selectedTypes);
            }
            if (symbol instanceof ConstraintSymbol constraint) {
                collectConstraint(constraint.definition(), selectedExpressions);
            }
        }
        for (ExpressionId expressionId : List.copyOf(selectedExpressions)) {
            collectExpressionTypes(expressionId, selectedTypes, selectedExpressions, new LinkedHashSet<>());
        }
        List<TypedExpression> selectedExpressionList = selectedExpressions.stream()
                .map(expressions::get).filter(Objects::nonNull).toList();
        Map<SymbolId, TypeId> selectedDeclared = new LinkedHashMap<>();
        declaredTypes.forEach((key, value) -> {
            if (selected.contains(key)) {
                selectedDeclared.put(key, value);
            }
        });
        Map<SymbolId, EffectiveType> selectedEffective = new LinkedHashMap<>();
        effectiveTypes.forEach((key, value) -> {
            if (selected.contains(key)) {
                selectedEffective.put(key, value);
            }
        });
        Map<SymbolId, List<EffectiveMember>> selectedMembers = new LinkedHashMap<>();
        effectiveMembers.forEach((key, value) -> {
            if (selected.contains(key)) {
                selectedMembers.put(key, value);
            }
        });
        List<NameReference> selectedReferences = references.values().stream()
                .filter(ref -> ref.segments().stream()
                        .anyMatch(segment -> segment.target().filter(selected::contains).isPresent()))
                .toList();
        Set<SourceId> selectedSources = new LinkedHashSet<>();
        for (Symbol symbol : selectedSymbols) {
            if (symbol.origin() instanceof Origin.DeclaredOrigin declared) {
                selectedSources.add(declared.range().source());
            }
        }
        Map<SourceId, SourceRevision> selectedRevisions = new LinkedHashMap<>();
        sourceRevisions.forEach((key, value) -> {
            if (selectedSources.contains(key)) {
                selectedRevisions.put(key, value);
            }
        });
        List<SymbolId> selectedRoots = schemaRoots.stream().filter(selected::contains).toList();
        List<SymbolId> selectedModels = models.stream().filter(selected::contains).toList();
        return new SemanticGraph(snapshotId, selectedSymbols,
                selectedTypes.stream().map(types::get).filter(Objects::nonNull).toList(),
                selectedExpressionList, selectedReferences, selectedDeclared, selectedEffective,
                selectedMembers, selectedRoots, selectedModels, languageVersions, selectedRevisions);
    }

    private void collectType(TypeId id, Set<TypeId> target) {
        if (!target.add(id)) {
            return;
        }
        TypeDescriptor type = types.get(id);
        if (type == null) {
            return;
        }
        switch (type) {
            case CollectionType collection -> collectType(collection.elementType(), target);
            case DomainReferenceType reference -> {
                // The target domain symbol is selected with its model; only the
                // type chain is needed here.
            }
            case StructureValueType ignored -> {
            }
            case ReferenceType ignored -> {
            }
            default -> {
            }
        }
    }

    private void collectConstraint(ConstraintDefinition definition, Set<ExpressionId> target) {
        switch (definition) {
            case MandatoryConstraint mandatory -> target.add(mandatory.condition());
            case PlausibilityConstraint plausibility -> target.add(plausibility.condition());
            case UniquenessConstraint uniqueness -> uniqueness.where().ifPresent(target::add);
            case DomainConstraint domain -> target.add(domain.condition());
            case SetConstraint set -> {
                set.where().ifPresent(target::add);
                target.add(set.condition());
            }
            case ExistenceConstraint ignored -> {
            }
        }
    }

    private void collectExpressionTypes(ExpressionId id, Set<TypeId> targetTypes,
                                        Set<ExpressionId> targetExpressions,
                                        Set<ExpressionId> visited) {
        if (!visited.add(id)) {
            return;
        }
        TypedExpression expression = expressions.get(id);
        if (expression == null) {
            return;
        }
        collectType(expression.typeId(), targetTypes);
        for (ExpressionId child : expression.children()) {
            targetExpressions.add(child);
            collectExpressionTypes(child, targetTypes, targetExpressions, visited);
        }
    }

    /**
     * Returns the {@code snapshotId} value.
     *
     * @return the value
     */
    public SnapshotId snapshotId() {
        return snapshotId;
    }

    /**
     * Returns the {@code symbols} value.
     *
     * @return the value
     */
    public Collection<Symbol> symbols() {
        return symbols.values();
    }

    /**
     * Returns the {@code types} value.
     *
     * @return the value
     */
    public Collection<TypeDescriptor> types() {
        return types.values();
    }

    /**
     * Returns the {@code expressions} value.
     *
     * @return the value
     */
    public Collection<TypedExpression> expressions() {
        return expressions.values();
    }

    /**
     * Returns the {@code references} value.
     *
     * @return the value
     */
    public Collection<NameReference> references() {
        return references.values();
    }

    /**
     * Executes {@code symbol}.
     *
     * @param id the {@code id} argument
     *
     * @return the result
     */
    public Optional<Symbol> symbol(SymbolId id) {
        return Optional.ofNullable(symbols.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Executes {@code type}.
     *
     * @param id the {@code id} argument
     *
     * @return the result
     */
    public Optional<TypeDescriptor> type(TypeId id) {
        return Optional.ofNullable(types.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Executes {@code expression}.
     *
     * @param id the {@code id} argument
     *
     * @return the result
     */
    public Optional<TypedExpression> expression(ExpressionId id) {
        return Optional.ofNullable(expressions.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Executes {@code reference}.
     *
     * @param id the {@code id} argument
     *
     * @return the result
     */
    public Optional<NameReference> reference(ReferenceId id) {
        return Optional.ofNullable(references.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Executes {@code declaredType}.
     *
     * @param id the {@code id} argument
     *
     * @return the result
     */
    public Optional<TypeId> declaredType(SymbolId id) {
        return Optional.ofNullable(declaredTypes.get(id));
    }

    /**
     * Executes {@code effectiveType}.
     *
     * @param id the {@code id} argument
     *
     * @return the result
     */
    public Optional<EffectiveType> effectiveType(SymbolId id) {
        return Optional.ofNullable(effectiveTypes.get(id));
    }

    /**
     * Executes {@code effectiveMembers}.
     *
     * @param viewable the {@code viewable} argument
     *
     * @return the result
     */
    public List<EffectiveMember> effectiveMembers(SymbolId viewable) {
        return effectiveMembers.getOrDefault(viewable, List.of());
    }

    /**
     * Executes {@code hasEffectiveMembers}.
     *
     * @param viewable the {@code viewable} argument
     *
     * @return the result
     */
    public boolean hasEffectiveMembers(SymbolId viewable) {
        return effectiveMembers.containsKey(viewable);
    }

    /**
     * Returns the {@code qualifiedNames} value.
     *
     * @return the value
     */
    public Map<QualifiedName, List<SymbolId>> qualifiedNames() {
        return qualifiedNames;
    }

    /**
     * Returns the {@code rootModels} value.
     *
     * @return the value
     */
    public List<SymbolId> rootModels() {
        return rootModels;
    }

    /**
     * Returns the {@code models} value.
     *
     * @return the value
     */
    public List<SymbolId> models() {
        return models;
    }

    /**
     * Returns the {@code languageVersions} value.
     *
     * @return the value
     */
    public Set<LanguageVersion> languageVersions() {
        return languageVersions;
    }

    /**
     * Returns the {@code sourceRevisions} value.
     *
     * @return the value
     */
    public Map<SourceId, SourceRevision> sourceRevisions() {
        return sourceRevisions;
    }

    /**
     * Returns the {@code declarationsInOrder} value.
     *
     * @return the value
     */
    public List<Symbol> declarationsInOrder() {
        List<Symbol> result = new ArrayList<>(symbols.values());
        result.sort(java.util.Comparator
                .comparing((Symbol s) -> s.origin() instanceof Origin.DeclaredOrigin d
                        ? d.range().source().value() : "")
                .thenComparingInt(s -> s.origin() instanceof Origin.DeclaredOrigin d
                        ? d.range().startOffset() : Integer.MAX_VALUE)
                .thenComparingInt(Symbol::declarationOrder)
                .thenComparing(s -> s.id().value()));
        return List.copyOf(result);
    }

    /**
     * Returns the {@code allSymbolIds} value.
     *
     * @return the value
     */
    public Set<SymbolId> allSymbolIds() {
        return new LinkedHashSet<>(symbols.keySet());
    }
}
