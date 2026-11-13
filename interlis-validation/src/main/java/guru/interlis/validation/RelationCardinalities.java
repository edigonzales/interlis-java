package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.Cardinality;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Evaluates counterpart cardinalities over stored, resolved association occurrences. */
final class RelationCardinalities {
    private final ValidationPlan plan;
    private final ValidationTable metadata;
    private final ValidationTable links;
    private final ValidationTable temporary;
    private final StoredValueCodec codec;
    private final DatasetObjectStore objects;
    private final ReferenceResolver resolver;
    private final DatasetOptions options;
    private final ValidationSession report;
    private final RelationGroups groups;
    private final RelationOrdering ordering;
    private final RelationPaths paths;
    private final CompositionGraph composition;
    private long remainingWork;

    RelationCardinalities(ValidationPlan plan, ValidationStorage storage, StoredValueCodec codec,
                          DatasetObjectStore objects, IdentityIndex identities, DatasetOptions options,
                          ValidationSession report) {
        this.plan = plan;
        this.metadata = storage.table(StorageArea.METADATA);
        this.links = storage.table(StorageArea.LINKS);
        this.temporary = storage.table(StorageArea.TEMPORARY);
        this.codec = codec;
        this.objects = objects;
        this.resolver = new ReferenceResolver(plan, identities, objects);
        this.options = options;
        this.report = report;
        this.groups = new RelationGroups(storage);
        this.ordering = new RelationOrdering(storage);
        this.paths = new RelationPaths(temporary);
        this.composition = new CompositionGraph(storage);
        this.remainingWork = options.limits().maxRelationWork();
    }

    void check(CancellationToken cancellation) {
        try (var cursor = links.scan(new byte[0], Optional.empty())) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                cancellation.throwIfCancellationRequested();
                var entry = next.orElseThrow();
                observe(codec.decodeLink(entry.value()), new ObjectHandle(ByteBuffer.wrap(entry.key()).getLong()),
                        cancellation);
            }
        }
        seedGroups(cancellation);
        groups.groups(cancellation, this::checkGroup);
        ordering.check(cancellation, this::completeOrderedRelation, this::reportFinding);
        composition.check(cancellation, this::reportFinding);
    }

    private void observe(RelationProjector.Link link, ObjectHandle occurrence, CancellationToken cancellation) {
        var source = objects.get(link.source());
        var basket = basket(source.basket());
        var input = input(source.input());
        var endpoints = new ArrayList<ObjectHandle>();
        for (var endpoint : link.endpoints()) {
            spendWork(cancellation);
            var target = endpoint.implicitTarget();
            if (target.isEmpty() && endpoint.reference().isPresent()) {
                var role = (RoleSymbol) plan.symbol(endpoint.role().symbol()).orElseThrow();
                var result = resolver.resolve(new BasketHandle(source.input(), source.basket()), basket,
                        input.namespaces(), endpoint.reference().orElseThrow(), role.targetRoots(), role.external(), false);
                if (result instanceof ReferenceResolver.Found found) {
                    target = Optional.of(found.target());
                }
            }
            if (target.isEmpty()) {
                temporary.put(DatasetValidationSession.metadataKey((byte) 4, source.basket()), new byte[0]);
                return;
            }
            endpoints.add(target.orElseThrow());
        }

        var association = plan.transferPlan().object(link.association()).orElseThrow();
        for (var current = Optional.of(association); current.isPresent();
             current = current.orElseThrow().baseType().flatMap(plan.transferPlan()::object)) {
            var descriptor = current.orElseThrow();
            var roles = roles(descriptor);
            if (roles.size() != endpoints.size()) {
                throw new ValidationStreamException("Association arity changed along its inheritance chain");
            }
            for (int i = 0; i < roles.size(); i++) {
                var counterparts = new ArrayList<>(endpoints);
                var target = counterparts.remove(i);
                var role = roles.get(i).role();
                groups.observe(role.id(), counterparts, target);
                if (role.ordered()) {
                    ordering.observe(role, counterparts, source.basket(), link.source(), occurrence,
                            link.endpoints().get(i).reference().flatMap(TransferValue.Reference::orderPosition));
                }
                if (role.aggregation() == RoleAggregation.COMPOSITION) {
                    composition.observe(counterparts.getFirst(), target, role.id());
                }
            }
            if (plan.hasRelationPaths()) {
                for (int sourceRole = 0; sourceRole < roles.size(); sourceRole++) {
                    for (int targetRole = 0; targetRole < roles.size(); targetRole++) {
                        spendWork(cancellation);
                        paths.observe(endpoints.get(sourceRole), roles.get(sourceRole).role().id(),
                                roles.get(targetRole).role().id(), sourceRole == targetRole ? link.source()
                                        : endpoints.get(targetRole), occurrence,
                                roles.get(targetRole).role().ordered() ? link.endpoints().get(targetRole).reference()
                                        .flatMap(TransferValue.Reference::orderPosition) : Optional.empty());
                    }
                }
            }
            if (descriptor.associationCardinality().isPresent()) {
                groups.observe(descriptor.symbol().symbol(), endpoints, occurrence);
            }
        }
    }

    private void seedGroups(CancellationToken cancellation) {
        long currentBasket = 0;
        long first = 0;
        long last = 0;
        try (var cursor = objects.scan()) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                cancellation.throwIfCancellationRequested();
                var entry = next.orElseThrow();
                var stored = objects.decode(entry);
                last = DatasetObjectStore.handle(entry).ordinal();
                if (stored.basket() != currentBasket) {
                    seedBasket(currentBasket, first, last, cancellation);
                    currentBasket = stored.basket();
                    first = last;
                }
            }
        }
        seedBasket(currentBasket, first, Math.incrementExact(last), cancellation);
    }

    private List<ObjectDescriptor> associations(TransferEvent.StartBasket basket) {
        var candidates = new LinkedHashSet<SymbolRef>();
        var topics = new ArrayList<>(basket.additionalTopics());
        topics.addFirst(basket.topic());
        for (var ref : topics) {
            candidates.addAll(plan.transferPlan().topic(ref).orElseThrow().objectTypes());
        }
        return candidates.stream().map(ref -> plan.transferPlan().object(ref).orElseThrow())
                .filter(descriptor -> descriptor.kind() == SymbolKind.ASSOCIATION)
                .filter(descriptor -> ((AssociationSymbol) plan.symbol(descriptor.symbol().symbol()).orElseThrow())
                        .derivedFrom().isEmpty())
                .filter(descriptor -> descriptor.associationCardinality().filter(c -> c.lower().signum() > 0).isPresent()
                        || roles(descriptor).stream().anyMatch(role -> role.role().cardinality().lower().signum() > 0))
                .toList();
    }

    private void seedBasket(long ordinal, long first, long end, CancellationToken cancellation) {
        if (ordinal == 0) {
            return;
        }
        var header = basket(ordinal);
        var inputOrdinal = objects.get(new ObjectHandle(first)).input();
        if (input(inputOrdinal).descriptor().role() != InputRole.PRIMARY) {
            return;
        }
        int nextMembership = 0;
        for (var association : associations(header)) {
            var roles = roles(association);
            var participants = new ArrayList<Integer>();
            for (var role : roles) {
                int membership = nextMembership++;
                participants.add(membership);
                try (var cursor = objects.scan(new ObjectHandle(first), end)) {
                    for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                        spendWork(cancellation);
                        var entry = next.orElseThrow();
                        var member = objects.decode(entry).event().object();
                        var type = plan.transferPlan().object(member.type()).orElseThrow();
                        if (ModelValueRules.specializes(plan, type, role.role().targetRoots())) {
                            temporary.put(memberKey(ordinal, membership, DatasetObjectStore.handle(entry).ordinal()),
                                    new byte[0]);
                        }
                    }
                }
            }
            for (int i = 0; i < roles.size(); i++) {
                if (roles.get(i).role().cardinality().lower().signum() > 0) {
                    var counterparts = new ArrayList<>(participants);
                    counterparts.remove(i);
                    seedTuples(ordinal, roles.get(i).role().id(), counterparts, cancellation);
                }
            }
            if (association.associationCardinality().filter(c -> c.lower().signum() > 0).isPresent()) {
                seedTuples(ordinal, association.symbol().symbol(), participants, cancellation);
            }
        }
    }

    private void seedTuples(long basket, SymbolId declaration, List<Integer> memberships,
                            CancellationToken cancellation) {
        var positions = new long[memberships.size()];
        int depth = 0;
        while (depth >= 0) {
            spendWork(cancellation);
            int membership = memberships.get(depth);
            var start = memberKey(basket, membership, Math.incrementExact(positions[depth]));
            var end = memberKey(basket, Math.incrementExact(membership), 0);
            Optional<EntryCursor.Entry> next;
            try (var cursor = temporary.scan(start, Optional.of(end))) {
                next = cursor.next();
            }
            if (next.isEmpty()) {
                positions[depth--] = 0;
                continue;
            }
            positions[depth] = ByteBuffer.wrap(next.orElseThrow().key()).getLong(1 + Long.BYTES + Integer.BYTES);
            if (depth + 1 == positions.length) {
                var tuple = new ArrayList<ObjectHandle>(positions.length);
                for (long position : positions) {
                    tuple.add(new ObjectHandle(position));
                }
                groups.expect(declaration, tuple);
            } else {
                depth++;
            }
        }
    }

    private static byte[] memberKey(long basket, int membership, long handle) {
        return ByteBuffer.allocate(1 + 2 * Long.BYTES + Integer.BYTES).put((byte) 5)
                .putLong(basket).putInt(membership).putLong(handle).array();
    }

    private void spendWork(CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (remainingWork-- == 0) {
            throw new ValidationLimitException("Dataset relationship work limit exceeded");
        }
    }

    private void checkGroup(RelationGroups.Group group) {
        if (group.counterparts().isEmpty()) {
            return;
        }
        var declaration = plan.symbol(group.role()).orElseThrow();
        var association = declaration instanceof RoleSymbol role
                ? role.header().owner().map(plan::ref).flatMap(plan.transferPlan()::object).orElseThrow()
                : plan.transferPlan().object(plan.ref(group.role())).orElseThrow();
        Cardinality bounds = declaration instanceof RoleSymbol role
                ? role.cardinality() : association.associationCardinality().orElseThrow();
        StoredValueCodec.StoredObject representative = null;
        boolean complete = roles(association).stream().noneMatch(slot -> slot.role().external());
        for (var counterpart : group.counterparts()) {
            var stored = objects.get(counterpart);
            var input = input(stored.input());
            var basket = basket(stored.basket());
            if (selected(input, basket, stored.event().object().type())
                    && representative == null) {
                representative = stored;
            }
            complete &= completeRelation(stored.basket(), input.descriptor().id(), basket, association);
        }
        if (representative == null) {
            return;
        }
        var object = representative.event().object();
        var id = object.identity();
        var source = input(representative.input()).header().source();
        var path = declaration instanceof RoleSymbol ? List.<DataLocation.Step>of(new DataLocation.Slot(plan.ref(group.role())))
                : List.<DataLocation.Step>of();
        var data = new DataLocation(id.source().or(() -> source), id.currentBasket(), id.tid(), id.oid(),
                Optional.of(object.type()), path);
        report.relationCardinality(object, declaration, data, group.targets(), bounds, complete);
    }

    private boolean completeOrderedRelation(long ordinal, SymbolId declaration) {
        var stored = codec.decodeBasket(metadata.get(DatasetValidationSession.metadataKey((byte) 3, ordinal))
                .orElseThrow());
        var association = plan.symbol(declaration).orElseThrow().owner()
                .map(plan::ref).flatMap(plan.transferPlan()::object).orElseThrow();
        return completeRelation(ordinal, input(stored.input()).descriptor().id(), stored.event(), association);
    }

    private boolean completeRelation(long ordinal, InputId input, TransferEvent.StartBasket basket,
                                     ObjectDescriptor association) {
        if (temporary.get(DatasetValidationSession.metadataKey((byte) 4, ordinal)).isPresent()) {
            return false;
        }
        if (basket.consistency() == TransferEvent.Consistency.COMPLETE) {
            return true;
        }
        return options.extents().claims().stream().anyMatch(claim ->
                claim.aspect() == ExtentAspect.RELATION_MEMBERSHIP
                        && (claim.selector().root().equals(association.symbol())
                            || claim.selector().root().equals(basket.topic())
                            || basket.additionalTopics().contains(claim.selector().root()))
                        && claim.selector().inputs().contains(input)
                        && claim.selector().basket().map(basket.id()::equals).orElse(true));
    }

    private void reportFinding(ObjectHandle source, List<ObjectHandle> counterparts, SymbolId role,
                               Optional<ValidationCode> violation, boolean complete, List<String> arguments) {
        var stored = objects.get(source);
        var input = input(stored.input());
        var object = stored.event().object();
        var basket = basket(stored.basket());
        if (selected(input, basket, object.type()) || counterparts.stream().anyMatch(this::selected)) {
            var id = object.identity();
            var data = new DataLocation(id.source().or(() -> input.header().source()), id.currentBasket(),
                    id.tid(), id.oid(), Optional.of(object.type()), List.of(new DataLocation.Slot(plan.ref(role))));
            report.relationFinding(object, plan.symbol(role).orElseThrow(), data, violation, complete, arguments);
        }
    }

    private boolean selected(ObjectHandle handle) {
        var stored = objects.get(handle);
        return selected(input(stored.input()), basket(stored.basket()), stored.event().object().type());
    }

    private boolean selected(StoredValueCodec.StoredInput input, TransferEvent.StartBasket basket,
                             SymbolRef type) {
        var subjects = options.subjects();
        var role = input.descriptor().role();
        return subjects.selects(plan, role, basket.topic(), type)
                || basket.additionalTopics().stream().anyMatch(topic -> subjects.selects(plan, role, topic, type));
    }

    private StoredValueCodec.StoredInput input(long ordinal) {
        return codec.decodeInput(metadata.get(DatasetValidationSession.metadataKey((byte) 2, ordinal)).orElseThrow());
    }

    private TransferEvent.StartBasket basket(long ordinal) {
        return codec.decodeBasket(metadata.get(DatasetValidationSession.metadataKey((byte) 3, ordinal)).orElseThrow()).event();
    }

    private static List<RoleSlot> roles(ObjectDescriptor association) {
        return association.slots().stream().filter(RoleSlot.class::isInstance).map(RoleSlot.class::cast).toList();
    }
}
