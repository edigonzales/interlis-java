package guru.interlis.validation;

import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CollectionType;
import guru.interlis.model.Origin;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TypeKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
     * Accepts one non-null event. A sequence error or cancellation makes this session terminal.
     * Hosts that obtain events from a reader must separately confirm EOF before publishing a report.
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
            var report = new ValidationReport(violations == 0 ? PASSED_CHECKED_SCOPE : FAILED_CHECKED_SCOPE,
                    baskets, objects, violations, examples,
                    new ValidationCoverage(counts(checked), plan.uncheckedRules(), counts(incomplete),
                            counts(missingRuntime), counts(unsupported), List.copyOf(EnumSet.of(VALUE_RANGE, IDENTITY, GEOMETRY_VALUES,
                                    LOCAL_CONSTRAINTS, LOCAL_UNIQUENESS, REFERENTIAL_INTEGRITY, TOPOLOGY))));
            clear();
            state = State.FINISHED;
            return report;
        } catch (RuntimeException failure) {
            clear();
            state = State.FAILED;
            throw failure;
        }
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
        long visited = checkValueBudget(object, cancellation);
        var stack = new ArrayDeque<Iterator<PendingValue>>();
        stack.push(fields(objectPlan, object.data(), null));
        boolean delete = event.operation().orElse(null) == TransferEvent.Operation.DELETE;
        boolean partial = event.consistency() == TransferEvent.Consistency.INCOMPLETE
                || basket.start().consistency() == TransferEvent.Consistency.INCOMPLETE;
        while (!stack.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            var iterator = stack.peek();
            if (!iterator.hasNext()) {
                stack.pop();
                continue;
            }
            visited = visit(visited);
            var pending = iterator.next();
            var children = check(object, pending, delete, partial);
            if (children.isPresent()) {
                stack.push(children.orElseThrow());
            }
        }
    }

    private Iterator<PendingValue> fields(ObjectValidationPlan object, TransferValue.Structured value, ValuePath parent) {
        return object.fields().stream().map(field -> new PendingValue(object.descriptor().symbol(), field, 0,
                value.value(field.slot().symbol()),
                new ValuePath(parent, new DataLocation.Slot(field.slot().symbol())))).iterator();
    }

    private Optional<Iterator<PendingValue>> check(TransferObject object, PendingValue pending, boolean delete,
                                                   boolean partial) {
        var field = pending.field();
        var rules = field.values().get(pending.depth());
        var value = pending.value();
        var actual = value.orElse(TransferValue.Undefined.INSTANCE);
        boolean undefined = actual == TransferValue.Undefined.INSTANCE
                || (rules.type().kind() == TypeKind.TEXT && actual instanceof TransferValue.Text text
                    && text.value().isEmpty());
        boolean uncertain = value.isEmpty() && (delete || (partial && rules.type().kind() == TypeKind.GEOMETRY));
        if (value.isEmpty() && field.derived()) {
            if (rules.required()) {
                count(uncertain ? incomplete : missingRuntime, pending, DEFINED_VALUE);
            }
            if (rules.type() instanceof CollectionType) {
                count(uncertain ? incomplete : missingRuntime, pending, CARDINALITY);
            }
            return Optional.empty();
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
        switch (ValueFamilies.match(rules.type(), actual)) {
            case UNSUPPORTED -> {
                count(unsupported, pending, VALUE_FAMILY);
                return Optional.empty();
            }
            case NO -> {
                count(checked, pending, VALUE_FAMILY);
                violation(object, pending, field.slot().attribute(), TYPE_MISMATCH,
                        List.of(rules.type().kind().name(), actual.getClass().getSimpleName()));
                return Optional.empty();
            }
            case YES -> count(checked, pending, VALUE_FAMILY);
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
                            Optional.of(repeated.values().get(index)),
                            new ValuePath(pending.path(), new DataLocation.Index(index + 1)))).iterator());
        }
        if (rules.type() instanceof StructureValueType structure && actual instanceof TransferValue.Structured data) {
            count(checked, pending, STRUCTURE);
            var child = plan.object(data.type());
            if (child == null || !assignable(structure, child)) {
                violation(object, pending, field.slot().attribute(), STRUCTURE_TYPE_MISMATCH, List.of());
            } else {
                return Optional.of(fields(child, data, pending.path()));
            }
        }
        return Optional.empty();
    }

    private boolean assignable(StructureValueType type, ObjectValidationPlan child) {
        var descriptor = child.descriptor();
        if (descriptor.kind() != SymbolKind.STRUCTURE || descriptor.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            return false;
        }
        if (type.targetRoots().isEmpty()) {
            return true;
        }
        for (;;) {
            if (type.targetRoots().contains(descriptor.symbol().symbol())) {
                return true;
            }
            if (descriptor.baseType().isEmpty()) {
                return false;
            }
            descriptor = plan.transferPlan().object(descriptor.baseType().orElseThrow()).orElseThrow();
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
        violations = Math.incrementExact(violations);
        if (examples.size() >= options.maxDiagnosticExamples()) {
            return;
        }
        var identity = object.identity();
        var path = new ArrayList<DataLocation.Step>();
        for (var step = pending.path(); step != null; step = step.parent()) {
            path.add(step.step());
        }
        var location = new DataLocation(identity.source().or(() -> source), identity.currentBasket(),
                identity.tid(), identity.oid(), object.type(), path.reversed());
        Optional<SourceRange> range = switch (declaration.origin()) {
            case Origin.DeclaredOrigin declared -> Optional.of(declared.range());
            case Origin.OverlayOrigin overlay -> overlay.range();
            case Origin.SyntheticOrigin synthetic -> synthetic.range();
            case Origin.DerivedOrigin _, Origin.PredefinedOrigin _ -> Optional.empty();
        };
        examples.add(new ValidationDiagnostic(code, Severity.ERROR, location,
                new RuleLocation(new SymbolRef(plan.snapshotId(), declaration.id()), range), arguments));
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
    }

    private record SlotKey(SymbolRef objectType, SymbolRef slot, ValidationCoverage.RuleFamily family) { }

    private record ValuePath(ValuePath parent, DataLocation.Step step) { }

    private record PendingValue(SymbolRef owner, ObjectValidationPlan.FieldRule field, int depth,
                                Optional<TransferValue> value, ValuePath path) { }

    private record BasketContext(TransferEvent.StartBasket start, Map<SymbolRef, SymbolRef> domains) {
        static BasketContext of(TransferEvent.StartBasket event, ValidationPlan plan) {
            if (!plan.snapshotId().equals(event.topic().snapshot())) {
                throw new ValidationStreamException("Basket topic belongs to another snapshot");
            }
            Map<SymbolRef, SymbolRef> domains = new LinkedHashMap<>();
            for (var binding : event.domainBindings()) {
                if (!plan.snapshotId().equals(binding.generic().snapshot())
                        || !plan.snapshotId().equals(binding.concrete().snapshot())
                        || domains.putIfAbsent(binding.generic(), binding.concrete()) != null) {
                    throw new ValidationStreamException("Invalid basket domain binding");
                }
            }
            return new BasketContext(event, Map.copyOf(domains));
        }
    }
}
