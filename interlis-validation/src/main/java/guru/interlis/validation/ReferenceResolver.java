package guru.interlis.validation;

import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolId;
import guru.interlis.transfer.TransferIdentity.ObjectOid;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.model.SymbolRef;
import java.util.ArrayList;
import java.util.HashSet;

import java.util.List;
import java.util.Map;

/** Resolves a reference against the sealed occurrence index. */
final class ReferenceResolver {
    private final ValidationPlan plan;
    private final IdentityIndex identities;
    private final DatasetObjectStore objects;

    ReferenceResolver(ValidationPlan plan, IdentityIndex identities, DatasetObjectStore objects) {
        this.plan = plan;
        this.identities = identities;
        this.objects = objects;
    }

    Result resolve(BasketHandle source, StartBasket sourceBasket, Map<String, QualifiedName> namespaces, Reference reference,
                   List<SymbolId> allowedRoots, boolean external, boolean closedExtent) {
        var candidates = switch (reference.target()) {
            case TransferId tid -> identities.temporaryTarget(source.input(), tid.value());
            case ObjectOid oid -> {
                var identity = StableIdentity.bind(namespaces, oid.value());
                yield identities.stableTarget(identity.namespace(), identity.value());
            }
        };
        return switch (candidates) {
            case IdentityIndex.Missing ignored -> closedExtent ? Missing.CLOSED : Missing.OPEN;
            case IdentityIndex.Ambiguous conflict -> new Ambiguous(conflict.first(), conflict.second());
            case IdentityIndex.Found candidate -> check(source, sourceBasket, reference, allowedRoots, external, candidate.handle());
        };
    }

    private Result check(BasketHandle source, StartBasket sourceBasket, Reference reference, List<SymbolId> allowedRoots,
                         boolean external, ObjectHandle handle) {
        var target = objects.get(handle);
        var object = target.event().object();
        var descriptor = plan.transferPlan().object(object.type()).orElseThrow();
        if (!ModelValueRules.specializes(plan, descriptor, allowedRoots)) {
            return new InvalidTarget(handle, Reason.TYPE);
        }
        boolean sameBasket = source.input() == target.input() && source.ordinal() == target.basket();
        if ((!sameBasket && (!external || descriptor.topic().filter(topic -> !topicReachable(sourceBasket, topic)).isPresent()))
                || reference.targetBasket()
                .filter(basket -> !basket.value().equals(object.identity().currentBasket().value())).isPresent()) {
            return new InvalidTarget(handle, Reason.BASKET);
        }
        return new Found(handle);
    }

    private boolean topicReachable(StartBasket source, SymbolRef target) {
        var allowed = new HashSet<SymbolRef>();
        var active = new ArrayList<>(source.additionalTopics());
        active.addFirst(source.topic());
        for (var ref : active) {
            var topic = plan.transferPlan().topic(ref);
            while (topic.isPresent()) {
                var current = topic.orElseThrow();
                allowed.add(current.symbol());
                allowed.addAll(current.dependencies());
                topic = current.baseTopic().flatMap(plan.transferPlan()::topic);
            }
        }
        var candidate = plan.transferPlan().topic(target);
        while (candidate.isPresent()) {
            var current = candidate.orElseThrow();
            if (allowed.contains(current.symbol())) {
                return true;
            }
            candidate = current.baseTopic().flatMap(plan.transferPlan()::topic);
        }
        return false;
    }

    sealed interface Result permits Found, Missing, Ambiguous, InvalidTarget { }

    record Found(ObjectHandle target) implements Result { }

    enum Missing implements Result { OPEN, CLOSED }

    record Ambiguous(ObjectHandle first, ObjectHandle second) implements Result { }

    record InvalidTarget(ObjectHandle target, Reason reason) implements Result { }

    enum Reason { TYPE, BASKET }
}
