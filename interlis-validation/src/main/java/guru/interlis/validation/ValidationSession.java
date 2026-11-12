package guru.interlis.validation;

import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.Origin;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
                            List.of(ValidationCoverage.RuleFamily.values())));
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
        for (var field : objectPlan.fields()) {
            cancellation.throwIfCancellationRequested();
            visited = visit(visited);
            if (!field.values().getFirst().required()) {
                continue;
            }
            var slot = field.slot().symbol();
            var key = new SlotKey(object.type(), slot);
            if (event.operation().orElse(null) == TransferEvent.Operation.DELETE) {
                increment(incomplete, key);
                continue;
            }
            increment(checked, key);
            if (!object.data().values().containsKey(slot)) {
                missing(object, field);
            }
        }
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

    private void missing(TransferObject object, ObjectValidationPlan.FieldRule field) {
        violations = Math.incrementExact(violations);
        if (examples.size() >= options.maxDiagnosticExamples()) {
            return;
        }
        var identity = object.identity();
        var slot = field.slot().symbol();
        var location = new DataLocation(identity.source().or(() -> source), identity.currentBasket(),
                identity.tid(), identity.oid(), object.type(), List.of(new DataLocation.Slot(slot)));
        Optional<SourceRange> range = switch (field.slot().attribute().origin()) {
            case Origin.DeclaredOrigin declared -> Optional.of(declared.range());
            case Origin.OverlayOrigin overlay -> overlay.range();
            case Origin.SyntheticOrigin synthetic -> synthetic.range();
            case Origin.DerivedOrigin _, Origin.PredefinedOrigin _ -> Optional.empty();
        };
        examples.add(new ValidationDiagnostic(ValidationCode.REQUIRED_VALUE_MISSING, Severity.ERROR,
                location, new RuleLocation(slot, range), List.of()));
    }

    private static void increment(Map<SlotKey, Long> counts, SlotKey key) {
        counts.merge(key, 1L, Math::addExact);
    }

    private static List<ValidationCoverage.RuleCount> counts(Map<SlotKey, Long> counts) {
        return counts.entrySet().stream().map(entry -> new ValidationCoverage.RuleCount(
                entry.getKey().objectType(), entry.getKey().slot(), entry.getValue())).toList();
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
    }

    private record SlotKey(SymbolRef objectType, SymbolRef slot) { }

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
