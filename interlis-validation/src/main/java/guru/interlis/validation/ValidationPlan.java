package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SchemaFingerprint;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.Modifier;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypeRef;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferPlan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable and shareable rules for one checked schema revision. */
public final class ValidationPlan {
    private final TransferPlan transferPlan;
    private final SemanticGraph graph;
    private final TypeRelations typeRelations;
    private final Map<SymbolRef, ObjectValidationPlan> objects;
    private final Map<ExpressionId, Optional<ExpressionProgram>> expressions;
    private final List<UncheckedRuleInfo> uncheckedRules;
    private final ValidationProfile profile;
    private final List<DatasetRulePlan> datasetRules;
    private final List<SymbolRef> rootModels;
    private final Map<SymbolId, SymbolId> originals;
    private final Map<TypeId, TypeId> originalEnumerations;

    ValidationPlan(TransferPlan transferPlan, SemanticGraph graph, Map<SymbolRef, ObjectValidationPlan> objects,
                   List<UncheckedRuleInfo> uncheckedRules, Map<ExpressionId, Optional<ExpressionProgram>> expressions,
                   ValidationProfile profile, List<DatasetRulePlan> datasetRules, List<SymbolRef> rootModels) {
        this.transferPlan = transferPlan;
        this.graph = graph;
        this.typeRelations = new TypeRelations(graph);
        this.objects = Map.copyOf(objects);
        this.uncheckedRules = List.copyOf(uncheckedRules);
        this.expressions = Map.copyOf(expressions);
        this.profile = profile;
        this.datasetRules = List.copyOf(datasetRules);
        this.rootModels = List.copyOf(rootModels);
        var translatedSymbols = new HashMap<SymbolId, SymbolId>();
        for (var ref : transferPlan.models()) {
            var model = (ModelSymbol) graph.symbol(ref.symbol().symbol()).orElseThrow();
            model.translation().ifPresent(translation -> translation.correspondences().forEach(link ->
                    translatedSymbols.put(link.translated(), link.original())));
        }
        this.originals = Map.copyOf(translatedSymbols);
        var translatedTypes = new HashMap<TypeId, TypeId>();
        for (var entry : originals.entrySet()) {
            var translated = enumType(entry.getKey());
            var original = enumType(entry.getValue());
            if (translated.isPresent() && original.isPresent()
                    && !translated.orElseThrow().id().equals(original.orElseThrow().id())) {
                translatedTypes.put(translated.orElseThrow().id(), original.orElseThrow().id());
            }
        }
        this.originalEnumerations = Map.copyOf(translatedTypes);
    }

    /** Identifies the snapshot that owns all plan references. */
    public SnapshotId snapshotId() {
        return transferPlan.snapshotId();
    }

    /** Identifies the checked schema used to build the plan. */
    public SchemaFingerprint schemaFingerprint() {
        return transferPlan.schemaFingerprint();
    }

    /** Immutable list in object, effective slot and constraint declaration order. */
    public List<UncheckedRuleInfo> uncheckedRules() {
        return uncheckedRules;
    }

    /** Returns the execution profile of this immutable plan. */
    public ValidationProfile profile() {
        return profile;
    }

    /** Dataset declarations pending evaluation, in plan order. Local plans return an empty list. */
    public List<RuleKey> deferredRules() {
        return datasetRules.stream().map(DatasetRulePlan::key).toList();
    }

    List<DatasetRulePlan> datasetRules() {
        return datasetRules;
    }

    List<SymbolRef> rootModels() {
        return rootModels;
    }

    SymbolRef ref(SymbolId id) {
        return new SymbolRef(snapshotId(), id);
    }

    SymbolId canonical(SymbolId id) {
        while (originals.containsKey(id) && !originals.get(id).equals(id)) {
            id = originals.get(id);
        }
        return id;
    }

    EnumerationType canonicalEnumeration(EnumerationType type) {
        var current = type;
        while (originalEnumerations.containsKey(current.id())) {
            current = (EnumerationType) effectiveType(originalEnumerations.get(current.id())).orElseThrow();
        }
        return current;
    }

    private Optional<EnumerationType> enumType(SymbolId symbol) {
        return graph.symbol(symbol).flatMap(value -> switch (value) {
            case DomainSymbol domain -> effectiveType(domain.typeId());
            case AttributeSymbol attribute -> effectiveType(attribute.declaredType());
            default -> Optional.empty();
        }).filter(EnumerationType.class::isInstance).map(EnumerationType.class::cast);
    }

    ObjectValidationPlan.FieldRule field(ObjectValidationPlan type, SymbolId declaration) {
        var field = type.field(declaration);
        if (field != null || originals.isEmpty()) {
            return field;
        }
        var canonical = canonical(declaration);
        return type.fields().stream().filter(candidate -> canonical(candidate.slot().symbol().symbol()).equals(canonical))
                .findFirst().orElse(null);
    }

    Optional<Symbol> symbol(SymbolId id) {
        return graph.symbol(id);
    }

    Optional<TypeDescriptor> domainType(SymbolId id) {
        return graph.symbol(id).filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast)
                .flatMap(domain -> effectiveType(domain.typeId()));
    }

    boolean assignable(StructureValueType type, ObjectValidationPlan child) {
        var descriptor = child.descriptor();
        if (descriptor.kind() != SymbolKind.STRUCTURE || descriptor.modifiers().contains(Modifier.ABSTRACT)) {
            return false;
        }
        return ModelValueRules.specializes(this, descriptor, type.targetRoots());
    }

    Optional<TypeDescriptor> effectiveType(TypeId id) {
        return typeRelations.resolveAliases(new TypeRef(snapshotId(), id)).known()
                .flatMap(resolved -> graph.type(resolved.effectiveType()));
    }

    List<SymbolId> symbols(QualifiedName name) {
        return graph.qualifiedNames().getOrDefault(name, List.of());
    }

    TypeRelations typeRelations() {
        return typeRelations;
    }

    TransferPlan transferPlan() {
        return transferPlan;
    }

    ExpressionProgram expression(ExpressionId root) {
        return datasetExpression(root).orElseThrow();
    }

    Optional<ExpressionProgram> datasetExpression(ExpressionId root) {
        return expressions.getOrDefault(root, Optional.empty());
    }

    ObjectValidationPlan object(SymbolRef type) {
        return objects.get(type);
    }
}
