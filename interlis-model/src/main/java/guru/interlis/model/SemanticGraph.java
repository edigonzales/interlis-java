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
        if (symbol.origin() instanceof Origin.PredefinedOrigin predefined
                && predefined.key().startsWith("INTERLIS.")) {
            return new QualifiedName(List.of(predefined.key().split("\\.")));
        }
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
    private void collectDrawingExpressions(DrawingRuleSymbol.Value value,Set<ExpressionId> result) {
        if(value instanceof DrawingRuleSymbol.ExpressionValue e) result.add(e.expression());
        if(value instanceof DrawingRuleSymbol.AccordingValue a) { result.add(a.selector()); a.cases().forEach(c->collectDrawingExpressions(c.value(),result)); }
    }

    private void validateDrawingValue(DrawingRuleSymbol.Value value) {
        if(value instanceof DrawingRuleSymbol.ExpressionValue e) requireExpression(e.expression());
        if(value instanceof DrawingRuleSymbol.MetaObjectValue m) m.object().ifPresent(id->requireKind(id,SymbolKind.METAOBJECT));
        if(value instanceof DrawingRuleSymbol.AccordingValue a) { requireExpression(a.selector()); a.cases().forEach(c->validateDrawingValue(c.value())); }
    }

    private void validateRange(NumericRange range) {
        range.unit().ifPresent(id -> requireKind(id, SymbolKind.UNIT));
        range.referenceSystem().ifPresent(r -> requireKind(r.target(), r.domain() ? SymbolKind.DOMAIN : SymbolKind.METAOBJECT));
    }

    private void validate() {
        for (Symbol symbol : symbols.values()) {
            symbol.owner().ifPresent(owner -> {
                if (!symbols.containsKey(owner)) {
                    throw new IllegalArgumentException("missing owner " + owner + " for " + symbol.id());
                }
            });
            symbol.header().oidRules().forEach(rule -> rule.domain().ifPresent(domain -> requireKind(domain, SymbolKind.DOMAIN)));
            switch (symbol) {
                case ClassSymbol c -> {
                    c.baseType().ifPresent(base -> {
                        requireSymbol(base);
                        SymbolKind kind = symbols.get(base).kind();
                        if (kind != SymbolKind.CLASS && kind != SymbolKind.STRUCTURE)
                            throw new IllegalArgumentException("class base is not a class or structure");
                    });
                    c.members().forEach(this::requireSymbol);
                }
                case StructureSymbol s -> {
                    s.baseType().ifPresent(base -> requireKind(base, SymbolKind.STRUCTURE));
                    s.members().forEach(this::requireSymbol);
                }
                case TopicSymbol t -> {
                    t.baseTopic().ifPresent(base -> requireKind(base, SymbolKind.TOPIC));
                    t.members().forEach(this::requireSymbol);
                    t.deferredGenerics().forEach(id -> requireKind(id, SymbolKind.DOMAIN));
                    t.genericBindings().forEach(binding -> {
                        requireKind(binding.genericDomain(), SymbolKind.DOMAIN);
                        binding.alternatives().forEach(id -> requireKind(id, SymbolKind.DOMAIN));
                        binding.contexts().forEach(id -> requireKind(id, SymbolKind.CONTEXT));
                    });
                }
                case MetadataBasketSymbol b -> { b.topic().ifPresent(id -> requireKind(id, SymbolKind.TOPIC)); b.baseBasket().ifPresent(id -> requireKind(id, SymbolKind.METADATA_BASKET)); b.objects().forEach(id -> requireKind(id, SymbolKind.METAOBJECT)); }
                case MetaObjectSymbol o -> o.objectClass().ifPresent(id -> requireKind(id, SymbolKind.CLASS));
                case LineFormSymbol f -> f.segmentStructure().ifPresent(id -> requireKind(id, SymbolKind.STRUCTURE));
                case UnitSymbol u -> {
                    u.base().ifPresent(id -> requireKind(id, SymbolKind.UNIT));
                    u.derivedFrom().ifPresent(id -> requireKind(id, SymbolKind.UNIT));
                    u.components().forEach(c -> requireKind(c.unit(), SymbolKind.UNIT));
                }
                case RoleSymbol r -> {
                    r.derivation().ifPresent(this::requireExpression);
                    for (RoleTarget target : r.targets()) {
                        var ids = new java.util.ArrayList<>(target.restrictions());
                        ids.add(target.base());
                        for (SymbolId id : ids) {
                            requireSymbol(id);
                            SymbolKind kind = symbols.get(id).kind();
                            if (kind != SymbolKind.CLASS && kind != SymbolKind.ASSOCIATION) {
                                throw new IllegalArgumentException("role target is not a class or association");
                            }
                        }
                    }
                }
                case FunctionSymbol f -> {
                    f.parameters().forEach(p -> requireType(p.typeId()));
                    requireType(f.returnType());
                }
                case DomainSymbol d -> requireType(d.typeId());
                case ConstraintSymbol c -> {
                    var ids = new java.util.LinkedHashSet<ExpressionId>();
                    collectConstraint(c.definition(), ids);
                    for (var id : ids) if (!expressions.containsKey(id))
                        throw new IllegalArgumentException("missing constraint expression " + id);
                    if (c.definition() instanceof ExistenceConstraint existence)
                        existence.targets().forEach(t -> requireSymbol(t.viewable()));
                }
                case AssociationSymbol a -> {
                    a.baseAssociation().ifPresent(id -> requireKind(id, SymbolKind.ASSOCIATION));
                    a.derivedFrom().ifPresent(id -> requireKind(id, SymbolKind.VIEW_BASE));
                    a.roles().forEach(id -> requireKind(id, SymbolKind.ROLE));
                    a.attributes().forEach(id -> requireKind(id, SymbolKind.ATTRIBUTE));
                }
                case GraphicSymbol graphic -> {
                    graphic.baseGraphic().ifPresent(id->requireKind(id,SymbolKind.GRAPHIC)); graphic.basis().ifPresent(this::requireSymbol);
                    graphic.rules().forEach(id->requireKind(id,SymbolKind.DRAWING_RULE)); graphic.effectiveRules().forEach(id->requireKind(id,SymbolKind.DRAWING_RULE));
                    graphic.selections().forEach(this::requireExpression);
                }
                case DrawingRuleSymbol rule -> {
                    rule.signatureClass().ifPresent(id->requireKind(id,SymbolKind.CLASS)); rule.overrides().ifPresent(id->requireKind(id,SymbolKind.DRAWING_RULE));
                    for(var clause:rule.clauses()) { clause.condition().ifPresent(this::requireExpression); for(var assignment:clause.assignments()) {
                        assignment.parameter().ifPresent(id->requireKind(id,SymbolKind.PARAMETER)); validateDrawingValue(assignment.value());
                    } }
                }
                case ModelSymbol m -> {
                    m.members().forEach(this::requireSymbol);
                    m.translation().ifPresent(t -> {
                        t.sourceTarget().ifPresent(id -> requireKind(id, SymbolKind.MODEL));
                        t.correspondences().forEach(link -> { requireSymbol(link.original()); requireSymbol(link.translated()); });
                    });
                }
                case ParameterSymbol p -> { requireType(p.typeId()); p.overrides().ifPresent(id -> requireKind(id, SymbolKind.PARAMETER)); }
                case ContextSymbol c -> c.mappings().forEach(m -> {
                    requireKind(m.genericDomain(), SymbolKind.DOMAIN);
                    m.alternatives().forEach(id -> requireKind(id, SymbolKind.DOMAIN));
                });
                case AttributeSymbol a -> { requireType(a.declaredType()); a.derivations().forEach(this::requireExpression); }
                case ViewSymbol.Base b -> requireSymbol(b.target());
                case ViewSymbol v -> {
                    v.baseView().ifPresent(this::requireSymbol); v.bases().forEach(this::requireSymbol);
                    v.baseExtensions().forEach(e -> { requireSymbol(e.base()); e.extensions().forEach(this::requireSymbol); });
                    v.members().forEach(this::requireSymbol); v.constraints().forEach(this::requireSymbol);
                    v.selections().forEach(this::requireExpression);
                    v.formation().ifPresent(f -> {
                        if (f instanceof ViewSymbol.Aggregation a) a.equal().forEach(this::requireExpression);
                        if (f instanceof ViewSymbol.Inspection i) {
                            requireExpression(i.path()); requireSymbol(i.element()); requireSymbol(i.parent());
                        }
                    });
                }
                default -> {
                }
            }
        }
        for (TypeDescriptor type : types.values()) {
            switch (type) {
                case GeometryType g -> {
                    g.lineForms().forEach(id -> requireKind(id, SymbolKind.LINE_FORM));
                    g.vertexDomain().ifPresent(id -> requireKind(id, SymbolKind.DOMAIN));
                    g.lineAttributes().ifPresent(id -> requireKind(id, SymbolKind.STRUCTURE));
                    g.overlap().filter(GeometryType.TrackWidth.class::isInstance).map(GeometryType.TrackWidth.class::cast)
                            .ifPresent(t -> requireKind(t.coordinateDomain(), SymbolKind.DOMAIN));
                }
                case DomainReferenceType d -> requireSymbol(d.targetDomain());
                case MetaObjectType m -> m.target().ifPresent(id -> requireKind(id, SymbolKind.CLASS));
                case ReferenceType r -> { r.target().ifPresent(this::requireSymbol); r.restrictions().forEach(this::requireSymbol); }
                case StructureValueType s -> { s.target().ifPresent(this::requireSymbol); s.restrictions().forEach(this::requireSymbol); }
                case ObjectArgumentType o -> {
                    o.base().ifPresent(this::requireSymbol);
                    o.restrictions().forEach(this::requireSymbol);
                }
                case ClassValueType c -> c.restrictions().forEach(this::requireSymbol);
                case AttributePathType p -> { p.owner().ifPresent(owner -> {
                    if (owner instanceof AttributePathType.ClassAttribute a) requireExpression(a.path());
                    if (owner instanceof AttributePathType.Argument a) {
                        requireKind(a.function(), SymbolKind.FUNCTION);
                        if (a.index() >= ((FunctionSymbol)symbols.get(a.function())).parameters().size()) throw new IllegalArgumentException("missing function argument");
                    }
                    if (owner instanceof AttributePathType.Viewable v) requireSymbol(v.target());
                }); p.restrictions().forEach(this::requireType); }
                case NumericType n -> validateRange(n.range());
                case CoordinateType c -> c.axes().forEach(this::validateRange);
                case CollectionType c -> requireType(c.elementType());
                case OidType oid -> oid.valueType().ifPresent(this::requireType);
                case EnumerationType e -> e.baseType().ifPresent(this::requireType);
                case FormattedType f -> {
                    f.baseDomain().ifPresent(this::requireSymbol); f.structure().ifPresent(this::requireSymbol);
                    f.parts().forEach(p -> { p.attribute().ifPresent(this::requireSymbol); p.format().ifPresent(this::requireSymbol); });
                }
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
            if(expression instanceof FunctionCallExpression call) {
                call.targetFunction().ifPresent(id->requireKind(id,SymbolKind.FUNCTION));
                for(var path:call.argumentPaths().values()) {
                    requireType(path.expectedType());
                    path.binding().ifPresent(id -> { if(!(expressions.get(id) instanceof PathExpression))throw new IllegalArgumentException("function argument path is not a bound path: "+id); });
                }
            }
            if (expression instanceof SymbolValueExpression value) requireSymbol(value.target());
            if (expression instanceof InspectionExpression inspected) {
                inspected.view().ifPresent(id -> requireKind(id, SymbolKind.VIEW));
                requireSymbol(inspected.source()); requireSymbol(inspected.element());
            }
            if (expression instanceof PathExpression path) {
                for (PathStep step : path.path().steps()) {
                    if (step instanceof PathStep.ViewBaseStep base) {
                        requireKind(base.base(), SymbolKind.VIEW_BASE); requireSymbol(base.target());
                    } else if (step instanceof PathStep.InspectionStep inspected) {
                        requireKind(inspected.view(), SymbolKind.VIEW); requireSymbol(inspected.target());
                    } else if (step instanceof PathStep.IndexStep selection) {
                        requireType(selection.resultType());
                    } else if (step instanceof PathStep.AssociationAccessStep access) {
                        requireKind(access.association(), SymbolKind.ASSOCIATION);
                        requireKind(access.sourceRole(), SymbolKind.ROLE);
                        requireKind(access.targetRole(), SymbolKind.ROLE);
                        requireType(access.resultType());
                    } else if (step instanceof PathStep.AssociationSourceStep access) {
                        requireKind(access.association(), SymbolKind.ASSOCIATION);
                        requireKind(access.sourceRole(), SymbolKind.ROLE);
                        requireType(access.resultType());
                    }
                }
            }
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

    private void requireExpression(ExpressionId id) {
        if (!expressions.containsKey(id)) throw new IllegalArgumentException("missing expression " + id);
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
        Set<LanguageVersion> selectedLanguages = allowedModels.stream().map(symbols::get).filter(Objects::nonNull)
                .map(Symbol::languageVersion).collect(java.util.stream.Collectors.toSet());
        Set<SymbolId> selected = new LinkedHashSet<>();
        for (Symbol symbol : symbols.values()) {
            SymbolId current = symbol.id();
            int guard = 0;
            while (current != null && guard++ < 64) {
                Symbol ancestor = symbols.get(current);
                if (allowedModels.contains(current) || ancestor != null && ancestor.owner().isEmpty()
                        && ancestor.origin() instanceof Origin.PredefinedOrigin && selectedLanguages.contains(ancestor.languageVersion())) {
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
                collectType(declared, selectedTypes, selectedExpressions);
            }
            if (symbol instanceof FunctionSymbol function) {
                function.parameters().forEach(p -> collectType(p.typeId(), selectedTypes, selectedExpressions));
                collectType(function.returnType(), selectedTypes, selectedExpressions);
            }
            if (symbol instanceof AttributeSymbol attribute) selectedExpressions.addAll(attribute.derivations());
            if (symbol instanceof RoleSymbol role) role.derivation().ifPresent(selectedExpressions::add);
            if(symbol instanceof GraphicSymbol graphic) selectedExpressions.addAll(graphic.selections());
            if(symbol instanceof DrawingRuleSymbol rule) for(var clause:rule.clauses()) {
                clause.condition().ifPresent(selectedExpressions::add);
                for(var assignment:clause.assignments()) collectDrawingExpressions(assignment.value(),selectedExpressions);
            }
            if (symbol instanceof ViewSymbol view) {
                selectedExpressions.addAll(view.selections());
                view.formation().ifPresent(f -> {
                    if (f instanceof ViewSymbol.Aggregation a) selectedExpressions.addAll(a.equal());
                    if (f instanceof ViewSymbol.Inspection i) selectedExpressions.add(i.path());
                });
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

    private void collectType(TypeId id, Set<TypeId> target, Set<ExpressionId> targetExpressions) {
        if (!target.add(id)) {
            return;
        }
        TypeDescriptor type = types.get(id);
        if (type == null) {
            return;
        }
        switch (type) {
            case AttributePathType path -> {
                path.restrictions().forEach(t -> collectType(t, target, targetExpressions));
                path.owner().filter(AttributePathType.ClassAttribute.class::isInstance).map(AttributePathType.ClassAttribute.class::cast).ifPresent(owner -> {
                    targetExpressions.add(owner.path());
                    collectExpressionTypes(owner.path(), target, targetExpressions, new LinkedHashSet<>());
                });
            }
            case CollectionType collection -> collectType(collection.elementType(), target, targetExpressions);
            case OidType oid -> oid.valueType().ifPresent(t -> collectType(t, target, targetExpressions));
            case EnumerationType e -> e.baseType().ifPresent(t -> collectType(t, target, targetExpressions));
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
            case UniquenessConstraint uniqueness -> {
                uniqueness.where().ifPresent(target::add);
                target.addAll(uniqueness.keys());
                for (var local : uniqueness.localKeys()) { target.add(local.prefix()); target.addAll(local.fields()); }
            }
            case DomainConstraint domain -> target.add(domain.condition());
            case SetConstraint set -> {
                set.where().ifPresent(target::add);
                target.add(set.condition());
            }
            case ExistenceConstraint existence -> {
                target.add(existence.source());
                existence.targets().forEach(t -> target.add(t.path()));
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
        collectType(expression.typeId(), targetTypes, targetExpressions);
        if (expression instanceof PathExpression path) {
            for (PathStep step : path.path().steps()) {
                if (step instanceof PathStep.IndexStep selection) collectType(selection.resultType(), targetTypes, targetExpressions);
                if (step instanceof PathStep.AssociationAccessStep access) collectType(access.resultType(), targetTypes, targetExpressions);
                if (step instanceof PathStep.AssociationSourceStep access) collectType(access.resultType(), targetTypes, targetExpressions);
            }
        }
        if (expression instanceof FunctionCallExpression call) for (var path : call.argumentPaths().values()) {
            collectType(path.expectedType(), targetTypes, targetExpressions);
            path.binding().ifPresent(binding -> {
                targetExpressions.add(binding);
                collectExpressionTypes(binding, targetTypes, targetExpressions, visited);
            });
        }
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
