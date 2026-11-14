package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ConstraintDefinition;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.ExistenceConstraint;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.ParameterSymbol;
import guru.interlis.model.Origin;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SetConstraint;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.validation.PlanningProblem.Code.*;
import static guru.interlis.validation.UncheckedRuleInfo.Reason.*;

/** Projects model rules for the transfer plan's immutable schema revision. */
public final class ValidationPlanner {
    private ValidationPlanner() { }

    /** Returns a projection or a structured rejection. The transfer plan is non-null. */
    public static ValidationPlanningResult plan(TransferPlan transferPlan) {
        return plan(transferPlan, ValidationProfile.LOCAL_ONLY);
    }

    /** Projects the non-null inputs for the chosen execution profile. */
    public static ValidationPlanningResult plan(TransferPlan transferPlan,
                                                ValidationProfile profile) {
        return plan(transferPlan, profile, RuntimeBindings.empty());
    }

    /** Projects a plan with immutable host values and function implementations. */
    public static ValidationPlanningResult plan(TransferPlan transferPlan,
                                                ValidationProfile profile, RuntimeBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(profile, "profile");
        var schema = Objects.requireNonNull(transferPlan, "transferPlan").schema();
        var projection = new Projection(schema.semanticGraph(), transferPlan, profile, schema.rootModels(), bindings);
        try {
            return new ValidationPlanningResult.Ready(projection.build());
        } catch (MissingModelInformation missing) {
            return rejected(MISSING_MODEL_INFORMATION, Optional.of(missing.symbol), missing.getMessage());
        } catch (ValidationLimitException limit) {
            return rejected(EXPRESSION_LIMIT_EXCEEDED, Optional.empty(), limit.getMessage());
        }
    }

    private static ValidationPlanningResult rejected(PlanningProblem.Code code, Optional<SymbolRef> symbol,
                                                     String message) {
        return new ValidationPlanningResult.Rejected(List.of(new PlanningProblem(code, symbol, message)));
    }

    private static final class Projection {
        private final SemanticGraph graph;
        private final TransferPlan transfer;
        private final ValidationProfile profile;
        private final RuntimeBindings bindings;
        private final List<SymbolRef> rootModels;
        private final Map<ConstraintSymbol, List<SymbolRef>> datasetCandidates = new LinkedHashMap<>();
        private final TypeRelations types;
        private final Map<ExpressionId, Optional<ExpressionProgram>> expressions = new LinkedHashMap<>();
        private final Map<SymbolId, List<IndexedConstraint>> declarations = new LinkedHashMap<>();
        // Structures may occur in baskets of several topics. Project the union
        // of alternatives once; a session selects rules for its actual binding.
        private final Map<SymbolId, Set<SymbolId>> genericAlternatives = new LinkedHashMap<>();
        private final Map<TypeId, Set<List<String>>> enumValues = new LinkedHashMap<>();
        private final List<UncheckedRuleInfo> unchecked = new ArrayList<>();

        Projection(SemanticGraph graph, TransferPlan transfer, ValidationProfile profile, List<SymbolRef> rootModels, RuntimeBindings bindings) {
            this.graph = graph;
            this.transfer = transfer;
            this.profile = profile;
            this.bindings = bindings;
            this.rootModels = rootModels;
            this.types = new TypeRelations(graph);
            for (var topic : transfer.topics()) {
                for (var binding : topic.genericBindings()) {
                    genericAlternatives.computeIfAbsent(binding.genericDomain(), ignored -> new LinkedHashSet<>())
                            .addAll(binding.alternatives());
                }
            }
            int order = 0;
            for (var symbol : graph.declarationsInOrder()) {
                if (symbol instanceof ConstraintSymbol constraint) {
                    var owner = constraint.target().or(constraint.header()::owner);
                    if (owner.isPresent()) {
                        declarations.computeIfAbsent(owner.orElseThrow(), ignored -> new ArrayList<>())
                                .add(new IndexedConstraint(order++, constraint));
                    }
                }
            }
        }

        ValidationPlan build() {
            Map<SymbolRef, ObjectValidationPlan> objects = new LinkedHashMap<>();
            for (var object : transfer.objects()) {
                if (!object.transferable() && object.kind() != SymbolKind.STRUCTURE) {
                    if (profile == ValidationProfile.DATASET && object.topic().isPresent()
                            && (object.kind() == SymbolKind.CLASS || object.kind() == SymbolKind.ASSOCIATION)) {
                        for (var indexed : declarations.getOrDefault(object.symbol().symbol(), List.of())) {
                            var declaration = indexed.constraint();
                            if (datasetRule(declaration.definition())) {
                                datasetCandidates.computeIfAbsent(declaration, ignored -> new ArrayList<>());
                            }
                        }
                    }
                    continue;
                }
                List<ObjectValidationPlan.FieldRule> fields = new ArrayList<>();
                for (var slot : object.slots()) {
                    if (slot instanceof AttributeSlot attribute && !attribute.attribute().transientAttribute()) {
                        fields.add(field(object, attribute));
                    }
                }
                var constraints = rules(object, Optional.empty(), 0, ancestry(object));
                Map<SymbolId, ObjectValidationPlan.FieldRule> pathFields = new LinkedHashMap<>();
                for (var field : fields) {
                    for (var attribute : declarations(field.slot())) {
                        pathFields.put(attribute.id(), field);
                    }
                }
                objects.put(object.symbol(), new ObjectValidationPlan(object, fields, constraints, pathFields));
            }
            var datasetRules = datasetCandidates.entrySet().stream()
                    .map(entry -> {
                        var declaration = entry.getKey();
                        var application = declaration.target().or(declaration.header()::owner).orElseThrow();
                        projectDatasetExpressions(declaration.definition(), application);
                        var key = new RuleKey(new SymbolRef(transfer.snapshotId(), declaration.id()),
                                new SymbolRef(transfer.snapshotId(), application), Optional.empty());
                        return new DatasetRulePlan(key, declaration, entry.getValue());
                    }).toList();
            for (var declaration : graph.symbols()) {
                if (declaration instanceof AttributeSymbol attribute) {
                    attribute.derivations().forEach(this::datasetExpression);
                }
            }
            var views = profile == ValidationProfile.DATASET ? ViewPlanner.compile(graph) : Map.<SymbolId, ViewPlan>of();
            if (profile == ValidationProfile.DATASET) {
                for (var view : views.values()) {
                    if (view.formation() instanceof guru.interlis.model.ViewSymbol.Aggregation aggregation) {
                        aggregation.equal().forEach(this::datasetExpression);
                    }
                    view.inheritance().forEach(ancestor -> ancestor.selections().forEach(this::datasetExpression));
                    for (var constraint : view.constraints()) {
                        switch (constraint.definition()) {
                            case MandatoryConstraint mandatory -> datasetExpression(mandatory.condition());
                            case UniquenessConstraint unique -> {
                                unique.keys().forEach(this::datasetExpression);
                                unique.where().ifPresent(this::datasetExpression);
                            }
                            default -> { }
                        }
                    }
                }
            }
            var plan = new ValidationPlan(transfer, graph, objects, unchecked, expressions, profile, datasetRules, rootModels, bindings, views);
            validateBindings(plan);
            return plan;
        }

        private ObjectValidationPlan.FieldRule field(ObjectDescriptor object, AttributeSlot slot) {
            List<ObjectValidationPlan.ValueRules> values = new ArrayList<>();
            TypeId valueType = slot.declaredType().type();
            for (int depth = 0; ; depth++) {
                Optional<Symbol> required = Optional.empty();
                Set<SymbolId> domains = new HashSet<>();
                Optional<SymbolId> genericDomain = Optional.empty();
                var type = type(valueType, slot.symbol());
                while (type instanceof DomainReferenceType reference) {
                    var domain = domain(reference.targetDomain(), slot.symbol());
                    if (domain.generic()) {
                        genericDomain = Optional.of(domain.id());
                    }
                    type = type(domain.typeId(), slot.symbol());
                    for (;;) {
                        if (!domains.add(domain.id())) {
                            break;
                        }
                        if (required.isEmpty() && domain.mandatory()) {
                            required = Optional.of(domain);
                        }
                        if (domain.baseDomain().isEmpty()) {
                            break;
                        }
                        domain = domain(domain.baseDomain().orElseThrow(), slot.symbol());
                    }
                }
                if (required.isEmpty() && depth == 0 && slot.attribute().mandatory()) {
                    required = Optional.of(slot.attribute());
                }
                var concreteDomains = genericDomain.map(id -> concreteDomainOwners(id, domains, slot.symbol()))
                        .orElseGet(Map::of);
                var allDomains = new HashSet<>(domains);
                concreteDomains.values().forEach(allDomains::addAll);
                int firstUnchecked = unchecked.size();
                var compiled = rules(object, Optional.of(slot.symbol()), depth, allDomains);
                Map<SymbolId, List<ConstraintSymbol>> concreteConstraints = new LinkedHashMap<>();
                Map<SymbolId, List<ConstraintSymbol>> concreteUnsupported = new LinkedHashMap<>();
                if (!concreteDomains.isEmpty()) {
                    var unsupported = unchecked.subList(firstUnchecked, unchecked.size()).stream()
                            .map(rule -> (ConstraintSymbol) graph.symbol(rule.constraint().symbol()).orElseThrow()).toList();
                    concreteDomains.forEach((id, owners) -> {
                        concreteConstraints.put(id, forOwners(compiled, owners));
                        concreteUnsupported.put(id, forOwners(unsupported, owners));
                    });
                }
                values.add(new ObjectValidationPlan.ValueRules(depth, type, genericDomain, required,
                        forOwners(compiled, domains), concreteConstraints, concreteUnsupported,
                        type instanceof EnumerationType enumeration
                                ? enumValues.computeIfAbsent(type.id(), id -> ScalarRules.enumerationValues(enumeration))
                                : Set.of()));
                if (!(type instanceof CollectionType collection)) {
                    return new ObjectValidationPlan.FieldRule(slot, hasDerivation(slot), charset(slot.attribute()), values);
                }
                valueType = collection.elementType();
            }
        }

        private Map<SymbolId, Set<SymbolId>> concreteDomainOwners(SymbolId generic, Set<SymbolId> declared,
                                                                 SymbolRef slot) {
            Map<SymbolId, Set<SymbolId>> alternatives = new LinkedHashMap<>();
            for (var concrete : genericAlternatives.getOrDefault(generic, Set.of())) {
                var owners = new HashSet<>(declared);
                var current = domain(concrete, slot);
                while (owners.add(current.id()) && current.baseDomain().isPresent()) {
                    current = domain(current.baseDomain().orElseThrow(), slot);
                }
                alternatives.put(concrete, owners);
            }
            return alternatives;
        }

        private static List<ConstraintSymbol> forOwners(List<ConstraintSymbol> rules, Set<SymbolId> owners) {
            return rules.stream().filter(rule -> rule.header().owner().filter(owners::contains).isPresent()).toList();
        }

        private Optional<String> charset(Symbol symbol) {
            while (!(symbol instanceof ModelSymbol) && symbol.header().owner().isPresent()) {
                var owner = graph.symbol(symbol.header().owner().orElseThrow());
                if (owner.isEmpty()) {
                    return Optional.empty();
                }
                symbol = owner.orElseThrow();
            }
            if (symbol instanceof ModelSymbol model) {
                return model.options().characterSet().map(option -> option.value());
            }
            return Optional.empty();
        }

        private boolean hasDerivation(AttributeSlot slot) {
            return declarations(slot).stream().anyMatch(attribute -> !attribute.derivations().isEmpty());
        }

        private List<AttributeSymbol> declarations(AttributeSlot slot) {
            var chain = new ArrayList<AttributeSymbol>();
            var attribute = slot.attribute();
            for (;;) {
                chain.add(attribute);
                if (attribute.overrides().isEmpty()) {
                    return chain;
                }
                var base = attribute.overrides().orElseThrow();
                if (!(graph.symbol(base).orElse(null) instanceof AttributeSymbol inherited)) {
                    throw missing(slot.symbol(), "Missing inherited attribute " + base);
                }
                attribute = inherited;
            }
        }

        private TypeDescriptor type(TypeId id, SymbolRef symbol) {
            return graph.type(id).orElseThrow(() -> missing(symbol, "Missing type " + id));
        }

        private DomainSymbol domain(SymbolId id, SymbolRef symbol) {
            if (graph.symbol(id).orElse(null) instanceof DomainSymbol domain) {
                return domain;
            }
            throw missing(symbol, "Missing domain " + id);
        }

        private Set<SymbolId> ancestry(ObjectDescriptor object) {
            var ancestry = new HashSet<SymbolId>();
            types.objectLineage(object.symbol()).forEach(ref -> ancestry.add(ref.symbol()));
            return ancestry;
        }

        private List<ConstraintSymbol> rules(ObjectDescriptor object, Optional<SymbolRef> slot, int depth,
                                             Set<SymbolId> owners) {
            if (owners.isEmpty()) {
                return List.of();
            }
            List<ConstraintSymbol> local = new ArrayList<>();
            var ordered = owners.stream()
                    .flatMap(owner -> declarations.getOrDefault(owner, List.of()).stream())
                    .sorted(Comparator.comparingInt(IndexedConstraint::order))
                    .map(IndexedConstraint::constraint).toList();
            for (var declaration : ordered) {
                var constraint = new SymbolRef(transfer.snapshotId(), declaration.header().id());
                var reason = unsupported(declaration.definition(), constraint);
                if (profile == ValidationProfile.DATASET && datasetRule(declaration.definition())) {
                    datasetCandidates.computeIfAbsent(declaration, ignored -> new ArrayList<>()).add(object.symbol());
                } else if (reason.isPresent()) {
                    unchecked.add(new UncheckedRuleInfo(constraint, object.symbol(), slot, depth, reason.orElseThrow()));
                } else {
                    local.add(declaration);
                }
            }
            return local;
        }

        private boolean hasHostFunction(SymbolId id) {
            var canonical = id;
            for (var model : transfer.models()) {
                var symbol = (ModelSymbol) graph.symbol(model.symbol().symbol()).orElseThrow();
                var original = symbol.translation().stream().flatMap(translation -> translation.correspondences().stream())
                        .filter(link -> link.translated().equals(id)).findFirst();
                if (original.isPresent()) {
                    canonical = original.orElseThrow().original();
                    break;
                }
            }
            return bindings.functions().containsKey(new SymbolRef(graph.snapshotId(), canonical));
        }

        private void validateBindings(ValidationPlan plan) {
            for (var entry : bindings.parameters().entrySet()) {
                var key = entry.getKey();
                if (!key.snapshot().equals(plan.snapshotId()) || !plan.canonical(key.symbol()).equals(key.symbol())
                        || !(plan.symbol(key.symbol()).orElse(null) instanceof ParameterSymbol parameter)
                        || !(parameter.owner().flatMap(plan::symbol).orElse(null) instanceof ModelSymbol)
                        || !RuntimeTypeChecks.accepts(plan, parameter.typeId(), parameter.mandatory(), entry.getValue(), () -> { })) {
                    throw missing(key, "Invalid host parameter binding");
                }
            }
            for (var entry : bindings.functions().entrySet()) {
                var key = entry.getKey();
                var signature = entry.getValue().signature();
                if (!key.snapshot().equals(plan.snapshotId()) || !plan.canonical(key.symbol()).equals(key.symbol())
                        || signature.origin() instanceof Origin.PredefinedOrigin
                        || !plan.symbol(key.symbol()).filter(FunctionSymbol.class::isInstance)
                                .filter(signature::equals).isPresent()) {
                    throw missing(key, "Invalid host function binding");
                }
            }
        }

        private void projectDatasetExpressions(ConstraintDefinition definition, SymbolId application) {
            switch (definition) {
                case UniquenessConstraint unique -> {
                    unique.where().ifPresent(this::datasetExpression);
                    unique.keys().forEach(this::datasetExpression);
                }
                case ExistenceConstraint existence -> {
                    datasetExpression(existence.source());
                    existence.targets().forEach(target -> datasetExpression(target.path()));
                }
                case SetConstraint set -> {
                    set.where().ifPresent(this::datasetExpression);
                    expressions.computeIfAbsent(set.condition(), id -> ExpressionCompiler.compileSet(graph, types, id, application, this::hasHostFunction));
                }
                case PlausibilityConstraint plausibility -> datasetExpression(plausibility.condition());
                default -> { }
            }
        }

        private void datasetExpression(ExpressionId root) {
            expressions.computeIfAbsent(root, id -> ExpressionCompiler.compile(graph, types, id, this::hasHostFunction));
        }

        private static boolean datasetRule(ConstraintDefinition definition) {
            return switch (definition) {
                case ExistenceConstraint _, SetConstraint _, PlausibilityConstraint _ -> true;
                case UniquenessConstraint rule -> rule.localKeys().isEmpty();
                default -> false;
            };
        }

        private Optional<UncheckedRuleInfo.Reason> unsupported(ConstraintDefinition definition, SymbolRef constraint) {
            List<ExpressionId> expressions = new ArrayList<>();
            switch (definition) {
                case MandatoryConstraint rule -> expressions.add(rule.condition());
                case DomainConstraint rule -> expressions.add(rule.condition());
                case UniquenessConstraint rule when !rule.localKeys().isEmpty() -> {
                    if (rule.basket()) {
                        return Optional.of(OUT_OF_SCOPE_GLOBAL);
                    }
                    if (rule.where().isPresent()) {
                        return Optional.of(UNSUPPORTED_EXPRESSION);
                    }
                    for (var key : rule.localKeys()) {
                        expressions.add(key.prefix());
                        expressions.addAll(key.fields());
                    }
                }
                case ExistenceConstraint _, SetConstraint _, PlausibilityConstraint _, UniquenessConstraint _ -> {
                    return Optional.of(OUT_OF_SCOPE_GLOBAL);
                }
            }
            for (var id : expressions) {
                Optional<ExpressionProgram> program;
                try {
                    program = this.expressions.computeIfAbsent(id, root -> ExpressionCompiler.compile(graph, types, root, this::hasHostFunction));
                } catch (IllegalArgumentException invalid) {
                    throw missing(constraint, invalid.getMessage());
                }
                if (program.isEmpty()) {
                    return Optional.of(UNSUPPORTED_EXPRESSION);
                }
            }
            if (definition instanceof UniquenessConstraint unique
                    && !LocalUnique.supports(unique, graph, types, id -> this.expressions.get(id).orElseThrow())) {
                return Optional.of(UNSUPPORTED_EXPRESSION);
            }
            return Optional.empty();
        }
    }

    private record IndexedConstraint(int order, ConstraintSymbol constraint) { }

    private static MissingModelInformation missing(SymbolRef symbol, String message) {
        return new MissingModelInformation(symbol, message);
    }

    private static final class MissingModelInformation extends RuntimeException {
        private final SymbolRef symbol;

        MissingModelInformation(SymbolRef symbol, String message) {
            super(message);
            this.symbol = symbol;
        }
    }
}
