package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathStep;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

final class DatasetRuleContext {
    final ValidationPlan plan;
    final ValidationTable keys;
    private final DatasetObjectStore objects;
    private final ValidationTable metadata;
    private final StoredValueCodec codec;
    private final ReferenceResolver resolver;
    private final DatasetOptions options;
    private final Set<InputId> inputs;
    private final CancellationToken cancellation;
    private long remainingWork;

    DatasetRuleContext(ValidationPlan plan, ValidationStorage storage, StoredValueCodec codec,
                       DatasetObjectStore objects, IdentityIndex identities, DatasetOptions options,
                       Set<InputId> inputs, CancellationToken cancellation) {
        this.plan = plan;
        this.keys = storage.table(StorageArea.RULE_KEYS);
        this.metadata = storage.table(StorageArea.METADATA);
        this.codec = codec;
        this.objects = objects;
        this.resolver = new ReferenceResolver(plan, identities, objects);
        this.options = options;
        this.inputs = Set.copyOf(inputs);
        this.cancellation = cancellation;
        this.remainingWork = options.limits().maxConstraintWork();
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
        var input = metadata.get(DatasetValidationSession.metadataKey((byte) 2, stored.input()))
                .map(codec::decodeInput).orElseThrow();
        var topics = new ArrayList<>(basket.event().additionalTopics());
        topics.addFirst(basket.event().topic());
        boolean selected = topics.stream().anyMatch(topic -> options.subjects().selects(plan,
                input.descriptor().role(), topic, stored.event().object().type()));
        return new Occurrence(handle, stored, basket.event(), input, selected);
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
        return ModelValueRules.specializes(plan, type, List.of(root.symbol()));
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
        var descriptor = plan.transferPlan().object(root).orElseThrow();
        return options.extents().claims().stream().anyMatch(claim -> {
            var scope = claim.selector();
            if (claim.aspect() != aspect || scope.basket().isPresent() || !scope.inputs().equals(inputs)) {
                return false;
            }
            return plan.transferPlan().topic(scope.root()).isPresent()
                    ? descriptor.topic().filter(topic -> topicSpecializes(topic, scope.root())).isPresent()
                    : ModelValueRules.specializes(plan, descriptor, List.of(scope.root().symbol()));
        });
    }

    Optional<LocalExpressions.Program> program(ExpressionId id) {
        return plan.datasetExpression(id);
    }

    LocalExpressions.Result evaluate(LocalExpressions.Program program, Occurrence occurrence) {
        return program.evaluate(new LocalExpressions.Context(plan, occurrence.object().data(),
                occurrence.partial(), this::visit));
    }

    TypeDescriptor valueType(LocalExpressions.Program program, Occurrence occurrence) {
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
