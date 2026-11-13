package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionType;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathStep;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;
import guru.interlis.validation.ValidationSession.ValuePath;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class DatasetRuleContext {
    final ValidationPlan plan;
    final ValidationTable keys;
    final ValidationTable temporary;
    private final DatasetObjectStore objects;
    private final ValidationTable metadata;
    private final StoredValueCodec codec;
    private final ReferenceResolver resolver;
    private final DatasetOptions options;
    private final boolean closedDelivery;
    private final CancellationToken cancellation;
    private long remainingWork;

    DatasetRuleContext(ValidationPlan plan, ValidationStorage storage, StoredValueCodec codec,
                       DatasetObjectStore objects, IdentityIndex identities, DatasetOptions options,
                       boolean completeDelivery, CancellationToken cancellation) {
        this.plan = plan;
        this.keys = storage.table(StorageArea.RULE_KEYS);
        this.temporary = storage.table(StorageArea.TEMPORARY);
        this.metadata = storage.table(StorageArea.METADATA);
        this.codec = codec;
        this.objects = objects;
        this.resolver = new ReferenceResolver(plan, identities, objects);
        this.options = options;
        this.closedDelivery = completeDelivery && options.scope() == DatasetScope.SUPPLIED_DATASET;
        this.cancellation = cancellation;
        this.remainingWork = options.limits().maxConstraintWork();
    }

    boolean populationClosed(SymbolRef root) {
        return closedDelivery && plan.transferPlan().object(root).isPresent();
    }

    boolean geometryPopulationClosed() {
        return closedDelivery;
    }

    EvaluationResult geometryDerivation(Occurrence occurrence, guru.interlis.model.AttributeSymbol attribute) {
        var frame = new EvaluationFrame(plan, occurrence.object().data(), occurrence.partial(), this::visit,
                java.util.OptionalLong.empty(), cancellation).withDataset(new DatasetPathContext(this, occurrence));
        return DerivedValueResolver.resolve(attribute, frame);
    }

    boolean selectsView(guru.interlis.model.ViewSymbol view) {
        return plan.subjects().selectsView(view);
    }

    void visit() {
        cancellation.throwIfCancellationRequested();
        if (remainingWork-- == 0) {
            throw new ValidationLimitException("Dataset constraint work limit exceeded");
        }
    }

    GeometryEngine.Cursor geometryCursor(java.util.function.Function<Occurrence, List<GeometryEngine.Input>> select) {
        var entries = objects.scan();
        return new GeometryEngine.Cursor() {
            private java.util.Iterator<GeometryEngine.Input> values = java.util.Collections.emptyIterator();

            @Override public Optional<GeometryEngine.Input> next() {
                while (!values.hasNext()) {
                    var entry = entries.next();
                    if (entry.isEmpty()) {
                        return Optional.empty();
                    }
                    visit();
                    var value = entry.orElseThrow();
                    values = select.apply(occurrence(DatasetObjectStore.handle(value), objects.decode(value))).iterator();
                }
                return Optional.of(values.next());
            }

            @Override public void close() {
                entries.close();
            }
        };
    }

    void forObjects(Consumer<Occurrence> consumer) {
        try (var cursor = objects.scan()) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                visit();
                var entry = next.orElseThrow();
                consumer.accept(occurrence(DatasetObjectStore.handle(entry), objects.decode(entry)));
            }
        }
    }

    Occurrence occurrence(ObjectHandle handle) {
        return occurrence(handle, objects.get(handle));
    }

    private Occurrence occurrence(ObjectHandle handle, StoredValueCodec.StoredObject stored) {
        var basket = metadata.get(DatasetValidationSession.metadataKey((byte) 3, stored.basket()))
                .map(codec::decodeBasket).orElseThrow();
        var input = input(stored.input());
        boolean selected = plan.subjects().selects(input.descriptor().role(), basket.event());
        return new Occurrence(handle, stored, basket.event(), input, selected);
    }

    private StoredValueCodec.StoredInput input(long ordinal) {
        return metadata.get(DatasetValidationSession.metadataKey((byte) 2, ordinal))
                .map(codec::decodeInput).orElseThrow();
    }

    void initializeScopes(DatasetRulePlan rule, boolean basketRule,
                          BiConsumer<Long, DatasetScopeStore.Completeness> consumer) {
        var root = rule.key().applicationRoot();
        if (!basketRule) {
            boolean selected = plan.transferPlan().topics().stream()
                    .anyMatch(topic -> selectsPopulation(rule, List.of(topic.symbol())));
            if (selected) {
                consumer.accept(0L, closedDelivery ? DatasetScopeStore.Completeness.CLOSED_DELIVERY
                        : DatasetScopeStore.Completeness.OPEN);
            }
            return;
        }
        try (var cursor = metadata.scan(new byte[]{3}, Optional.of(new byte[]{4}))) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                visit();
                var entry = next.orElseThrow();
                long ordinal = ByteBuffer.wrap(entry.key()).getLong(1);
                var basket = codec.decodeBasket(entry.value());
                var input = input(basket.input());
                if (input.descriptor().role() != InputRole.PRIMARY) {
                    continue;
                }
                var active = new ArrayList<>(basket.event().additionalTopics());
                active.addFirst(basket.event().topic());
                if (!selectsPopulation(rule, active)) {
                    continue;
                }
                var completeness = basketClosed(ordinal, basket.event())
                        ? DatasetScopeStore.Completeness.CLOSED_BY_TRANSFER : DatasetScopeStore.Completeness.OPEN;
                consumer.accept(ordinal, completeness);
            }
        }
    }

    DataLocation scopeLocation(DatasetRulePlan rule, long ordinal) {
        var root = rule.key().applicationRoot();
        if (ordinal == 0) {
            return new DataLocation(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(root), List.of());
        }
        var basket = metadata.get(DatasetValidationSession.metadataKey((byte) 3, ordinal))
                .map(codec::decodeBasket).orElseThrow();
        return new DataLocation(input(basket.input()).header().source(), basket.event().id(),
                Optional.empty(), Optional.empty(), Optional.of(root), List.of());
    }

    private boolean selectsPopulation(DatasetRulePlan rule, List<SymbolRef> active) {
        if (rule.declaration().target().isPresent()) {
            var declaringTopic = rule.declaration().owner().map(plan::ref)
                    .filter(topic -> plan.transferPlan().topic(topic).isPresent());
            if (declaringTopic.isPresent() && active.stream().noneMatch(
                    topic -> topicSpecializes(topic, declaringTopic.orElseThrow()))) {
                return false;
            }
        }
        for (var topic : active) {
            visit();
            var descriptor = plan.transferPlan().topic(topic).orElseThrow();
            for (var type : descriptor.objectTypes()) {
                if ((rule.candidateTypes().contains(type) || rule.key().applicationRoot().equals(type))
                        && plan.subjects().selectsTopic(topic)) {
                    return true;
                }
            }
        }
        return false;
    }

    java.util.OptionalLong countPopulation(long basketOrdinal, guru.interlis.model.ObjectArgumentType type) {
        var roots = type.restrictions().isEmpty()
                ? type.base().map(plan::ref).stream().toList() : type.restrictions().stream().map(plan::ref).toList();
        var basket = basketOrdinal == 0 ? Optional.<StoredValueCodec.StoredBasket>empty()
                : metadata.get(DatasetValidationSession.metadataKey((byte) 3, basketOrdinal)).map(codec::decodeBasket);
        if (basket.map(entry -> !basketClosed(basketOrdinal, entry.event())).orElse(!closedDelivery)) {
            return java.util.OptionalLong.empty();
        }
        var counts = new DatasetScopeCounts();
        forObjects(occurrence -> {
            if (occurrence.input().descriptor().role() == InputRole.PRIMARY && occurrence.selected()
                    && (basketOrdinal == 0 || occurrence.stored().basket() == basketOrdinal)
                    && (roots.isEmpty() || roots.stream().anyMatch(root -> target(root, occurrence)))) {
                counts.candidates++;
            }
        });
        return java.util.OptionalLong.of(counts.candidates);
    }

    boolean candidate(DatasetRulePlan rule, Occurrence occurrence) {
        if (!rule.candidateTypes().contains(occurrence.object().type())) {
            return false;
        }
        return rule.declaration().target().isEmpty()
                || rule.declaration().owner().map(plan::ref).filter(topic -> plan.transferPlan().topic(topic).isPresent())
                    .map(topic -> topicActive(occurrence.basket(), topic)).orElse(true);
    }

    boolean target(SymbolRef root, Occurrence occurrence) {
        var type = plan.transferPlan().object(occurrence.object().type()).orElseThrow();
        return ModelValueRules.specializes(plan, type, List.of(plan.canonical(root.symbol())));
    }

    private boolean topicActive(TransferEvent.StartBasket basket, SymbolRef topic) {
        var active = new ArrayList<>(basket.additionalTopics());
        active.addFirst(basket.topic());
        return active.stream().anyMatch(ref -> topicSpecializes(ref, topic));
    }

    private boolean topicSpecializes(SymbolRef actual, SymbolRef ancestor) {
        var current = Optional.of(actual);
        while (current.isPresent()) {
            if (plan.canonical(current.orElseThrow().symbol()).equals(plan.canonical(ancestor.symbol()))) {
                return true;
            }
            current = current.flatMap(plan.transferPlan()::topic).flatMap(topic -> topic.baseTopic());
        }
        return false;
    }

    boolean declaredComplete(SymbolRef root) {
        return populationClosed(root);
    }

    private boolean basketClosed(long ordinal, TransferEvent.StartBasket basket) {
        return basket.consistency() == TransferEvent.Consistency.COMPLETE
                && temporary.get(DatasetValidationSession.metadataKey((byte) 9, ordinal)).isEmpty();
    }

    Optional<ExpressionProgram> program(ExpressionId id) {
        return plan.datasetExpression(id);
    }

    EvaluationResult evaluate(ExpressionProgram program, Occurrence occurrence) {
        return program.evaluate(new EvaluationFrame(plan, occurrence.object().data(),
                occurrence.partial(), this::visit, java.util.OptionalLong.empty(), cancellation)
                .withDataset(new DatasetPathContext(this, occurrence)));
    }

    EvaluationResult evaluateKey(ExpressionProgram program, Occurrence occurrence) {
        var frame = new EvaluationFrame(plan, occurrence.object().data(), occurrence.partial(), this::visit,
                java.util.OptionalLong.empty(), cancellation).withDataset(new DatasetPathContext(this, occurrence));
        return new ExpressionEvaluator(program).evaluateKey(frame);
    }

    void checkDeferredConstraints(ValidationSession report) {
        if (!plan.hasDeferredPaths()) {
            return;
        }
        var relations = new RelationProjector(plan);
        forObjects(occurrence -> {
            if (!occurrence.selected() || occurrence.input().descriptor().role() != InputRole.PRIMARY
                    || occurrence.stored().event().operation().orElse(null) == TransferEvent.Operation.DELETE) {
                return;
            }
            var root = occurrence.object().data();
            var object = plan.object(root.type());
            var basket = ValidationSession.BasketContext.of(occurrence.basket(), plan);
            var frame = new EvaluationFrame(plan, root, occurrence.partial(), this::visit,
                    java.util.OptionalLong.empty(), cancellation).withDataset(new DatasetPathContext(this, occurrence));
            checkDeferredObject(report, occurrence, basket, root, object, frame, null);
            for (var entry : root.values().entrySet()) {
                visit();
                if (entry.getValue() instanceof TransferValue.Structured payload) {
                    relations.embedded(occurrence.handle(), object.descriptor(), entry.getKey(), payload, occurrence.basket())
                            .ifPresent(link -> checkDeferredObject(report, occurrence, basket, payload,
                                    plan.object(payload.type()), frame.withDataset(new DatasetPathContext(this, occurrence, payload, link))
                                            .descend(root, payload),
                                    new ValuePath(null, new DataLocation.Slot(entry.getKey()))));
                }
            }
        });
    }

    private void checkDeferredObject(ValidationSession report, Occurrence occurrence,
                                     ValidationSession.BasketContext basket, TransferValue.Structured current,
                                     ObjectValidationPlan object, EvaluationFrame frame, ValuePath path) {
        for (var declaration : object.constraints()) {
            visit();
            if (declaration.definition() instanceof MandatoryConstraint mandatory
                    && ValidationSession.ruleApplies(plan, declaration, basket.topicScope())) {
                var program = plan.expression(mandatory.condition());
                if (plan.requiresDataset(program)) {
                    report.recordDatasetConstraint(occurrence, current.type(), Optional.empty(), declaration,
                            path, program.evaluate(frame));
                }
            }
        }
        for (var field : object.fields()) {
            visit();
            var slot = field.slot().symbol();
            var at = new ValuePath(path, new DataLocation.Slot(slot));
            if (field.derived() && plan.derivedNeedsDataset(slot.symbol())) {
                var result = DerivedValueResolver.resolve(field.slot().attribute(), frame);
                report.recordDeferredDerived(occurrence, field, result, frame.effectiveType(field.values().getFirst()),
                        current, at, this::visit);
            }
            current.value(slot).ifPresent(value ->
                    checkDeferredValue(report, occurrence, basket, current, frame, field, 0, value, at));
        }
    }

    private void checkDeferredValue(ValidationSession report, Occurrence occurrence,
                                    ValidationSession.BasketContext basket, TransferValue.Structured owner,
                                    EvaluationFrame frame, ObjectValidationPlan.FieldRule field,
                                    int depth, TransferValue value, ValuePath path) {
        visit();
        var rules = field.values().get(depth);
        var domain = rules.genericDomain().map(basket.domains()::get);
        var effective = domain.flatMap(plan::domainType).orElse(rules.type());
        if (ValueFamilies.match(effective, value) != ValueFamilies.Match.YES
                || value instanceof TransferValue.Text text && text.value().isEmpty()) {
            return;
        }
        for (var declaration : rules.constraints(domain)) {
            visit();
            if (declaration.definition() instanceof DomainConstraint constraint) {
                var program = plan.expression(constraint.condition());
                if (plan.requiresDataset(program)) {
                    report.recordDatasetConstraint(occurrence, owner.type(), Optional.of(field.slot().symbol()),
                            declaration, path, program.evaluate(frame.withCurrent(value)));
                }
            }
        }
        if (rules.type() instanceof CollectionType collection && value instanceof TransferValue.Repeated repeated
                && collection.collectionKind() == repeated.kind()) {
            for (int index = 0; index < repeated.values().size(); index++) {
                checkDeferredValue(report, occurrence, basket, owner, frame, field, depth + 1,
                        repeated.values().get(index), new ValuePath(path, new DataLocation.Index(index + 1)));
            }
        } else if (rules.type() instanceof StructureValueType structure && value instanceof TransferValue.Structured child) {
            var type = plan.object(child.type());
            if (type != null && plan.assignable(structure, type)) {
                checkDeferredObject(report, occurrence, basket, child, type, frame.descend(owner, child), path);
            }
        }
    }

    EvaluationResult relationTargets(Occurrence source, SymbolId association, SymbolId sourceRole, SymbolId targetRole) {
        var paths = new RelationPaths(temporary);
        if (paths.hasUnresolved(source.handle(), sourceRole, targetRole)) {
            return new EvaluationResult.BlockedByViolation(source.location(List.of(new DataLocation.Slot(plan.ref(sourceRole)))));
        }
        var type = plan.transferPlan().object(plan.ref(association)).orElseThrow();
        var external = type.slots().stream().filter(guru.interlis.transfer.RoleSlot.class::isInstance)
                .map(guru.interlis.transfer.RoleSlot.class::cast).map(guru.interlis.transfer.RoleSlot::role)
                .anyMatch(role -> role.external() && role.targetRoots().stream().map(plan::ref)
                        .anyMatch(root -> !populationClosed(root)));
        if (source.partial() || external) {
            return EvaluationResult.MissingContext.POPULATION;
        }
        return new EvaluationResult.KnownObjects(() -> paths.firstTwo(source.handle(), sourceRole, targetRole, this::visit),
                () -> paths.count(source.handle(), sourceRole, targetRole, this::visit));
    }

    ReferenceResolver.Result resolveRole(Occurrence occurrence, TransferValue.Reference reference,
                                         guru.interlis.model.RoleSymbol role) {
        visit();
        return resolver.resolve(new BasketHandle(occurrence.stored().input(), occurrence.stored().basket()),
                occurrence.basket(), occurrence.input().namespaces(), reference, role.targetRoots(), role.external(), false);
    }

    ReferenceResolver.Result resolve(Occurrence occurrence, TransferValue.Reference reference, ReferenceType type) {
        visit();
        return resolver.resolve(new BasketHandle(occurrence.stored().input(), occurrence.stored().basket()),
                occurrence.basket(), occurrence.input().namespaces(), reference, type.targetRoots(), type.external(), false);
    }

    guru.interlis.execution.CancellationToken cancellation() {
        return cancellation;
    }

    TypeDescriptor valueType(ExpressionProgram program, Occurrence occurrence) {
        var declared = plan.effectiveType(program.expression().typeId()).orElse(null);
        if (!(program.expression() instanceof PathExpression path)) {
            return declared;
        }
        TransferValue current = occurrence.object().data();
        ObjectValidationPlan.FieldRule field = null;
        for (var step : path.path().steps()) {
            if (step instanceof PathStep.ThisStep) {
                current = occurrence.object().data();
                continue;
            }
            if (!(step instanceof PathStep.AttributeStep attribute)
                    || !(current instanceof TransferValue.Structured structure)) {
                return declared;
            }
            var type = plan.object(structure.type());
            if (type == null || (field = plan.field(type, attribute.target())) == null) {
                return declared;
            }
            current = structure.value(field.slot().symbol()).orElse(TransferValue.Undefined.INSTANCE);
        }
        return field == null ? declared : plan.effectiveType(field.slot().effectiveType().type()).orElse(declared);
    }

    ValueSemantics semantics(Occurrence occurrence) {
        return new ValueSemantics(plan, options.limits().maxKeyBytes(),
                occurrence.partial(), plan.symbol(occurrence.object().type().symbol()).orElseThrow().header().languageVersion(),
                this::visit, (reference, type) -> {
                    if (!(type instanceof ReferenceType target)) {
                        return Optional.empty();
                    }
                    var result = resolver.resolve(new BasketHandle(occurrence.stored().input(),
                                    occurrence.stored().basket()), occurrence.basket(), occurrence.input().namespaces(),
                            reference, target.targetRoots(), target.external(), false);
                    if (result instanceof ReferenceResolver.Ambiguous || result instanceof ReferenceResolver.InvalidTarget) {
                        return Optional.empty();
                    }
                    return Optional.of(switch (reference.target()) {
                        case guru.interlis.transfer.TransferIdentity.TransferId tid ->
                                new StableIdentity("tid:" + occurrence.stored().input(), tid.value());
                        case guru.interlis.transfer.TransferIdentity.ObjectOid oid -> {
                            var identity = StableIdentity.bind(occurrence.input().namespaces(), oid.value());
                            yield new StableIdentity("oid:" + identity.namespace(), identity.value());
                        }
                    });
                }, oid -> StableIdentity.bind(occurrence.input().namespaces(), oid));
    }

    byte[] indexKey(byte tag, int rule, long scope, CanonicalKey key) {
        byte[] value = key.bytes();
        return ByteBuffer.allocate(1 + Integer.BYTES + Long.BYTES + value.length)
                .put(tag).putInt(rule).putLong(scope).put(value).array();
    }

    record Occurrence(ObjectHandle handle, StoredValueCodec.StoredObject stored,
                      TransferEvent.StartBasket basket, StoredValueCodec.StoredInput input, boolean selected) {
        TransferObject object() {
            return stored.event().object();
        }

        boolean partial() {
            return basket.consistency() != TransferEvent.Consistency.COMPLETE
                    || stored.event().consistency() != TransferEvent.Consistency.COMPLETE;
        }

        DataLocation location(List<DataLocation.Step> path) {
            var id = object().identity();
            return new DataLocation(id.source().or(() -> input.header().source()), id.currentBasket(),
                    id.tid(), id.oid(), Optional.of(object().type()), path);
        }

        List<String> witness() {
            var id = object().identity();
            return List.of(input.descriptor().id().value(), id.currentBasket().value(),
                    id.oid().map(oid -> oid.value()).or(() -> id.tid().map(tid -> tid.value())).orElse(""));
        }
    }
}
