package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.OidRule;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferValue.Structured;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Projects decoded export events through the model's checked inheritance. */
final class DbBaseProjection {
    private record ProjectedRole(SymbolRef targetType, String role, String sourceTid, String targetTid) { }

    private final DbMapping mapping;
    private final TopicDescriptor topic;
    private final Map<SymbolRef, DbMapping.EmbeddedAssociation> roles = new HashMap<>();
    private final Map<String, SymbolRef> projectedObjects = new HashMap<>();
    private final List<ProjectedRole> references = new ArrayList<>();

    DbBaseProjection(DbMapping mapping, TopicDescriptor topic) {
        this.mapping = mapping;
        this.topic = topic;
        for (var association : mapping.associations()) {
            var owner = association.ownerRole();
            var target = association.targetRole();
            if (!association.linkTable() && !owner.single() && target.single()
                    && owner.targets().size() == 1 && target.targets().size() == 1
                    && !owner.external() && !target.external() && !association.ordered()
                    && !association.role().role().ordered()
                    && association.object().baseType().isEmpty()
                    && association.object().topic().filter(declared -> belongsTo(mapping.plan(), declared, topic)).isPresent()) {
                roles.put(association.role().symbol(), association);
            }
        }
    }

    static TopicDescriptor topic(TransferPlan plan, TopicDescriptor source, SymbolRef model) {
        for (var candidate = source; candidate != null; candidate = baseTopic(plan, candidate)) {
            if (candidate.model().equals(model) && !candidate.abstractTopic()) {
                var sourceOid = source.oidRules().stream().filter(OidRule::basket).findFirst().flatMap(OidRule::domain);
                var outputOid = candidate.oidRules().stream().filter(OidRule::basket).findFirst().flatMap(OidRule::domain);
                if (!sourceOid.equals(outputOid)) {
                    throw DbMapping.unsupported("base-model export changes basket identity at " + source.name().declared());
                }
                return candidate;
            }
        }
        throw new DbException("No concrete base topic in " + plan.model(model).orElseThrow().name().declared()
                + " for " + source.name().declared());
    }

    Optional<TransferObject> object(TransferObject object) {
        var plan = mapping.plan();
        var source = plan.object(object.type()).orElseThrow();
        var target = source;
        if (!belongsTo(plan, source.topic().orElseThrow(), topic)) {
            while (target != null && !target.topic().filter(topic.symbol()::equals).isPresent()) {
                target = target.baseType().flatMap(plan::object).orElse(null);
            }
        }
        if (target == null || !target.transferable()) return Optional.empty();
        if (target.kind() != SymbolKind.CLASS) {
            throw DbMapping.unsupported("base-model export of association " + target.name().declared());
        }
        if (!DbMapping.oidDomain(plan, source).equals(DbMapping.oidDomain(plan, target))) {
            throw DbMapping.unsupported("base-model export changes object identity at " + source.name().declared());
        }

        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        for (var field : target.slots()) {
            if (!(field instanceof AttributeSlot attribute) || attribute.attribute().transientAttribute()) continue;
            var value = object.value(field.symbol());
            var type = plan.type(attribute.effectiveType()).known().orElseThrow();
            switch (type.kind()) {
                case TEXT, BOOLEAN, NUMERIC, ENUMERATION, FORMATTED, OID, BLACKBOX -> { }
                case COORDINATE -> {
                    if (((CoordinateType) type).multiCoordinate()) {
                        throw DbMapping.unsupported("base-model export of " + field.name().declared());
                    }
                }
                case STRUCTURE_VALUE, COLLECTION -> {
                    var composition = mapping.compositionsForObject(target.symbol()).stream()
                            .filter(binding -> binding.slot().symbol().equals(field.symbol()))
                            .filter(binding -> binding.collectionKind().orElse(CollectionKind.LIST) == CollectionKind.LIST)
                            .filter(binding -> plan.object(binding.child()).orElseThrow().model().equals(topic.model()))
                            .findFirst();
                    if (composition.isEmpty()) throw DbMapping.unsupported("base-model export of " + field.name().declared());
                    value.ifPresent(structure -> checkStructure(plan, structure, topic.model(), attribute));
                }
                default -> throw DbMapping.unsupported("base-model export of " + field.name().declared());
            }
            value.ifPresent(present -> values.put(field.symbol(), present));
        }
        for (var field : source.slots()) {
            if (field instanceof AttributeSlot attribute && attribute.member().overridden() && target != source
                    && target.slots().stream().anyMatch(base -> base.name().declared().last().equals(field.name().declared().last())
                            && !base.symbol().equals(field.symbol()))) {
                throw DbMapping.unsupported("base-model export of extended attribute " + field.name().declared());
            }
        }
        if (!roles.isEmpty()) {
            projectedObjects.put(object.identity().tid().orElseThrow().value(), target.symbol());
        }
        for (var entry : object.data().values().entrySet()) {
            var field = plan.symbol(entry.getKey()).known().orElseThrow();
            if (field.kind() == SymbolKind.ROLE && field.owner().map(owner -> new SymbolRef(plan.snapshotId(), owner))
                    .flatMap(plan::object).flatMap(association -> association.topic())
                    .filter(declared -> belongsTo(plan, declared, topic)).isPresent()) {
                var association = roles.get(entry.getKey());
                if (association == null || !mapping.isAssignableTo(target.symbol(), association.owner())
                        || !(entry.getValue() instanceof Reference reference)) {
                    throw DbMapping.unsupported("base-model export of role " + field.header().name());
                }
                references.add(new ProjectedRole(association.target(), association.role().name().declared().toString(),
                        object.identity().tid().orElseThrow().value(), reference.target().value()));
                values.put(entry.getKey(), reference);
            }
        }
        return Optional.of(new TransferObject(object.identity(), new Structured(target.symbol(), values)));
    }

    void requireReferenceTargets(BasketId basket, CancellationToken token) {
        for (var reference : references) {
            token.throwIfCancellationRequested();
            var target = projectedObjects.get(reference.targetTid());
            if (target == null || !mapping.isAssignableTo(target, reference.targetType())) {
                throw new DbException("Missing projected target " + reference.targetTid() + " at " + reference.role()
                        + " of object " + reference.sourceTid() + " in basket " + basket.value());
            }
        }
    }

    private static void checkStructure(TransferPlan plan, TransferValue value, SymbolRef model, AttributeSlot field) {
        switch (value) {
            case Structured structure -> {
                var type = plan.object(structure.type()).orElseThrow();
                if (!type.model().equals(model)) {
                    throw DbMapping.unsupported("base-model export of structure " + type.name().declared()
                            + " at " + field.name().declared());
                }
                structure.values().values().forEach(part -> checkStructure(plan, part, model, field));
            }
            case TransferValue.Repeated elements ->
                    elements.values().forEach(part -> checkStructure(plan, part, model, field));
            case TransferValue.Reference ignored ->
                    throw DbMapping.unsupported("base-model export of reference in " + field.name().declared());
            default -> { }
        }
    }

    private static boolean belongsTo(TransferPlan plan, SymbolRef declaredTopic, TopicDescriptor output) {
        for (var candidate = output; candidate != null; candidate = baseTopic(plan, candidate)) {
            if (candidate.symbol().equals(declaredTopic)) return true;
        }
        return false;
    }

    private static TopicDescriptor baseTopic(TransferPlan plan, TopicDescriptor topic) {
        return topic.baseTopic().flatMap(plan::topic).orElse(null);
    }
}
