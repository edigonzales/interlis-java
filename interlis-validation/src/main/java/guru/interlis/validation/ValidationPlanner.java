package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.BinaryExpression;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.ConstantExpression;
import guru.interlis.model.ConstraintDefinition;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.DefinedExpression;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.ErrorExpression;
import guru.interlis.model.ExistenceConstraint;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.FunctionCallExpression;
import guru.interlis.model.InspectionExpression;
import guru.interlis.model.LiteralExpression;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.ObjectSetExpression;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathStep;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SetConstraint;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.SymbolValueExpression;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.UnaryExpression;
import guru.interlis.model.UnaryOperator;
import guru.interlis.model.UndefinedExpression;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** Returns a projection or a structured rejection. Both arguments are non-null. */
    public static ValidationPlanningResult plan(CompiledSchema schema, TransferPlan transferPlan) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(transferPlan, "transferPlan");
        if (!schema.snapshotId().equals(transferPlan.snapshotId())) {
            return rejected(SNAPSHOT_MISMATCH, Optional.empty(), "Transfer plan belongs to another snapshot");
        }
        if (!schema.fingerprint().equals(transferPlan.schemaFingerprint())) {
            return rejected(SCHEMA_FINGERPRINT_MISMATCH, Optional.empty(), "Transfer plan has a different schema fingerprint");
        }
        var projection = new Projection(schema.semanticGraph(), transferPlan);
        try {
            return new ValidationPlanningResult.Ready(projection.build());
        } catch (MissingModelInformation missing) {
            return rejected(MISSING_MODEL_INFORMATION, Optional.of(missing.symbol), missing.getMessage());
        }
    }

    private static ValidationPlanningResult rejected(PlanningProblem.Code code, Optional<SymbolRef> symbol,
                                                     String message) {
        return new ValidationPlanningResult.Rejected(List.of(new PlanningProblem(code, symbol, message)));
    }

    private static final class Projection {
        private final SemanticGraph graph;
        private final TransferPlan transfer;
        private final Map<SymbolId, List<IndexedConstraint>> declarations = new LinkedHashMap<>();
        private final Map<TypeId, Set<List<String>>> enumValues = new LinkedHashMap<>();
        private final List<UncheckedRuleInfo> unchecked = new ArrayList<>();

        Projection(SemanticGraph graph, TransferPlan transfer) {
            this.graph = graph;
            this.transfer = transfer;
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
                    continue;
                }
                List<ObjectValidationPlan.FieldRule> fields = new ArrayList<>();
                for (var slot : object.slots()) {
                    if (slot instanceof AttributeSlot attribute && !attribute.attribute().transientAttribute()) {
                        fields.add(field(object, attribute));
                    }
                }
                var constraints = rules(object, Optional.empty(), 0, ancestry(object));
                objects.put(object.symbol(), new ObjectValidationPlan(object, fields, constraints));
            }
            return new ValidationPlan(transfer, objects, unchecked);
        }

        private ObjectValidationPlan.FieldRule field(ObjectDescriptor object, AttributeSlot slot) {
            List<ObjectValidationPlan.ValueRules> values = new ArrayList<>();
            TypeId valueType = slot.declaredType().type();
            for (int depth = 0; ; depth++) {
                Optional<Symbol> required = Optional.empty();
                Set<SymbolId> domains = new HashSet<>();
                var type = type(valueType, slot.symbol());
                while (type instanceof DomainReferenceType reference) {
                    var domain = domain(reference.targetDomain(), slot.symbol());
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
                values.add(new ObjectValidationPlan.ValueRules(depth, type, required,
                        rules(object, Optional.of(slot.symbol()), depth, domains),
                        type instanceof EnumerationType enumeration
                                ? enumValues.computeIfAbsent(type.id(), id -> ScalarRules.enumerationValues(enumeration))
                                : Set.of()));
                if (!(type instanceof CollectionType collection)) {
                    return new ObjectValidationPlan.FieldRule(slot, hasDerivation(slot), charset(slot.attribute()), values);
                }
                valueType = collection.elementType();
            }
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
            var attribute = slot.attribute();
            while (attribute.derivations().isEmpty() && attribute.overrides().isPresent()) {
                var base = attribute.overrides().orElseThrow();
                if (!(graph.symbol(base).orElse(null) instanceof AttributeSymbol inherited)) {
                    throw missing(slot.symbol(), "Missing inherited attribute " + base);
                }
                attribute = inherited;
            }
            return !attribute.derivations().isEmpty();
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
            Set<SymbolId> types = new HashSet<>();
            var current = object;
            while (types.add(current.symbol().symbol()) && current.baseType().isPresent()) {
                var base = current.baseType().orElseThrow();
                current = transfer.object(base).orElseThrow(() -> missing(base, "Missing base object"));
            }
            return types;
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
                if (reason.isPresent()) {
                    unchecked.add(new UncheckedRuleInfo(constraint, object.symbol(), slot, depth, reason.orElseThrow()));
                } else {
                    local.add(declaration);
                }
            }
            return local;
        }

        private Optional<UncheckedRuleInfo.Reason> unsupported(ConstraintDefinition definition, SymbolRef constraint) {
            List<ExpressionId> expressions = new ArrayList<>();
            switch (definition) {
                case MandatoryConstraint rule -> expressions.add(rule.condition());
                case DomainConstraint rule -> expressions.add(rule.condition());
                case UniquenessConstraint rule when !rule.localKeys().isEmpty() -> {
                    rule.where().ifPresent(expressions::add);
                    for (var key : rule.localKeys()) {
                        expressions.add(key.prefix());
                        expressions.addAll(key.fields());
                    }
                }
                case ExistenceConstraint _, SetConstraint _, PlausibilityConstraint _, UniquenessConstraint _ -> {
                    return Optional.of(OUT_OF_SCOPE_GLOBAL);
                }
            }
            var pending = new ArrayDeque<>(expressions);
            Set<ExpressionId> visited = new HashSet<>();
            while (!pending.isEmpty()) {
                var id = pending.removeFirst();
                if (!visited.add(id)) {
                    continue;
                }
                var expression = graph.expression(id).orElseThrow(() -> missing(constraint, "Missing expression " + id));
                if (!supported(expression)) {
                    return Optional.of(UNSUPPORTED_EXPRESSION);
                }
                pending.addAll(expression.children());
            }
            return Optional.empty();
        }

        private boolean supported(TypedExpression expression) {
            return switch (expression) {
                case LiteralExpression _, DefinedExpression _, UndefinedExpression _ -> true;
                case ConstantExpression constant -> constant.constantName().startsWith("#");
                case BinaryExpression binary -> switch (binary.operator()) {
                    case EQ, NEQ, LT, LTE, GT, GTE, AND, OR, IMPLIES -> true;
                    case PLUS, MINUS, MUL, DIV -> false;
                };
                case UnaryExpression unary -> unary.operator() == UnaryOperator.NOT;
                case PathExpression path -> path.path().steps().stream().allMatch(step ->
                        step instanceof PathStep.ThisStep || step instanceof PathStep.AttributeStep
                                || step instanceof PathStep.IndexStep);
                case ErrorExpression _, FunctionCallExpression _, SymbolValueExpression _,
                     ObjectSetExpression _, InspectionExpression _ -> false;
            };
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
