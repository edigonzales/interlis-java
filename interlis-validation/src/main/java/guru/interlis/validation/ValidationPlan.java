package guru.interlis.validation;

import guru.interlis.model.DomainSymbol;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable and shareable rules for one checked schema revision. */
public final class ValidationPlan {
    private final TransferPlan transferPlan;
    private final SemanticGraph graph;
    private final TypeRelations typeRelations;
    private final Map<SymbolRef, ObjectValidationPlan> objects;
    private final Map<ExpressionId, Optional<LocalExpressions.Program>> expressions;
    private final List<UncheckedRuleInfo> uncheckedRules;
    private final ValidationProfile profile;
    private final List<DatasetRulePlan> datasetRules;
    private final List<SymbolRef> rootModels;

    ValidationPlan(TransferPlan transferPlan, SemanticGraph graph, Map<SymbolRef, ObjectValidationPlan> objects,
                   List<UncheckedRuleInfo> uncheckedRules, Map<ExpressionId, Optional<LocalExpressions.Program>> expressions,
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

    LocalExpressions.Program expression(ExpressionId root) {
        return expressions.getOrDefault(root, Optional.empty()).orElseThrow();
    }

    ObjectValidationPlan object(SymbolRef type) {
        return objects.get(type);
    }
}
