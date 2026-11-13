package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ExistenceConstraint;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.SetConstraint;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Single-caller dataset run. Inputs and storage remain owned by the caller. */
public final class DatasetValidationSession implements AutoCloseable {
    private enum State { OPEN, READING, SEALED, FINISHED, FAILED, CLOSED }

    private final ValidationPlan plan;
    private boolean completeDelivery = true;
    private final DatasetOptions options;
    private final ValidationStorage storage;
    private final StoredValueCodec codec;
    private final DatasetObjectStore objectStore;
    private final IdentityIndex identities;
    private final ValidationSession local;
    private final Map<InputId, Long> inputs = new HashMap<>();
    private State state = State.OPEN;
    private long baskets;
    private long objects;
    private boolean primary;

    DatasetValidationSession(ValidationPlan plan, DatasetOptions options, ValidationStorage storage) {
        this.plan = plan;
        this.options = options;
        this.storage = storage;
        this.codec = new StoredValueCodec(plan.snapshotId(), options.limits().maxRecordBytes());
        if (storage.usage().entries() != 0) {
            throw new IllegalArgumentException("Dataset storage must be empty");
        }
        if (storage.table(StorageArea.METADATA).putIfAbsent(new byte[]{0}, new byte[0]).isPresent()) {
            throw new IllegalArgumentException("Dataset storage is already in use");
        }
        this.objectStore = new DatasetObjectStore(storage, codec);
        this.identities = new IdentityIndex(storage);
        this.local = new ValidationSession(plan, options.localOptions());
    }

    /** Reads one input through EOF. Any failure makes the session terminal. */
    public void readInput(InputDescriptor input, TransferReader reader, CancellationToken cancellation) {
        requireOpen();
        state = State.READING;
        try {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(reader, "reader");
            Objects.requireNonNull(cancellation, "cancellation").throwIfCancellationRequested();
            var readerPlan = reader.transferPlan();
            if (plan.transferPlan().schema() != readerPlan.schema()) {
                throw new ValidationStreamException("Reader belongs to another schema revision");
            }
            if (inputs.containsKey(input.id())) {
                throw new ValidationStreamException("Duplicate dataset input id");
            }
            if (inputs.size() == options.limits().maxInputs()) {
                throw new ValidationLimitException("Dataset input budget exceeded");
            }
            long inputOrdinal = inputs.size() + 1L;
            boolean checkLocal = input.role() == InputRole.PRIMARY;
            var inputState = new InputState();
            for (;;) {
                cancellation.throwIfCancellationRequested();
                var next = reader.readNext(cancellation);
                if (next.isEmpty()) {
                    cancellation.throwIfCancellationRequested();
                    if (!inputState.ended) {
                        throw new ValidationStreamException("Dataset input ended before EndTransfer");
                    }
                    break;
                }
                var event = next.orElseThrow();
                if (event instanceof TransferEvent.StartTransfer) {
                    inputState.namespaces = Map.copyOf(reader.stableIdentityNamespaces());
                }
                boolean selected = accept(inputOrdinal, input, inputState, event, cancellation);
                if (checkLocal && selected) {
                    local.accept(event, cancellation);
                }
            }
            if (checkLocal) {
                local.finishDatasetInput();
                primary = true;
            }
            storage.checkpoint();
            inputs.put(input.id(), inputOrdinal);
            state = State.OPEN;
        } catch (RuntimeException failure) {
            fail();
            throw failure;
        }
    }

    private boolean accept(long input, InputDescriptor descriptor, InputState context, TransferEvent event,
                           CancellationToken cancellation) {
        return switch (event) {
            case TransferEvent.StartTransfer start -> {
                for (var declaration : start.models()) {
                    requireSnapshot(declaration.model());
                    var model = plan.transferPlan().model(declaration.model()).orElseThrow(
                            () -> new ValidationStreamException("Input model is outside the dataset plan"));
                    if (model.languageVersion() != declaration.languageVersion()
                            || declaration.modelVersion().filter(v -> !v.equals(model.modelVersion())).isPresent()) {
                        throw new ValidationStreamException("Input model version differs from the dataset plan");
                    }
                }
                storage.table(StorageArea.METADATA).put(metadataKey((byte) 2, input), codec.encodeInput(descriptor, start, context.namespaces));
                yield true;
            }
            case TransferEvent.StartBasket start -> {
                requireTopic(start.topic());
                start.additionalTopics().forEach(this::requireTopic);
                for (var binding : start.domainBindings()) {
                    requireSnapshot(binding.generic());
                    requireSnapshot(binding.concrete());
                    if (plan.domainType(binding.generic().symbol()).isEmpty()
                            || plan.domainType(binding.concrete().symbol()).isEmpty()) {
                        throw new ValidationStreamException("Input domain binding is outside the plan");
                    }
                }
                if (start.kind() != TransferEvent.BasketKind.FULL) {
                    throw new ValidationStreamException("Dataset inputs must contain FULL baskets");
                }
                if (baskets == options.limits().maxBaskets()) {
                    throw new ValidationLimitException("Dataset basket budget exceeded");
                }
                completeDelivery &= start.consistency() == TransferEvent.Consistency.COMPLETE;
                context.basket = start;
                context.basketOrdinal = ++baskets;
                var metadata = storage.table(StorageArea.METADATA);
                var encoded = codec.encodeBasket(input, start);
                metadata.put(metadataKey((byte) 3, context.basketOrdinal), encoded);
                yield true;
            }
            case TransferEvent.ObjectEvent occurrence -> {
                if (occurrence.operation().isPresent()) {
                    throw new ValidationStreamException("Dataset inputs must not contain incremental object operations: "
                            + occurrence.operation().orElseThrow());
                }
                var object = occurrence.object();
                requireSnapshot(object.type());
                if (context.basket == null || plan.object(object.type()) == null) {
                    throw new ValidationStreamException("Dataset object is outside the plan or basket");
                }
                checkHandles(object.data(), cancellation);
                if (objects == options.limits().maxObjects()) {
                    throw new ValidationLimitException("Dataset object budget exceeded");
                }
                var handle = new ObjectHandle(++objects);
                objectStore.add(handle, new BasketHandle(input, context.basketOrdinal), occurrence);
                object.identity().tid().ifPresent(tid -> identities.addTemporary(input, tid.value(), handle));
                object.identity().oid().ifPresent(oid -> {
                    var key = StableIdentity.bind(context.namespaces, oid.value());
                    identities.addStable(key.namespace(), key.value(), handle);
                });
                if (occurrence.consistency() != TransferEvent.Consistency.COMPLETE) {
                    completeDelivery = false;
                    storage.table(StorageArea.TEMPORARY).put(metadataKey((byte) 9, context.basketOrdinal), new byte[0]);
                }
                yield plan.subjects().selects(descriptor.role(), context.basket);
            }
            case TransferEvent.EndBasket ignored -> {
                context.basket = null;
                yield true;
            }
            case TransferEvent.EndTransfer ignored -> {
                context.ended = true;
                yield true;
            }
        };
    }

    private void checkHandles(TransferValue.Structured root, CancellationToken cancellation) {
        var pending = new ArrayDeque<java.util.Iterator<? extends TransferValue>>();
        pending.push(List.of(root).iterator());
        long visits = 0;
        while (!pending.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            var iterator = pending.peek();
            if (!iterator.hasNext()) {
                pending.pop();
                continue;
            }
            if (++visits > options.localOptions().maxValuesPerObject()
                    || pending.size() > options.localOptions().maxNestedDepth() + 1L) {
                throw new ValidationLimitException("Dataset value traversal budget exceeded");
            }
            switch (iterator.next()) {
                case TransferValue.Structured data -> {
                    requireSnapshot(data.type());
                    var type = plan.object(data.type());
                    if (type == null) {
                        throw new ValidationStreamException("Dataset value type is outside the plan");
                    }
                    for (var field : data.values().entrySet()) {
                        cancellation.throwIfCancellationRequested();
                        var slot = field.getKey();
                        requireSnapshot(slot);
                        if (type.descriptor().slots().stream().noneMatch(candidate -> candidate.symbol().equals(slot))
                                && !embeddedField(slot, field.getValue())) {
                            throw new ValidationStreamException("Dataset value slot is outside its type");
                        }
                    }
                    pending.push(data.values().values().iterator());
                }
                case TransferValue.Repeated data -> pending.push(data.values().iterator());
                default -> { }
            }
        }
    }

    private boolean embeddedField(SymbolRef slot, TransferValue value) {
        return plan.symbol(slot.symbol()).orElse(null) instanceof guru.interlis.model.RoleSymbol
                && (value instanceof TransferValue.Reference
                    || value instanceof TransferValue.Structured structure
                        && plan.object(structure.type()) != null
                        && plan.object(structure.type()).descriptor().kind() == guru.interlis.model.SymbolKind.ASSOCIATION);
    }

    /** Seals all inputs and returns coverage for the checks actually performed. */
    public ValidationReport finish(CancellationToken cancellation) {
        requireOpen();
        state = State.SEALED;
        try {
            Objects.requireNonNull(cancellation, "cancellation").throwIfCancellationRequested();
            if (!primary) {
                throw new ValidationStreamException("Dataset requires a primary input");
            }
            storage.checkpoint();
            new DatasetReferences(plan, storage, codec, objectStore, identities, options, completeDelivery, local).check(cancellation);
            new RelationCardinalities(plan, storage, codec, objectStore, identities, options, completeDelivery, local).check(cancellation);
            var context = new DatasetRuleContext(plan, storage, codec, objectStore, identities, options,
                    completeDelivery, cancellation);
            context.checkDeferredConstraints(local);
            new ViewExecutor(context).check(local);
            var coverage = checkDatasetRules(context);
            cancellation.throwIfCancellationRequested();
            storage.checkpoint();
            var report = local.finishDataset(coverage, options.scope());
            cancellation.throwIfCancellationRequested();
            inputs.clear();
            state = State.FINISHED;
            return report;
        } catch (RuntimeException failure) {
            fail();
            throw failure;
        }
    }

    private List<DatasetRuleCoverage> checkDatasetRules(DatasetRuleContext context) {
        var uniqueGroups = new LinkedHashMap<SymbolId, List<DatasetRulePlan>>();
        for (var rule : plan.datasetRules()) {
            context.visit();
            if (rule.declaration().definition() instanceof UniquenessConstraint) {
                var canonical = plan.canonical(rule.key().declaration().symbol());
                uniqueGroups.computeIfAbsent(canonical, ignored -> new ArrayList<>()).add(rule);
            }
        }
        var coverage = new HashMap<RuleKey, List<DatasetRuleCoverage>>();
        int ruleOrdinal = 0;
        for (var rule : plan.datasetRules()) {
            context.visit();
            int ordinal = ruleOrdinal++;
            switch (rule.declaration().definition()) {
                case UniquenessConstraint ignored -> {
                    var group = uniqueGroups.remove(plan.canonical(rule.key().declaration().symbol()));
                    if (group != null) {
                        coverage.put(rule.key(), new DatasetUnique(context, local, group, ordinal).check());
                    }
                }
                case ExistenceConstraint existence -> coverage.put(rule.key(),
                        List.of(new DatasetExistence(context, local, rule, existence, ordinal).check()));
                default -> { }
            }
        }
        ruleOrdinal = 0;
        for (var rule : plan.datasetRules()) {
            context.visit();
            int ordinal = ruleOrdinal++;
            switch (rule.declaration().definition()) {
                case SetConstraint set -> coverage.put(rule.key(), new DatasetAggregate(context, local, rule, ordinal).check(set));
                case PlausibilityConstraint plausibility ->
                        coverage.put(rule.key(), new DatasetAggregate(context, local, rule, ordinal).check(plausibility));
                default -> { }
            }
        }
        return plan.datasetRules().stream().flatMap(rule -> coverage.getOrDefault(rule.key(), List.of()).stream()).toList();
    }

    private void requireTopic(SymbolRef topic) {
        requireSnapshot(topic);
        if (plan.transferPlan().topic(topic).isEmpty()) {
            throw new ValidationStreamException("Input topic is outside the dataset plan");
        }
    }

    private void requireSnapshot(SymbolRef ref) {
        if (!plan.snapshotId().equals(ref.snapshot())) {
            throw new ValidationStreamException("Input belongs to another snapshot");
        }
    }

    static byte[] metadataKey(byte tag, long value) {
        return ByteBuffer.allocate(1 + Long.BYTES).put(tag).putLong(value).array();
    }

    private void requireOpen() {
        if (state != State.OPEN) {
            throw new ValidationStreamException("Dataset session is " + state);
        }
    }

    private void fail() {
        inputs.clear();
        local.close();
        state = State.FAILED;
    }

    @Override
    public void close() {
        inputs.clear();
        local.close();
        state = State.CLOSED;
    }

    private static final class InputState {
        private TransferEvent.StartBasket basket;
        private long basketOrdinal;
        private boolean ended;
        private Map<String, guru.interlis.model.QualifiedName> namespaces = Map.of();
    }
}
