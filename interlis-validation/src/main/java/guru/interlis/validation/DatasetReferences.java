package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Visits the retained values once all input identities are known. */
final class DatasetReferences {
    private final ValidationPlan plan;
    private final ValidationTable metadata;
    private final StoredValueCodec codec;
    private final DatasetObjectStore objects;
    private final DatasetOptions options;
    private final Set<InputId> inputs;
    private final ValidationSession report;
    private final ReferenceResolver resolver;
    private final IdentityIndex identities;
    private final RelationProjector relations;
    private final ValidationTable links;
    private final ValidationTable incompleteLinks;
    private long linkOrdinal;

    DatasetReferences(ValidationPlan plan, ValidationStorage storage, StoredValueCodec codec,
                      DatasetObjectStore objects, IdentityIndex identities, DatasetOptions options,
                      Set<InputId> inputs, ValidationSession report) {
        this.plan = plan;
        this.metadata = storage.table(StorageArea.METADATA);
        this.codec = codec;
        this.objects = objects;
        this.options = options;
        this.inputs = inputs;
        this.report = report;
        this.resolver = new ReferenceResolver(plan, identities, objects);
        this.identities = identities;
        this.relations = new RelationProjector(plan);
        this.links = storage.table(StorageArea.LINKS);
        this.incompleteLinks = storage.table(StorageArea.TEMPORARY);
    }

    void check(CancellationToken cancellation) {
        identities.checkStableCollisions(cancellation, this::identityConflict);
        long lastInput = 0;
        long lastBasket = 0;
        StoredValueCodec.StoredInput input = null;
        TransferEvent.StartBasket basket = null;
        try (var cursor = objects.scan()) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                cancellation.throwIfCancellationRequested();
                var entry = next.orElseThrow();
                var stored = objects.decode(entry);
                if (stored.input() != lastInput) {
                    lastInput = stored.input();
                    input = readInput(lastInput);
                }
                if (stored.basket() != lastBasket) {
                    lastBasket = stored.basket();
                    basket = codec.decodeBasket(metadata.get(DatasetValidationSession.metadataKey((byte) 3, lastBasket))
                            .orElseThrow()).event();
                }
                var object = stored.event().object();
                var role = input.descriptor().role();
                var currentBasket = basket;
                boolean selected = options.subjects().selects(plan, role, basket.topic(), object.type())
                        || basket.additionalTopics().stream()
                                .anyMatch(topic -> options.subjects().selects(plan, role, topic, object.type()));
                var context = new Context(new BasketHandle(lastInput, lastBasket), input, currentBasket, object, cancellation);
                projectLinks(DatasetObjectStore.handle(entry), context, selected);
                if (selected) {
                    fields(context, object.data(), new ArrayList<>());
                }
            }
        }
    }

    private void identityConflict(ObjectHandle first, ObjectHandle duplicate) {
        var original = objects.get(first);
        var stored = objects.get(duplicate);
        var input = readInput(stored.input());
        var other = readInput(original.input());
        var object = stored.event().object();
        var id = object.identity();
        var data = new DataLocation(id.source().or(() -> input.header().source()), id.currentBasket(),
                id.tid(), id.oid(), Optional.of(object.type()), List.of());
        report.identityConflict(object, data,
                List.of(other.descriptor().id().value(), original.event().object().identity().currentBasket().value(),
                        input.descriptor().id().value(), id.currentBasket().value()));
    }

    private StoredValueCodec.StoredInput readInput(long ordinal) {
        return codec.decodeInput(metadata.get(DatasetValidationSession.metadataKey((byte) 2, ordinal)).orElseThrow());
    }

    private void projectLinks(ObjectHandle handle, Context context, boolean selected) {
        var object = context.object();
        var type = plan.transferPlan().object(object.type()).orElseThrow();
        if (type.kind() == guru.interlis.model.SymbolKind.ASSOCIATION) {
            storeLink(relations.standalone(handle, object.data()));
        }
        for (var field : object.data().values().entrySet()) {
            context.cancellation().throwIfCancellationRequested();
            if (type.slots().stream().anyMatch(slot -> slot.symbol().equals(field.getKey()))) {
                continue;
            }
            var link = relations.embedded(handle, type, field.getKey(), field.getValue(), context.header());
            link.ifPresent(this::storeLink);
            if (link.isEmpty() && (field.getValue() instanceof TransferValue.Reference
                    || field.getValue() instanceof TransferValue.Structured)) {
                incompleteLinks.put(DatasetValidationSession.metadataKey((byte) 4, context.basket().ordinal()), new byte[0]);
            }
            if (selected && field.getValue() instanceof TransferValue.Reference reference) {
                var path = List.<DataLocation.Step>of(new DataLocation.Slot(field.getKey()));
                if (link.isEmpty()) {
                    var role = plan.symbol(field.getKey().symbol()).orElseThrow();
                    report.referenceResult(object, role, location(context, path), ReferenceResolver.Missing.OPEN);
                } else {
                    for (var endpoint : link.orElseThrow().endpoints()) {
                        if (endpoint.reference().isPresent()) {
                            var role = (RoleSymbol) plan.symbol(endpoint.role().symbol()).orElseThrow();
                            resolve(context, reference, role, role.targetRoots(), role.external(), path);
                        }
                    }
                }
            }
        }
    }

    private void storeLink(RelationProjector.Link link) {
        byte[] key = java.nio.ByteBuffer.allocate(Long.BYTES).putLong(++linkOrdinal).array();
        links.put(key, codec.encodeLink(link));
    }

    private void fields(Context context, TransferValue.Structured structured, ArrayList<DataLocation.Step> path) {
        var descriptor = plan.transferPlan().object(structured.type()).orElseThrow();
        for (var entry : structured.values().entrySet()) {
            context.cancellation().throwIfCancellationRequested();
            var field = entry.getKey();
            if (entry.getValue() instanceof TransferValue.Reference
                    && descriptor.slots().stream().noneMatch(slot -> slot.symbol().equals(field))) {
                continue;
            }
            var symbol = plan.symbol(field.symbol()).orElseThrow();
            var attribute = descriptor.slots().stream().filter(slot -> slot.symbol().equals(field))
                    .filter(AttributeSlot.class::isInstance).map(AttributeSlot.class::cast).findFirst();
            var type = attribute.flatMap(slot -> plan.effectiveType(slot.declaredType().type()));
            path.add(new DataLocation.Slot(field));
            value(context, entry.getValue(), symbol, type, path);
            path.removeLast();
        }
    }

    private void value(Context context, TransferValue value, Symbol slot, Optional<TypeDescriptor> type,
                       ArrayList<DataLocation.Step> path) {
        context.cancellation().throwIfCancellationRequested();
        switch (value) {
            case TransferValue.Reference reference -> {
                if (slot instanceof RoleSymbol role) {
                    resolve(context, reference, slot, role.targetRoots(), role.external(), path);
                } else if (type.orElse(null) instanceof ReferenceType target) {
                    resolve(context, reference, slot, target.targetRoots(), target.external(), path);
                }
            }
            case TransferValue.Structured structured -> fields(context, structured, path);
            case TransferValue.Repeated repeated -> {
                var element = type.filter(CollectionType.class::isInstance).map(CollectionType.class::cast)
                        .flatMap(collection -> plan.effectiveType(collection.elementType()));
                for (int i = 0; i < repeated.values().size(); i++) {
                    path.add(new DataLocation.Index(i + 1));
                    value(context, repeated.values().get(i), slot, element, path);
                    path.removeLast();
                }
            }
            default -> { }
        }
    }

    private void resolve(Context context, TransferValue.Reference reference, Symbol slot, List<SymbolId> roots,
                         boolean external, List<DataLocation.Step> path) {
        boolean closed = !external && context.header().consistency() == TransferEvent.Consistency.COMPLETE
                || explicitlyClosed(context, reference, roots);
        var result = resolver.resolve(context.basket(), context.header(), context.input().namespaces(),
                reference, roots, external, closed);
        report.referenceResult(context.object(), slot, location(context, path), result);
    }

    private boolean explicitlyClosed(Context context, TransferValue.Reference reference, List<SymbolId> roots) {
        if (roots.isEmpty()) {
            return false;
        }
        Set<InputId> required = reference.target() instanceof guru.interlis.transfer.TransferIdentity.TransferId
                ? Set.of(context.input().descriptor().id()) : inputs;
        return roots.stream().allMatch(root -> options.extents().claims().stream().anyMatch(claim ->
                claim.aspect() == ExtentAspect.OBJECT_MEMBERSHIP && claim.selector().inputs().containsAll(required)
                        && claim.selector().root().equals(plan.ref(root))
                        && (claim.selector().basket().isEmpty() || reference.targetBasket()
                            .map(id -> id.value().equals(claim.selector().basket().orElseThrow().value())).orElse(false))));
    }

    private static DataLocation location(Context context, List<DataLocation.Step> path) {
        var id = context.object().identity();
        return new DataLocation(id.source().or(() -> context.input().header().source()),
                id.currentBasket(), id.tid(), id.oid(), Optional.of(context.object().type()), path);
    }

    private record Context(BasketHandle basket, StoredValueCodec.StoredInput input, TransferEvent.StartBasket header,
                           TransferObject object, CancellationToken cancellation) { }
}
