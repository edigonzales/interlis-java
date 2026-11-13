package guru.interlis.validation;

import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.GeometryType;
import guru.interlis.model.Modifier;
import guru.interlis.model.OidRule;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.OidType;
import guru.interlis.model.Origin;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.validation.ValidationCoverage.ConstraintStatus;
import guru.interlis.model.TypeKind;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferValue;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.ValidationCoverage.RuleFamily.*;
import static guru.interlis.validation.ValidationReport.Result.FAILED_CHECKED_SCOPE;
import static guru.interlis.validation.ValidationReport.Result.PASSED_CHECKED_SCOPE;

/** One single-caller validation run. A session retains only the active basket and bounded report state. */
public final class ValidationSession implements AutoCloseable {
    private enum State { BEFORE_TRANSFER, BETWEEN_BASKETS, IN_BASKET, AFTER_END, FINISHED, FAILED, CLOSED }

    private final ValidationPlan plan;
    private final ValidationOptions options;
    private final List<ValidationDiagnostic> examples = new ArrayList<>();
    private final Map<SlotKey, Long> checked = new LinkedHashMap<>();
    private final Map<SlotKey, Long> incomplete = new LinkedHashMap<>();
    private final Map<SlotKey, Long> missingRuntime = new LinkedHashMap<>();
    private final Map<ConstraintKey, Long> constraints = new LinkedHashMap<>();
    private final Map<SlotKey, Long> unsupported = new LinkedHashMap<>();
    private State state = State.BEFORE_TRANSFER;
    private Optional<SourceId> source = Optional.empty();
    private BasketContext basket;
    private long baskets;
    private long objects;
    private long violations;

    ValidationSession(ValidationPlan plan, ValidationOptions options) {
        this.plan = plan;
        this.options = options;
    }

    /**
     * Validates one non-null event synchronously using the non-null cancellation
     * token. The caller supplies structurally decoded events for the plan's schema
     * revision. The session does not retain an object after the call returns.
     * Hosts that obtain events from a reader must confirm EOF before publishing
     * the report. Failure makes this session terminal; close remains available.
     *
     * @throws ValidationStreamException if the validation context or event order is invalid
     * @throws ValidationLimitException if a validation budget is exceeded
     * @throws guru.interlis.execution.OperationCancelledException on cancellation
     */
    public void accept(TransferEvent event, CancellationToken cancellation) {
        requireActive();
        try {
            Objects.requireNonNull(cancellation, "cancellation").throwIfCancellationRequested();
            switch (Objects.requireNonNull(event, "event")) {
                case TransferEvent.StartTransfer start -> {
                    require(State.BEFORE_TRANSFER);
                    source = start.source();
                    state = State.BETWEEN_BASKETS;
                }
                case TransferEvent.StartBasket start -> {
                    require(State.BETWEEN_BASKETS);
                    basket = BasketContext.of(start, plan);
                    checkBasketIdentity();
                    baskets = Math.incrementExact(baskets);
                    state = State.IN_BASKET;
                }
                case TransferEvent.ObjectEvent object -> {
                    require(State.IN_BASKET);
                    inspect(object, cancellation);
                    objects = Math.incrementExact(objects);
                }
                case TransferEvent.EndBasket end -> {
                    require(State.IN_BASKET);
                    if (!basket.start().id().equals(end.id())) {
                        throw new ValidationStreamException("EndBasket does not match the active basket");
                    }
                    basket = null;
                    state = State.BETWEEN_BASKETS;
                }
                case TransferEvent.EndTransfer ignored -> {
                    require(State.BETWEEN_BASKETS);
                    state = State.AFTER_END;
                }
            }
        } catch (RuntimeException failure) {
            clear();
            state = State.FAILED;
            throw failure;
        }
    }

    /** Returns the completed report once, after EndTransfer. Early finish makes the session terminal. */
    public ValidationReport finish() {
        requireActive();
        try {
            require(State.AFTER_END);
            return completedReport(List.of());
        } catch (RuntimeException failure) {
            clear();
            state = State.FAILED;
            throw failure;
        }
    }

    void finishDatasetInput() {
        require(State.AFTER_END);
        source = Optional.empty();
        state = State.BEFORE_TRANSFER;
    }

    ValidationReport finishDataset(List<DatasetRuleCoverage> datasetCoverage) {
        require(State.BEFORE_TRANSFER);
        return completedReport(datasetCoverage);
    }

    void relationCardinality(TransferObject object, Symbol declaration, DataLocation data, long targets,
                             guru.interlis.model.Cardinality bounds, boolean complete) {
        var actual = BigInteger.valueOf(targets);
        var upper = bounds.upper();
        boolean over = upper instanceof CardinalityUpper.Finite maximum && actual.compareTo(maximum.value()) > 0;
        var key = new SlotKey(object.type(), plan.ref(declaration.id()), CARDINALITY);
        if (!complete && !over) {
            missingRuntime.merge(key, 1L, Math::addExact);
            return;
        }
        checked.merge(key, 1L, Math::addExact);
        if (over || actual.compareTo(bounds.lower()) < 0) {
            diagnostic(CARDINALITY_VIOLATION, data, ruleLocation(plan.ref(declaration.id()), declaration.origin()),
                    List.of(actual.toString(), bounds.lower().toString(),
                            upper instanceof CardinalityUpper.Finite maximum ? maximum.value().toString() : "*"));
        }
    }

    void relationFinding(TransferObject object, Symbol declaration, DataLocation data,
                         Optional<ValidationCode> violation, boolean complete, List<String> arguments) {
        var key = new SlotKey(object.type(), plan.ref(declaration.id()), CARDINALITY);
        if (violation.isPresent()) {
            diagnostic(violation.orElseThrow(), data,
                    ruleLocation(plan.ref(declaration.id()), declaration.origin()), arguments);
        }
        (complete || violation.isPresent() ? checked : missingRuntime).merge(key, 1L, Math::addExact);
    }

    void datasetViolation(Symbol declaration, ValidationCode code, DataLocation data, List<String> arguments) {
        diagnostic(code, data, ruleLocation(plan.ref(declaration.id()), declaration.origin()), arguments);
    }

    void identityConflict(TransferObject object, DataLocation data, List<String> origins) {
        var declaration = plan.symbol(object.type().symbol()).orElseThrow();
        diagnostic(AMBIGUOUS_OBJECT_IDENTITY, data,
                ruleLocation(object.type(), declaration.origin()), origins);
    }

    void referenceResult(TransferObject object, Symbol slot, DataLocation data, ReferenceResolver.Result result) {
        var key = new SlotKey(object.type(), plan.ref(slot.id()), REFERENTIAL_INTEGRITY);
        if (result == ReferenceResolver.Missing.OPEN) {
            missingRuntime.merge(key, 1L, Math::addExact);
            return;
        }
        checked.merge(key, 1L, Math::addExact);
        var rule = ruleLocation(plan.ref(slot.id()), slot.origin());
        switch (result) {
            case ReferenceResolver.Found ignored -> { }
            case ReferenceResolver.Missing ignored -> diagnostic(REFERENCE_TARGET_MISSING, data, rule, List.of());
            case ReferenceResolver.InvalidTarget invalid -> diagnostic(
                    invalid.reason() == ReferenceResolver.Reason.TYPE ? REFERENCE_TARGET_TYPE : REFERENCE_TARGET_BASKET,
                    data, rule, List.of(Long.toString(invalid.target().ordinal())));
            case ReferenceResolver.Ambiguous ambiguous -> diagnostic(AMBIGUOUS_OBJECT_IDENTITY, data, rule,
                    List.of(Long.toString(ambiguous.first().ordinal()), Long.toString(ambiguous.second().ordinal())));
        }
    }

    private ValidationReport completedReport(List<DatasetRuleCoverage> datasetCoverage) {
        var report = new ValidationReport(violations == 0 ? PASSED_CHECKED_SCOPE : FAILED_CHECKED_SCOPE,
                baskets, objects, violations, examples,
                new ValidationCoverage(counts(checked), plan.uncheckedRules(), counts(incomplete),
                        counts(missingRuntime), counts(unsupported),
                        plan.profile() == ValidationProfile.LOCAL_ONLY ? List.of(REFERENTIAL_INTEGRITY, TOPOLOGY) : List.of(TOPOLOGY),
                        constraintCounts(), datasetCoverage));
        clear();
        state = State.FINISHED;
        return report;
    }

    /** Releases session state. Repeated close calls are harmless. */
    @Override
    public void close() {
        clear();
        state = State.CLOSED;
    }

    private void inspect(TransferEvent.ObjectEvent event, CancellationToken cancellation) {
        var object = event.object();
        if (!object.identity().currentBasket().equals(basket.start().id())) {
            throw new ValidationStreamException("Object does not belong to the active basket");
        }
        var objectPlan = plan.object(object.type());
        if (objectPlan == null || !objectPlan.descriptor().transferable()) {
            throw new ValidationStreamException("Object type does not belong to the validation plan");
        }
        checkObjectIdentity(object, objectPlan);
        var budget = new ObjectBudget(cancellation, checkValueBudget(object, cancellation));
        var stack = new ArrayDeque<Iterator<PendingValue>>();
        stack.push(fields(objectPlan, object.data(), null));
        boolean delete = event.operation().orElse(null) == TransferEvent.Operation.DELETE;
        boolean partial = event.consistency() == TransferEvent.Consistency.INCOMPLETE
                || basket.start().consistency() == TransferEvent.Consistency.INCOMPLETE;
        for (var entry : object.data().values().entrySet()) {
            if (entry.getValue() instanceof TransferValue.Structured payload
                    && plan.symbol(entry.getKey().symbol()).orElse(null) instanceof RoleSymbol) {
                var association = plan.object(payload.type());
                if (association != null && association.descriptor().kind() == SymbolKind.ASSOCIATION) {
                    budget.consume();
                    var path = new ValuePath(null, new DataLocation.Slot(entry.getKey()));
                    checkObjectConstraints(object, association, payload, path, delete, partial, budget);
                    stack.push(fields(association, payload, path));
                }
            }
        }
        while (!stack.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            var iterator = stack.peek();
            if (!iterator.hasNext()) {
                stack.pop();
                continue;
            }
            budget.consume();
            var pending = iterator.next();
            var children = check(object, pending, delete, partial, budget);
            if (children.isPresent()) {
                stack.push(children.orElseThrow());
            }
        }
        checkRoles(object, objectPlan, budget);
        checkObjectConstraints(object, objectPlan, object.data(), null, delete, partial, budget);
    }

    private void checkRoles(TransferObject object, ObjectValidationPlan objectPlan, ObjectBudget budget) {
        for (var slot : objectPlan.descriptor().slots()) {
            if (!(slot instanceof RoleSlot role)) {
                continue;
            }
            budget.consume();
            var value = object.value(role.symbol());
            if (value.isEmpty() || value.orElseThrow() == TransferValue.Undefined.INSTANCE) {
                continue;
            }
            checked.merge(new SlotKey(object.type(), role.symbol(), VALUE_FAMILY), 1L, Math::addExact);
            if (!(value.orElseThrow() instanceof TransferValue.Reference)) {
                violation(object, new ValuePath(null, new DataLocation.Slot(role.symbol())), role.role(), TYPE_MISMATCH,
                        List.of("REFERENCE", value.orElseThrow().getClass().getSimpleName()));
            }
        }
    }

    private Iterator<PendingValue> fields(ObjectValidationPlan object, TransferValue.Structured value, ValuePath parent) {
        return object.fields().stream().map(field -> new PendingValue(object.descriptor().symbol(), field, 0,
                value.value(field.slot().symbol()), value,
                new ValuePath(parent, new DataLocation.Slot(field.slot().symbol())))).iterator();
    }

    private Optional<Iterator<PendingValue>> check(TransferObject object, PendingValue pending, boolean delete,
                                                   boolean partial, ObjectBudget budget) {
        var field = pending.field();
        var rules = field.values().get(pending.depth());
        var value = pending.value();
        var actual = value.orElse(TransferValue.Undefined.INSTANCE);
        int textLength = rules.type().kind() == TypeKind.TEXT && actual instanceof TransferValue.Text text
                ? ScalarRules.textLength(text.value()) : -1;
        boolean undefined = actual == TransferValue.Undefined.INSTANCE || textLength == 0;
        boolean uncertain = value.isEmpty() && (delete || (partial && rules.type().kind() == TypeKind.GEOMETRY));
        if (pending.depth() == 0 && field.derived()) {
            var attribute = field.slot().attribute();
            boolean deferred = plan.profile() == ValidationProfile.DATASET && plan.derivedNeedsDataset(attribute.id());
            if (!deferred && !delete) {
                var frame = new EvaluationFrame(plan, pending.container(), partial, budget::consume,
                        java.util.OptionalLong.empty(), budget.cancellation);
                var derived = DerivedValueResolver.resolve(attribute, frame);
                if (derived instanceof EvaluationResult.Known || derived instanceof EvaluationResult.KnownNumber
                        || derived instanceof EvaluationResult.UndefinedByData) {
                    if (value.isPresent() && (actual == TransferValue.Undefined.INSTANCE
                            || ValueFamilies.match(rules.type(), actual) == ValueFamilies.Match.YES)
                            && !DerivedValueResolver.matches(derived, actual, rules.type())) {
                        violation(object, pending, attribute, DERIVED_VALUE_MISMATCH, List.of());
                    }
                } else if (value.isEmpty()) {
                    count(missingRuntime, pending, VALUE_RANGE);
                }
            }
            if (value.isEmpty()) {
                return Optional.empty();
            }
        }
        if (rules.required()) {
            count(uncertain ? incomplete : checked, pending, DEFINED_VALUE);
            if (undefined && !uncertain) {
                violation(object, pending, rules.requiredDeclaration().orElseThrow(),
                        value.isEmpty() ? REQUIRED_VALUE_MISSING : REQUIRED_VALUE_UNDEFINED, List.of());
                return Optional.empty();
            }
        }
        if (undefined) {
            if (rules.type() instanceof CollectionType collection) {
                if (uncertain) {
                    count(incomplete, pending, CARDINALITY);
                } else {
                    cardinality(object, pending, collection, 0);
                }
            }
            return Optional.empty();
        }
        var concreteDomain = rules.genericDomain().map(basket.domains()::get);
        var type = rules.genericDomain().isPresent() ? concreteDomain.flatMap(plan::domainType) : Optional.of(rules.type());
        if (type.isEmpty()) {
            count(missingRuntime, pending, GEOMETRY_VALUES);
        }
        var effective = type.orElse(rules.type());
        switch (ValueFamilies.match(effective, actual)) {
            case UNSUPPORTED -> {
                count(unsupported, pending, VALUE_FAMILY);
                return Optional.empty();
            }
            case NO -> {
                count(checked, pending, VALUE_FAMILY);
                violation(object, pending, field.slot().attribute(), TYPE_MISMATCH,
                        List.of(effective.kind().name(), actual.getClass().getSimpleName()));
                return Optional.empty();
            }
            case YES -> count(checked, pending, VALUE_FAMILY);
        }
        if (type.isPresent()) {
            concreteDomain.ifPresent(domain -> {
                for (var declaration : rules.concreteUnsupported().getOrDefault(domain, List.of())) {
                    budget.consume();
                    countConstraint(pending.owner(), Optional.of(field.slot().symbol()), declaration,
                            ConstraintStatus.UNSUPPORTED_EXPRESSION);
                }
            });
            for (var declaration : rules.constraints(concreteDomain)) {
                if (declaration.definition() instanceof DomainConstraint domain) {
                    checkConstraint(object, pending.owner(), Optional.of(field.slot().symbol()), declaration,
                            domain.condition(), actual, pending.path(), false, partial, budget);
                }
            }
        }
        if (actual instanceof TransferGeometry geometry) {
            geometry(object, pending, effective, geometry, partial, type.isPresent(), budget);
            return Optional.empty();
        }
        if (effective instanceof OidType oid && actual instanceof TransferValue.Oid valueOid) {
            boolean applied = OidRules.check(plan, oid, valueOid.lexeme(), (code, arguments) ->
                    violation(object, pending, field.slot().attribute(), code, arguments));
            count(applied ? checked : unsupported, pending, IDENTITY);
            return Optional.empty();
        }
        if (actual instanceof TransferValue.ClassName || actual instanceof TransferValue.AttributePath) {
            var result = ModelValueRules.check(plan, effective, actual);
            if (result == ModelValueRules.Result.UNKNOWN) {
                count(missingRuntime, pending, VALUE_RANGE);
            } else {
                count(checked, pending, VALUE_RANGE);
                if (result == ModelValueRules.Result.INVALID) {
                    violation(object, pending, field.slot().attribute(), MODEL_VALUE_NOT_ALLOWED, List.of());
                }
            }
            return Optional.empty();
        }
        var coverage = ScalarRules.check(plan, budget::consume, rules, actual, textLength, field.slot().attribute().languageVersion(),
                field.charset(), (code, arguments) -> violation(object, pending, field.slot().attribute(), code, arguments));
        if (coverage != ScalarRules.Coverage.NOT_APPLICABLE) {
            count(coverage == ScalarRules.Coverage.CHECKED ? checked : unsupported, pending, VALUE_RANGE);
        }
        if (rules.type() instanceof CollectionType collection && actual instanceof TransferValue.Repeated repeated) {
            if (collection.collectionKind() != repeated.kind()) {
                violation(object, pending, field.slot().attribute(), TYPE_MISMATCH,
                        List.of(collection.collectionKind().name(), repeated.kind().name()));
                return Optional.empty();
            }
            cardinality(object, pending, collection, repeated.values().size());
            return Optional.of(java.util.stream.IntStream.range(0, repeated.values().size()).mapToObj(index ->
                    new PendingValue(pending.owner(), field, pending.depth() + 1,
                            Optional.of(repeated.values().get(index)), pending.container(),
                            new ValuePath(pending.path(), new DataLocation.Index(index + 1)))).iterator());
        }
        if (rules.type() instanceof StructureValueType structure && actual instanceof TransferValue.Structured data) {
            count(checked, pending, STRUCTURE);
            var child = plan.object(data.type());
            if (child == null || !plan.assignable(structure, child)) {
                violation(object, pending, field.slot().attribute(), STRUCTURE_TYPE_MISMATCH, List.of());
            } else {
                checkObjectConstraints(object, child, data, pending.path(), false, partial, budget);
                return Optional.of(fields(child, data, pending.path()));
            }
        }
        return Optional.empty();
    }

    private void checkObjectConstraints(TransferObject root, ObjectValidationPlan objectPlan,
                                        TransferValue.Structured current, ValuePath path,
                                        boolean incomplete, boolean partial, ObjectBudget budget) {
        for (var declaration : objectPlan.constraints()) {
            if (declaration.target().isPresent() && declaration.header().owner()
                    .filter(id -> plan.symbol(id).orElse(null) instanceof TopicSymbol)
                    .filter(id -> !basket.topicScope().contains(id)).isPresent()) {
                continue;
            }
            if (declaration.definition() instanceof MandatoryConstraint mandatory) {
                checkConstraint(root, objectPlan.descriptor().symbol(), Optional.empty(), declaration,
                        mandatory.condition(), current, path, incomplete, partial, budget);
            } else if (declaration.definition() instanceof UniquenessConstraint unique) {
                var status = incomplete ? ConstraintStatus.INCOMPLETE_CONTEXT
                        : LocalUnique.check(unique, plan, current, path, partial, budget::consume,
                                (first, duplicate) -> violation(root, duplicate, declaration, LOCAL_UNIQUE_DUPLICATE,
                                        List.of(indices(first), indices(duplicate))));
                countConstraint(objectPlan.descriptor().symbol(), Optional.empty(), declaration, status);
            }
        }
    }

    private void checkConstraint(TransferObject root, SymbolRef owner, Optional<SymbolRef> slot,
                                 ConstraintSymbol declaration, ExpressionId expression, TransferValue current,
                                 ValuePath path, boolean incomplete, boolean partial, ObjectBudget budget) {
        if (incomplete) {
            countConstraint(owner, slot, declaration, ConstraintStatus.INCOMPLETE_CONTEXT);
            return;
        }
        if (plan.profile() == ValidationProfile.DATASET && plan.requiresDataset(plan.expression(expression))) {
            return;
        }
        var result = plan.expression(expression).evaluate(new EvaluationFrame(plan, current, partial, budget::consume,
                java.util.OptionalLong.empty(), budget.cancellation));
        recordConstraint(root, owner, slot, declaration, path, result, null);
    }

    void recordDeferredDerived(DatasetRuleContext.Occurrence occurrence, ObjectValidationPlan.FieldRule field,
                               EvaluationResult result, TypeDescriptor effectiveType) {
        var attribute = field.slot().attribute();
        var location = occurrence.location(List.of(new DataLocation.Slot(field.slot().symbol())));
        var supplied = occurrence.object().data().value(field.slot().symbol());
        var key = new SlotKey(occurrence.object().type(), field.slot().symbol(), VALUE_RANGE);
        switch (result) {
            case EvaluationResult.Known _, EvaluationResult.KnownNumber _, EvaluationResult.UndefinedByData _ -> {
                checked.merge(key, 1L, Math::addExact);
                if (supplied.isPresent() && !DerivedValueResolver.matches(result, supplied.orElseThrow(), effectiveType)) {
                    datasetViolation(attribute, DERIVED_VALUE_MISMATCH, location, List.of());
                } else if (result instanceof EvaluationResult.UndefinedByData && field.values().getFirst().required()) {
                    datasetViolation(attribute, REQUIRED_VALUE_UNDEFINED, location, List.of());
                }
            }
            case EvaluationResult.Unsupported _ -> unsupported.merge(key, 1L, Math::addExact);
            default -> missingRuntime.merge(key, 1L, Math::addExact);
        }
    }

    void recordUnavailableView(ViewPlan view) {
        for (var constraint : view.constraints()) {
            countConstraint(plan.ref(view.view().id()), Optional.empty(), constraint,
                    ConstraintStatus.UNSUPPORTED_EXPRESSION);
        }
    }

    void recordViewUnique(ViewRow row, ConstraintSymbol declaration, ConstraintStatus status) {
        countConstraint(plan.ref(row.view()), Optional.empty(), declaration, status);
    }

    void recordViewConstraint(ViewRow row, ConstraintSymbol declaration, EvaluationResult result) {
        var origin = row.origins().getFirst();
        recordConstraint(origin.object(), plan.ref(row.view()), Optional.empty(), declaration, null, result,
                origin.location(List.of()));
    }

    void recordDatasetConstraint(DatasetRuleContext.Occurrence occurrence, ConstraintSymbol declaration,
                                 EvaluationResult result) {
        recordConstraint(occurrence.object(), occurrence.object().type(), Optional.empty(), declaration,
                null, result, occurrence.location(List.of()));
    }

    private void recordConstraint(TransferObject root, SymbolRef owner, Optional<SymbolRef> slot,
                                  ConstraintSymbol declaration, ValuePath path, EvaluationResult result,
                                  DataLocation datasetLocation) {
        var status = switch (result) {
            case EvaluationResult.Known known when known.value() instanceof TransferValue.BooleanValue value -> {
                if (!value.value()) {
                    var code = declaration.definition() instanceof DomainConstraint
                            ? DOMAIN_CONSTRAINT_FALSE : MANDATORY_CONSTRAINT_FALSE;
                    if (datasetLocation == null) {
                        violation(root, path, declaration, code, List.of());
                    } else {
                        datasetViolation(declaration, code, datasetLocation, List.of());
                    }
                }
                yield ConstraintStatus.CHECKED;
            }
            case EvaluationResult.UndefinedByData undefined -> {
                if (declaration.definition() instanceof MandatoryConstraint
                        && (declaration.languageVersion() == LanguageVersion.ILI24 || undefined.comparison())) {
                    yield ConstraintStatus.UNDEFINED_BY_DATA;
                }
                yield ConstraintStatus.UNSUPPORTED_VALUE;
            }
            case EvaluationResult.BlockedByViolation _ -> ConstraintStatus.INCOMPLETE_CONTEXT;
            case EvaluationResult.MissingContext _ -> ConstraintStatus.MISSING_RUNTIME_CONTEXT;
            case EvaluationResult.Unsupported _, EvaluationResult.Known _, EvaluationResult.KnownNumber _, EvaluationResult.KnownObjects _ -> ConstraintStatus.UNSUPPORTED_VALUE;
        };
        countConstraint(owner, slot, declaration, status);
    }

    private void countConstraint(SymbolRef owner, Optional<SymbolRef> slot, ConstraintSymbol rule, ConstraintStatus status) {
        constraints.merge(new ConstraintKey(owner, slot, plan.ref(rule.id()), status), 1L, Math::addExact);
    }

    private List<ValidationCoverage.ConstraintCount> constraintCounts() {
        return constraints.entrySet().stream().map(entry -> {
            var key = entry.getKey();
            return new ValidationCoverage.ConstraintCount(key.owner(), key.slot(), key.declaration(), key.status(),
                    entry.getValue());
        }).toList();
    }

    private Optional<TypeDescriptor> concreteDomain(SymbolId generic) {
        var concrete = basket.domains().get(generic);
        return concrete == null ? Optional.empty() : plan.domainType(concrete);
    }

    private void geometry(TransferObject object, PendingValue pending, TypeDescriptor type,
                          TransferGeometry value, boolean partial, boolean concrete, ObjectBudget budget) {
        final CoordinateType vertices;
        final List<String> forms;
        if (type instanceof CoordinateType coordinate) {
            if (coordinate.multiCoordinate() != (value instanceof TransferGeometry.MultiCoordinate)) {
                violation(object, pending, pending.field().slot().attribute(), TYPE_MISMATCH,
                        List.of(coordinate.multiCoordinate() ? "MULTICOORD" : "COORD", value.getClass().getSimpleName()));
                return;
            }
            vertices = concrete ? coordinate : null;
            forms = List.of();
        } else if (type instanceof GeometryType geometry) {
            if (!GeometryRules.accepts(geometry, value, partial)) {
                violation(object, pending, pending.field().slot().attribute(), TYPE_MISMATCH,
                        List.of(geometry.geometryKind().name(), value.getClass().getSimpleName()));
                return;
            }
            vertices = geometry.vertexDomain().flatMap(id -> {
                var symbol = plan.symbol(id).orElse(null);
                if (symbol instanceof guru.interlis.model.DomainSymbol domain && domain.generic()) {
                    return concreteDomain(id);
                }
                return plan.domainType(id);
            }).filter(CoordinateType.class::isInstance).map(CoordinateType.class::cast).orElse(null);
            forms = GeometryRules.forms(plan, geometry);
        } else {
            return;
        }
        var rules = new GeometryRules(vertices, forms, budget::consume, (code, suffix, arguments) -> {
            var path = pending.path();
            for (var step : suffix) {
                path = new ValuePath(path, step);
            }
            violation(object, path, pending.field().slot().attribute(), code, arguments);
        });
        if (rules.check(value)) {
            count(checked, pending, GEOMETRY_VALUES);
        } else if (concrete) {
            count(vertices == null ? missingRuntime : unsupported, pending, GEOMETRY_VALUES);
        }
    }

    private void checkBasketIdentity() {
        var event = basket.start();
        var data = new DataLocation(source, event.id(), Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        var checkedDomains = new HashSet<SymbolId>();
        for (var topic : basket.topics()) {
            for (var rule : topic.oidRules()) {
                if (rule.basket() && rule.domain().filter(checkedDomains::add).isPresent()) {
                    identity(rule, topic.symbol(), event.stableOid(), data);
                }
            }
        }
    }

    private void checkObjectIdentity(TransferObject object, ObjectValidationPlan objectPlan) {
        var descriptor = objectPlan.descriptor();
        var rule = descriptor.oidRules().stream().filter(candidate -> !candidate.basket()).findFirst();
        if (rule.isEmpty() && descriptor.kind() == SymbolKind.CLASS) {
            rule = descriptor.topic().flatMap(plan.transferPlan()::topic)
                    .flatMap(topic -> topic.oidRules().stream().filter(candidate -> !candidate.basket()).findFirst());
        }
        var id = object.identity();
        if (rule.isPresent() && rule.orElseThrow().domain().isPresent()) {
            var data = location(object, List.of());
            identity(rule.orElseThrow(), object.type(), id.oid().map(oid -> oid.value()), data);
            if (id.oid().isPresent() && id.tid().isEmpty()) {
                diagnostic(REQUIRED_TID_MISSING, data, ruleLocation(object.type(), rule.orElseThrow().origin()), List.of());
            }
        } else if (rule.isEmpty() && descriptor.modifiers().contains(Modifier.OID) && id.tid().isEmpty()) {
            var declaration = plan.symbol(object.type().symbol()).orElseThrow();
            diagnostic(REQUIRED_TID_MISSING, location(object, List.of()),
                    ruleLocation(object.type(), declaration.origin()), List.of());
        }
    }

    private void identity(OidRule rule, SymbolRef owner, Optional<String> value, DataLocation data) {
        var domain = plan.ref(rule.domain().orElseThrow());
        var key = new SlotKey(owner, domain, IDENTITY);
        var location = ruleLocation(domain, rule.origin());
        if (value.isEmpty()) {
            checked.merge(key, 1L, Math::addExact);
            diagnostic(REQUIRED_OID_MISSING, data, location, List.of());
        } else if (plan.domainType(domain.symbol()).orElse(null) instanceof OidType oid
                && OidRules.check(plan, oid, value.orElseThrow(),
                    (code, arguments) -> diagnostic(code, data, location, arguments))) {
            checked.merge(key, 1L, Math::addExact);
        } else {
            unsupported.merge(key, 1L, Math::addExact);
        }
    }

    private void cardinality(TransferObject object, PendingValue pending, CollectionType collection, int size) {
        count(checked, pending, CARDINALITY);
        var bound = collection.cardinality();
        var actual = BigInteger.valueOf(size);
        if (actual.compareTo(bound.lower()) < 0
                || (bound.upper() instanceof CardinalityUpper.Finite upper && actual.compareTo(upper.value()) > 0)) {
            violation(object, pending, pending.field().slot().attribute(), CARDINALITY_VIOLATION,
                    List.of(Integer.toString(size), bound.lower().toString(),
                            bound.upper() instanceof CardinalityUpper.Finite upper ? upper.value().toString() : "*"));
        }
    }

    private static void count(Map<SlotKey, Long> counts, PendingValue pending, ValidationCoverage.RuleFamily family) {
        counts.merge(new SlotKey(pending.owner(), pending.field().slot().symbol(), family), 1L, Math::addExact);
    }

    private long checkValueBudget(TransferObject object, CancellationToken cancellation) {
        var stack = new ArrayDeque<Iterator<TransferValue>>();
        stack.push(object.data().values().values().iterator());
        long visited = 0;
        while (!stack.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            var current = stack.peek();
            if (!current.hasNext()) {
                stack.pop();
                continue;
            }
            visited = visit(visited);
            Iterator<TransferValue> children = switch (current.next()) {
                case TransferValue.Structured structure -> structure.values().values().iterator();
                case TransferValue.Repeated repeated -> repeated.values().iterator();
                default -> null;
            };
            if (children != null) {
                if (stack.size() > options.maxNestedDepth()) {
                    throw new ValidationLimitException("Maximum value depth exceeded");
                }
                stack.push(children);
            }
        }
        return visited;
    }

    private long visit(long visited) {
        if (visited == options.maxValuesPerObject()) {
            throw new ValidationLimitException("Maximum inspected values per object exceeded");
        }
        return visited + 1;
    }

    private void violation(TransferObject object, PendingValue pending, Symbol declaration,
                           ValidationCode code, List<String> arguments) {
        violation(object, pending.path(), declaration, code, arguments);
    }

    private void violation(TransferObject object, ValuePath at, Symbol declaration,
                           ValidationCode code, List<String> arguments) {
        if (!retainExample()) {
            return;
        }
        var path = new ArrayList<DataLocation.Step>();
        for (var step = at; step != null; step = step.parent()) {
            path.add(step.step());
        }
        examples.add(new ValidationDiagnostic(code, Severity.ERROR, location(object, path.reversed()),
                ruleLocation(plan.ref(declaration.id()), declaration.origin()), arguments));
    }

    private void diagnostic(ValidationCode code, DataLocation data, RuleLocation rule, List<String> arguments) {
        if (retainExample()) {
            examples.add(new ValidationDiagnostic(code, Severity.ERROR, data, rule, arguments));
        }
    }

    private boolean retainExample() {
        violations = Math.incrementExact(violations);
        return examples.size() < options.maxDiagnosticExamples();
    }

    private DataLocation location(TransferObject object, List<DataLocation.Step> path) {
        var identity = object.identity();
        return new DataLocation(identity.source().or(() -> source), identity.currentBasket(),
                identity.tid(), identity.oid(), Optional.of(object.type()), path);
    }

    private static RuleLocation ruleLocation(SymbolRef declaration, Origin origin) {
        Optional<SourceRange> range = switch (origin) {
            case Origin.DeclaredOrigin declared -> Optional.of(declared.range());
            case Origin.OverlayOrigin overlay -> overlay.range();
            case Origin.SyntheticOrigin synthetic -> synthetic.range();
            case Origin.DerivedOrigin _, Origin.PredefinedOrigin _ -> Optional.empty();
        };
        return new RuleLocation(declaration, range);
    }

    private static List<ValidationCoverage.RuleCount> counts(Map<SlotKey, Long> counts) {
        return counts.entrySet().stream().map(entry -> new ValidationCoverage.RuleCount(
                entry.getKey().objectType(), entry.getKey().slot(), entry.getKey().family(), entry.getValue())).toList();
    }

    private void require(State expected) {
        if (state != expected) {
            throw new ValidationStreamException("Expected " + expected + " but session is " + state);
        }
    }

    private void requireActive() {
        if (state == State.FINISHED || state == State.FAILED || state == State.CLOSED) {
            throw new ValidationStreamException("Session is " + state);
        }
    }

    private void clear() {
        basket = null;
        source = Optional.empty();
        examples.clear();
        checked.clear();
        incomplete.clear();
        missingRuntime.clear();
        unsupported.clear();
        constraints.clear();
    }

    private record SlotKey(SymbolRef objectType, SymbolRef slot, ValidationCoverage.RuleFamily family) { }

    private record ConstraintKey(SymbolRef owner, Optional<SymbolRef> slot, SymbolRef declaration,
                                 ConstraintStatus status) { }

    private static String indices(ValuePath path) {
        var positions = new ArrayList<String>();
        for (var step = path; step != null; step = step.parent()) {
            if (step.step() instanceof DataLocation.Index index) {
                positions.add(Integer.toString(index.position()));
            }
        }
        return String.join("/", positions.reversed());
    }

    record ValuePath(ValuePath parent, DataLocation.Step step) { }

    private record PendingValue(SymbolRef owner, ObjectValidationPlan.FieldRule field, int depth,
                                Optional<TransferValue> value, TransferValue.Structured container, ValuePath path) { }

    private final class ObjectBudget {
        private final CancellationToken cancellation;
        private long visited;

        private ObjectBudget(CancellationToken cancellation, long visited) {
            this.cancellation = cancellation;
            this.visited = visited;
        }

        void consume() {
            cancellation.throwIfCancellationRequested();
            visited = visit(visited);
        }
    }

    record BasketContext(TransferEvent.StartBasket start, List<TopicDescriptor> topics,
                                 Map<SymbolId, SymbolId> domains, Set<SymbolId> topicScope) {
        static BasketContext of(TransferEvent.StartBasket event, ValidationPlan plan) {
            var topic = topic(plan, event.topic());
            var topics = new ArrayList<TopicDescriptor>();
            topics.add(topic);
            for (var ref : event.additionalTopics()) {
                topics.add(topic(plan, ref));
            }
            Map<SymbolId, SymbolId> supplied = new LinkedHashMap<>();
            for (var binding : event.domainBindings()) {
                if (!plan.snapshotId().equals(binding.generic().snapshot())
                        || !plan.snapshotId().equals(binding.concrete().snapshot())
                        || supplied.putIfAbsent(binding.generic().symbol(), binding.concrete().symbol()) != null) {
                    throw new ValidationStreamException("Invalid basket domain binding");
                }
            }
            Map<SymbolId, SymbolId> selected = new LinkedHashMap<>();
            for (var active : topics) {
                for (var binding : active.genericBindings()) {
                    var generic = binding.genericDomain();
                    var concrete = switch (binding.state()) {
                        case CONCRETE -> supplied.getOrDefault(generic, binding.alternatives().getFirst());
                        case DEFERRED -> supplied.get(generic);
                        case ABSTRACT -> null;
                    };
                    if (concrete != null) {
                        if (!binding.alternatives().contains(concrete)) {
                            throw new ValidationStreamException("Basket domain binding is outside the topic alternatives");
                        }
                        var previous = selected.putIfAbsent(generic, concrete);
                        if (previous != null && !previous.equals(concrete)) {
                            throw new ValidationStreamException("Basket topics disagree on a generic domain");
                        }
                    }
                }
            }
            if (!selected.keySet().containsAll(supplied.keySet())) {
                throw new ValidationStreamException("Unexpected basket domain binding");
            }
            var topicScope = new HashSet<SymbolId>();
            for (var active : topics) {
                var ancestor = active;
                while (topicScope.add(ancestor.symbol().symbol()) && ancestor.baseTopic().isPresent()) {
                    ancestor = topic(plan, ancestor.baseTopic().orElseThrow());
                }
            }
            return new BasketContext(event, List.copyOf(topics), Map.copyOf(selected), Set.copyOf(topicScope));
        }

        private static TopicDescriptor topic(ValidationPlan plan, SymbolRef ref) {
            if (!plan.snapshotId().equals(ref.snapshot())) {
                throw new ValidationStreamException("Basket topic belongs to another snapshot");
            }
            return plan.transferPlan().topic(ref)
                    .orElseThrow(() -> new ValidationStreamException("Basket topic does not belong to the plan"));
        }
    }
}
