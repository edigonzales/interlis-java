package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathStep;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final Set<InputId> inputs;
    private final Set<InputId> primaryInputs;
    private final CancellationToken cancellation;
    private long remainingWork;

    DatasetRuleContext(ValidationPlan plan, ValidationStorage storage, StoredValueCodec codec,
                       DatasetObjectStore objects, IdentityIndex identities, DatasetOptions options,
                       Set<InputId> inputs, CancellationToken cancellation) {
        this.plan = plan;
        this.keys = storage.table(StorageArea.RULE_KEYS);
        this.temporary = storage.table(StorageArea.TEMPORARY);
        this.metadata = storage.table(StorageArea.METADATA);
        this.codec = codec;
        this.objects = objects;
        this.resolver = new ReferenceResolver(plan, identities, objects);
        this.options = options;
        this.inputs = Set.copyOf(inputs);
        this.cancellation = cancellation;
        this.remainingWork = options.limits().maxConstraintWork();
        var primary = new HashSet<InputId>();
        for (long ordinal = 1; ordinal <= inputs.size(); ordinal++) {
            cancellation.throwIfCancellationRequested();
            var input = input(ordinal);
            if (input.descriptor().role() == InputRole.PRIMARY) {
                primary.add(input.descriptor().id());
            }
        }
        this.primaryInputs = Set.copyOf(primary);
    }

    void visit() {
        cancellation.throwIfCancellationRequested();
        if (remainingWork-- == 0) {
            throw new ValidationLimitException("Dataset constraint work limit exceeded");
        }
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
        var topics = new ArrayList<>(basket.event().additionalTopics());
        topics.addFirst(basket.event().topic());
        boolean selected = topics.stream().anyMatch(topic -> options.subjects().selects(plan,
                input.descriptor().role(), topic, stored.event().object().type()));
        return new Occurrence(handle, stored, basket.event(), input, selected);
    }

    private StoredValueCodec.StoredInput input(long ordinal) {
        return metadata.get(DatasetValidationSession.metadataKey((byte) 2, ordinal))
                .map(codec::decodeInput).orElseThrow();
    }

    void initializeScopes(DatasetRulePlan rule, boolean basketRule, boolean requiresValues,
                          BiConsumer<Long, DatasetRuleCoverage.Completeness> consumer) {
        var root = rule.key().applicationRoot();
        if (!basketRule) {
            boolean selected = plan.transferPlan().topics().stream()
                    .anyMatch(topic -> selectsPopulation(rule, List.of(topic.symbol())));
            if (selected) {
                boolean complete = claimsComplete(root, ExtentAspect.OBJECT_MEMBERSHIP, primaryInputs, Optional.empty())
                        && (!requiresValues || claimsComplete(root, ExtentAspect.ATTRIBUTE_VALUES,
                                primaryInputs, Optional.empty()));
                consumer.accept(0L, complete ? DatasetRuleCoverage.Completeness.DECLARED_COMPLETE
                        : DatasetRuleCoverage.Completeness.OPEN);
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
                var completeness = DatasetRuleCoverage.Completeness.OPEN;
                if (basket.event().consistency() == TransferEvent.Consistency.COMPLETE) {
                    completeness = DatasetRuleCoverage.Completeness.CLOSED_BY_TRANSFER;
                } else {
                    var ids = Set.of(input.descriptor().id());
                    var id = Optional.of(basket.event().id());
                    if (claimsComplete(root, ExtentAspect.OBJECT_MEMBERSHIP, ids, id)
                            && (!requiresValues || claimsComplete(root, ExtentAspect.ATTRIBUTE_VALUES, ids, id))) {
                        completeness = DatasetRuleCoverage.Completeness.DECLARED_COMPLETE;
                    }
                }
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
                        && options.subjects().selects(plan, InputRole.PRIMARY, topic, type)) {
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
        if (basket.isEmpty() || basket.orElseThrow().event().consistency() != TransferEvent.Consistency.COMPLETE) {
            var expected = basket.map(entry -> Set.of(input(entry.input()).descriptor().id())).orElse(primaryInputs);
            var basketId = basket.map(entry -> entry.event().id());
            if (roots.isEmpty() || roots.stream().anyMatch(root ->
                    !claimsComplete(root, ExtentAspect.OBJECT_MEMBERSHIP, expected, basketId))) {
                return java.util.OptionalLong.empty();
            }
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
        return declaredComplete(root, ExtentAspect.OBJECT_MEMBERSHIP)
                && declaredComplete(root, ExtentAspect.ATTRIBUTE_VALUES);
    }

    private boolean declaredComplete(SymbolRef root, ExtentAspect aspect) {
        return claimsComplete(root, aspect, inputs, Optional.empty());
    }

    private boolean claimsComplete(SymbolRef root, ExtentAspect aspect, Set<InputId> expected,
                                   Optional<guru.interlis.transfer.TransferIdentity.BasketId> basket) {
        var descriptor = plan.transferPlan().object(root).orElseThrow();
        return options.extents().claims().stream().anyMatch(claim -> {
            var scope = claim.selector();
            if (claim.aspect() != aspect || !scope.inputs().containsAll(expected)
                    || scope.basket().isPresent() && !scope.basket().equals(basket)) {
                return false;
            }
            return plan.transferPlan().topic(scope.root()).isPresent()
                    ? descriptor.topic().filter(topic -> topicSpecializes(topic, scope.root())).isPresent()
                    : ModelValueRules.specializes(plan, descriptor, List.of(plan.canonical(scope.root().symbol())));
        });
    }

    Optional<ExpressionProgram> program(ExpressionId id) {
        return plan.datasetExpression(id);
    }

    EvaluationResult evaluate(ExpressionProgram program, Occurrence occurrence) {
        return program.evaluate(new EvaluationFrame(plan, occurrence.object().data(),
                occurrence.partial(), this::visit, java.util.OptionalLong.empty(), cancellation)
                .withDataset(new DatasetPathContext(this, occurrence)));
    }

    void checkDeferredConstraints(ValidationSession report) {
        if (!plan.hasDeferredPaths()) {
            return;
        }
        forObjects(occurrence -> {
            if (!occurrence.selected() || occurrence.input().descriptor().role() != InputRole.PRIMARY) {
                return;
            }
            var object = plan.object(occurrence.object().type());
            var frame = new EvaluationFrame(plan, occurrence.object().data(), occurrence.partial(), this::visit,
                    java.util.OptionalLong.empty(), cancellation).withDataset(new DatasetPathContext(this, occurrence));
            for (var field : object.fields()) {
                if (!field.derived() || !plan.derivedNeedsDataset(field.slot().symbol().symbol())) {
                    continue;
                }
                var attribute = field.slot().attribute();
                var result = DerivedValueResolver.resolve(attribute, frame);
                var type = frame.effectiveType(field.values().getFirst());
                report.recordDeferredDerived(occurrence, field, result, type);
            }
            for (var declaration : object.constraints()) {
                if (declaration.definition() instanceof guru.interlis.model.MandatoryConstraint mandatory) {
                    var program = plan.expression(mandatory.condition());
                    if (plan.requiresDataset(program)) {
                        report.recordDatasetConstraint(occurrence, declaration, evaluate(program, occurrence));
                    }
                }
            }
        });
    }

    List<ObjectHandle> relationTargets(ObjectHandle source, SymbolId sourceRole, SymbolId targetRole) {
        return new RelationPaths(temporary).targets(source, sourceRole, targetRole, this::visit);
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
                    return result instanceof ReferenceResolver.Found found ? Optional.of(found.target()) : Optional.empty();
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
