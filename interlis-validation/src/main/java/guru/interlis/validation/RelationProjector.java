package guru.interlis.validation;

import guru.interlis.model.RoleSymbol;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferValue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Projects neutral standalone and embedded links into ordered association endpoints. */
final class RelationProjector {
    private final ValidationPlan plan;

    RelationProjector(ValidationPlan plan) {
        this.plan = plan;
    }

    Link standalone(ObjectHandle occurrence, TransferValue.Structured value) {
        var association = association(value.type()).orElseThrow();
        var endpoints = roles(association).stream().map(role ->
                new Endpoint(role.symbol(), value.value(role.symbol())
                        .filter(TransferValue.Reference.class::isInstance).map(TransferValue.Reference.class::cast),
                        Optional.empty())).toList();
        return new Link(occurrence, association.symbol(), Optional.empty(), endpoints);
    }

    Optional<Link> embedded(ObjectHandle owner, ObjectDescriptor ownerType, SymbolRef slot,
                            TransferValue value, TransferEvent.StartBasket basket) {
        var declaration = plan.symbol(slot.symbol()).filter(RoleSymbol.class::isInstance)
                .map(RoleSymbol.class::cast);
        var declaredAssociation = declaration.flatMap(role -> role.header().owner()).map(plan::ref)
                .flatMap(this::association);
        if (declaredAssociation.isEmpty()) {
            return Optional.empty();
        }
        var root = declaredAssociation.orElseThrow();
        var wireRole = roles(root).stream().filter(role -> role.symbol().equals(slot)).findFirst().orElseThrow();
        List<ObjectDescriptor> candidates;
        if (value instanceof TransferValue.Structured payload) {
            candidates = association(payload.type()).stream().toList();
        } else if (value instanceof TransferValue.Reference) {
            var types = new LinkedHashSet<SymbolRef>();
            var topics = new ArrayList<>(basket.additionalTopics());
            topics.addFirst(basket.topic());
            for (var topic : topics) {
                plan.transferPlan().topic(topic).ifPresent(descriptor -> types.addAll(descriptor.objectTypes()));
            }
            candidates = types.stream().flatMap(type -> association(type).stream()).toList();
        } else {
            return Optional.empty();
        }
        var matches = new ArrayList<Link>();
        for (var candidate : candidates) {
            if (!ModelValueRules.specializes(plan, candidate, List.of(root.symbol().symbol()))) {
                continue;
            }
            var roles = roles(candidate);
            if (roles.size() != 2) {
                continue;
            }
            var opposite = roles.stream().filter(role -> role.member().position() == wireRole.member().position())
                    .findFirst().orElse(null);
            if (opposite == null) {
                continue;
            }
            var owning = roles.getFirst().equals(opposite) ? roles.getLast() : roles.getFirst();
            if (!ModelValueRules.specializes(plan, ownerType, owning.role().targetRoots())) {
                continue;
            }
            var reference = switch (value) {
                case TransferValue.Reference ref -> Optional.of(ref);
                case TransferValue.Structured payload -> payload.value(opposite.symbol())
                        .filter(TransferValue.Reference.class::isInstance).map(TransferValue.Reference.class::cast);
                default -> Optional.<TransferValue.Reference>empty();
            };
            var endpoints = roles.stream().map(role -> role.equals(owning)
                    ? new Endpoint(role.symbol(), Optional.empty(), Optional.of(owner))
                    : new Endpoint(role.symbol(), reference, Optional.empty())).toList();
            matches.add(new Link(owner, candidate.symbol(), Optional.of(slot), endpoints));
            if (matches.size() > 1) {
                return Optional.empty();
            }
        }
        return matches.stream().findFirst();
    }

    private Optional<ObjectDescriptor> association(SymbolRef type) {
        return plan.transferPlan().object(type).filter(descriptor -> descriptor.kind() == SymbolKind.ASSOCIATION);
    }

    private static List<RoleSlot> roles(ObjectDescriptor type) {
        return type.slots().stream().filter(RoleSlot.class::isInstance).map(RoleSlot.class::cast).toList();
    }

    record Link(ObjectHandle source, SymbolRef association, Optional<SymbolRef> embeddedField,
                List<Endpoint> endpoints) {
        Link {
            endpoints = List.copyOf(endpoints);
        }
    }

    record Endpoint(SymbolRef role, Optional<TransferValue.Reference> reference,
                    Optional<ObjectHandle> implicitTarget) { }
}
