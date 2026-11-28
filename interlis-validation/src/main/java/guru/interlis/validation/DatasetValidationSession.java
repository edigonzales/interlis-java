package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ExistenceConstraint;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.SetConstraint;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private final LinkedHashSet<SymbolRef> primaryTopics;
    private final Set<SymbolRef> deliveredObjectTypes = new HashSet<>();
    private final Set<SymbolRef> selectedObjectTypes = new HashSet<>();
    private boolean hasReferencesOrLinks;
    private boolean hasDeferredChecks;
    private boolean hasRequiredAssociations;
    private State state = State.OPEN;
    private long baskets;
    private long objects;
    private boolean primary;
    private Findings terminalFindings;

    /** Violations observed so far, with immutable, bounded diagnostic examples. */
    public record Findings(long detectedViolations, List<ValidationDiagnostic> examples) {
        public Findings {
            examples = List.copyOf(examples);
            if (detectedViolations < examples.size()) throw new IllegalArgumentException("Inconsistent findings");
        }
    }

    /**
     * Returns findings from the checks already executed, including after a failed read or finish.
     * The count may be a lower bound until finish succeeds. This is not a completed report.
     * @throws IllegalStateException if this session has been closed
     */
    public Findings findings() {
        if (state == State.CLOSED) throw new IllegalStateException("Dataset session is closed");
        return terminalFindings == null ? local.findings() : terminalFindings;
    }

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
        this.primaryTopics = new LinkedHashSet<>(plan.subjects().rootTopics());
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
                    inputState.xtfEncoding = Objects.requireNonNull(reader.xtfEncoding(), "reader XTF encoding");
                }
                var selected = accept(inputOrdinal, input, inputState, event, cancellation);
                if (checkLocal && selected.isPresent()) {
                    local.acceptPrepared(selected.orElseThrow(), cancellation);
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

    private Optional<TransferEvent> accept(long input, InputDescriptor descriptor, InputState context, TransferEvent event,
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
                storage.table(StorageArea.METADATA).put(metadataKey((byte) 2, input),
                        codec.encodeInput(descriptor, start, context.namespaces, context.xtfEncoding));
                yield Optional.of(event);
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
                if (plan.subjects().selects(descriptor.role(), start)) {
                    primaryTopics.add(start.topic());
                    primaryTopics.addAll(start.additionalTopics());
                }
                if (descriptor.role() == InputRole.PRIMARY && !hasRequiredAssociations) {
                    hasRequiredAssociations = RelationCardinalities.hasRequiredAssociations(plan, start);
                }
                completeDelivery &= start.consistency() == TransferEvent.Consistency.COMPLETE;
                context.basket = start;
                context.validationBasket = ValidationSession.BasketContext.of(start, plan);
                context.basketOrdinal = ++baskets;
                var metadata = storage.table(StorageArea.METADATA);
                var encoded = codec.encodeBasket(input, start);
                metadata.put(metadataKey((byte) 3, context.basketOrdinal), encoded);
                yield Optional.of(event);
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
                boolean selected = plan.subjects().selects(descriptor.role(), context.basket);
                var projection = checkHandles(object.data(), selected, cancellation);
                deliveredObjectTypes.add(object.type());
                if (selected) {
                    selectedObjectTypes.add(object.type());
                }
                if (objects == options.limits().maxObjects()) {
                    throw new ValidationLimitException("Dataset object budget exceeded");
                }
                var handle = new ObjectHandle(++objects);
                var prepared = NumericRounding.prepare(occurrence, plan, context.validationBasket, options.localOptions(), cancellation);
                objectStore.add(handle, new BasketHandle(input, context.basketOrdinal), prepared, projection);
                object.identity().tid().ifPresent(tid -> identities.addTemporary(input, tid.value(), handle));
                object.identity().oid().ifPresent(oid -> {
                    var key = StableIdentity.bind(context.namespaces, oid.value());
                    identities.addStable(key.namespace(), key.value(), handle);
                });
                if (occurrence.consistency() != TransferEvent.Consistency.COMPLETE) {
                    completeDelivery = false;
                    storage.table(StorageArea.TEMPORARY).put(metadataKey((byte) 9, context.basketOrdinal), new byte[0]);
                }
                yield selected ? Optional.of(prepared) : Optional.empty();
            }
            case TransferEvent.EndBasket ignored -> {
                context.basket = null;
                context.validationBasket = null;
                yield Optional.of(event);
            }
            case TransferEvent.EndTransfer ignored -> {
                context.ended = true;
                yield Optional.of(event);
            }
        };
    }

    private Set<SymbolRef> checkHandles(TransferValue.Structured root, boolean selected, CancellationToken cancellation) {
        var projection = options.localOptions().maxNestedDepth() <= StoredValueCodec.MAX_DEPTH
                ? plan.retention().slots(root.type()) : null;
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
            var value = iterator.next();
            if (value instanceof TransferValue.Structured data) {
                requireSnapshot(data.type());
                var type = plan.object(data.type());
                if (type == null) {
                    throw new ValidationStreamException("Dataset value type is outside the plan");
                }
                if (projection != null && plan.retention().slots(data.type()) == null) {
                    projection = null;
                }
                if (selected && !hasDeferredChecks && plan.objectMayHaveDeferredChecks(data.type())) {
                    hasDeferredChecks = true;
                }
                if (!hasReferencesOrLinks && type.descriptor().kind() == SymbolKind.ASSOCIATION) {
                    hasReferencesOrLinks = true;
                }
                for (var field : data.values().entrySet()) {
                    cancellation.throwIfCancellationRequested();
                    var slot = field.getKey();
                    requireSnapshot(slot);
                    if (type.descriptor().slots().stream().noneMatch(candidate -> candidate.symbol().equals(slot))) {
                        if (!embeddedField(slot, field.getValue())) {
                            throw new ValidationStreamException("Dataset value slot is outside its type");
                        }
                        hasReferencesOrLinks = true;
                        projection = null;
                    }
                }
            } else if (value instanceof TransferValue.Reference) {
                hasReferencesOrLinks = true;
                projection = null;
            }
            var children = ValidationSession.valueChildren(value, options.localOptions(), cancellation);
            if (children != null) {
                // The stored geometry format also nests curve parts and their ordinates.
                if (projection != null && value instanceof TransferGeometry
                        && (pending.size() > StoredValueCodec.MAX_DEPTH - 4 || children.hasNext())) {
                    projection = null;
                }
                pending.push(children);
            }
        }
        return projection;
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
            new DatasetReferences(plan, storage, codec, objectStore, identities, options, completeDelivery, local)
                    .check(hasReferencesOrLinks, cancellation);
            new RelationCardinalities(plan, storage, codec, objectStore, identities, options, completeDelivery, local)
                    .check(hasRequiredAssociations, cancellation);
            var context = new DatasetRuleContext(plan, storage, codec, objectStore, identities, options,
                    completeDelivery, List.copyOf(primaryTopics), cancellation);
            if (hasDeferredChecks) {
                context.checkDeferredConstraints(local);
            }
            new ViewExecutor(context).check(local);
            var coverage = new ArrayList<>(checkDatasetRules(context));
            coverage.addAll(new GeometryScopeRunner(context).check(local, selectedObjectTypes));
            cancellation.throwIfCancellationRequested();
            storage.checkpoint();
            var report = local.finishDataset(coverage, options.scope());
            terminalFindings = new Findings(report.detectedViolations(), report.examples());
            cancellation.throwIfCancellationRequested();
            inputs.clear();
            primaryTopics.clear();
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
                        coverage.put(rule.key(), new DatasetUnique(context, local, group, ordinal).check(deliveredObjectTypes, hasReferencesOrLinks));
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
        if (terminalFindings == null) terminalFindings = local.findings();
        inputs.clear();
        primaryTopics.clear();
        local.close();
        state = State.FAILED;
    }

    @Override
    public void close() {
        terminalFindings = null;
        inputs.clear();
        primaryTopics.clear();
        local.close();
        state = State.CLOSED;
    }

    private static final class InputState {
        private TransferEvent.StartBasket basket;
        private ValidationSession.BasketContext validationBasket;
        private long basketOrdinal;
        private boolean ended;
        private Map<String, guru.interlis.model.QualifiedName> namespaces = Map.of();
        private Optional<TransferReader.XtfEncoding> xtfEncoding = Optional.empty();
    }
}
