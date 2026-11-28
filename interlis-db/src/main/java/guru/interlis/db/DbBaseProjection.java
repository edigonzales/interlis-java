package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.GeometryType;
import guru.interlis.model.OidRule;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeId;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferValue.Structured;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Projects decoded export events through the model's checked inheritance. */
final class DbBaseProjection {
    private record ProjectedRole(SymbolRef targetType, String role, String source, String targetTid) { }

    private final DbMapping mapping;
    private final TopicDescriptor topic;
    private final Map<SymbolRef, DbMapping.EmbeddedAssociation> roles = new HashMap<>();
    private final Map<SymbolRef, List<DbMapping.AssociationRole>> links = new HashMap<>();
    private final Map<String, SymbolRef> projectedObjects = new HashMap<>();
    private final List<ProjectedRole> references = new ArrayList<>();
    private final Map<TypeId, Set<String>> enumLeaves = new HashMap<>();

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
        var plan = mapping.plan();
        for (var table : mapping.tables()) {
            var association = table.object();
            if (association.kind() != SymbolKind.ASSOCIATION || table.carriesObjectId()
                    || mapping.isEmbeddedLink(table) || association.baseType().isPresent()
                    || association.topic().filter(declared -> belongsTo(plan, declared, topic)).isEmpty()) {
                continue;
            }
            var ends = DbMapping.describeRoles(plan, association);
            if (ends.size() == 2 && ends.stream().allMatch(end -> !end.single() && end.targets().size() == 1
                    && !end.external() && !end.slot().role().ordered()
                    && end.slot().role().aggregation() == RoleAggregation.NONE)) {
                links.put(association.symbol(), ends);
            }
        }
    }

    static TopicDescriptor topic(TransferPlan plan, TopicDescriptor source, List<SymbolRef> models) {
        for (var candidate = source; candidate != null; candidate = baseTopic(plan, candidate)) {
            if (models.contains(candidate.model()) && !candidate.abstractTopic()) {
                var sourceOid = source.oidRules().stream().filter(OidRule::basket).findFirst().flatMap(OidRule::domain);
                var outputOid = candidate.oidRules().stream().filter(OidRule::basket).findFirst().flatMap(OidRule::domain);
                if (!sourceOid.equals(outputOid)) {
                    throw DbMapping.unsupported("base-model export changes basket identity at " + source.name().declared());
                }
                return candidate;
            }
        }
        var names = models.stream().map(model -> plan.model(model).orElseThrow().name().declared().toString()).toList();
        throw new DbException("No concrete base topic in " + String.join(", ", names) + " for " + source.name().declared());
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
        var linkEnds = links.get(target.symbol());
        if (target.kind() != SymbolKind.CLASS && (target != source || linkEnds == null)) {
            throw DbMapping.unsupported("base-model export of association " + target.name().declared());
        }
        if (!DbMapping.oidDomain(plan, source).equals(DbMapping.oidDomain(plan, target))) {
            throw DbMapping.unsupported("base-model export changes object identity at " + source.name().declared());
        }

        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        var extensions = mapping.extendedFields(source.symbol());
        for (var field : target.slots()) {
            if (!(field instanceof AttributeSlot attribute) || attribute.attribute().transientAttribute()) continue;
            var extension = extensions.get(field.symbol());
            if (extension != null) {
                var extended = source.slots().stream().filter(slot -> slot.symbol().equals(extension))
                        .map(AttributeSlot.class::cast).findFirst().orElseThrow();
                projectEnum(extended, attribute, object).ifPresent(value -> values.put(field.symbol(), value));
                continue;
            }
            var value = object.value(field.symbol());
            var type = plan.type(attribute.effectiveType()).known().orElseThrow();
            switch (type.kind()) {
                case TEXT, BOOLEAN, NUMERIC, ENUMERATION, FORMATTED, OID, BLACKBOX -> { }
                case COORDINATE -> {
                    if (((CoordinateType) type).multiCoordinate()) {
                        throw DbMapping.unsupported("base-model export of " + field.name().declared());
                    }
                }
                case GEOMETRY -> {
                    switch (((GeometryType) type).geometryKind()) {
                        case POLYLINE, SURFACE, AREA -> { }
                        default -> throw DbMapping.unsupported("base-model export of " + field.name().declared());
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
        if (linkEnds != null) {
            for (var end : linkEnds) {
                var role = end.slot();
                if (!(object.value(role.symbol()).orElse(null) instanceof Reference reference)) {
                    throw DbMapping.unsupported("base-model export of link role " + role.name().declared());
                }
                references.add(new ProjectedRole(end.target(), role.name().declared().toString(),
                        "link " + target.name().declared(), reference.target().value()));
                values.put(role.symbol(), reference);
            }
            return Optional.of(new TransferObject(object.identity(), new Structured(target.symbol(), values)));
        }
        if (!roles.isEmpty() || !links.isEmpty()) {
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
                        "object " + object.identity().tid().orElseThrow().value(), reference.target().value()));
                values.put(entry.getKey(), reference);
            }
        }
        return Optional.of(new TransferObject(object.identity(), new Structured(target.symbol(), values)));
    }

    private Optional<TransferValue> projectEnum(AttributeSlot source, AttributeSlot target, TransferObject object) {
        var plan = mapping.plan();
        if (!DbMapping.inlineEnumeration(plan, source) || !DbMapping.inlineEnumeration(plan, target)) {
            throw DbMapping.unsupported("base-model export of extended attribute " + source.name().declared());
        }
        return object.value(source.symbol()).map(value -> {
            var enumeration = (Enumeration) value;
            var path = enumeration.path();
            String code = String.join(".", path);
            if (enumLeaves(source).contains(code)) {
                var targetLeaves = enumLeaves(target);
                for (int length = path.size(); length > 0; length--) {
                    var prefix = path.subList(0, length);
                    if (targetLeaves.contains(String.join(".", prefix))) return new Enumeration(prefix);
                }
            }
            throw new DbException("Cannot project enumeration value " + code + " at " + source.name().declared()
                    + " into " + target.name().declared());
        });
    }

    private Set<String> enumLeaves(AttributeSlot attribute) {
        var type = (EnumerationType) mapping.plan().type(attribute.effectiveType()).known().orElseThrow();
        return enumLeaves.computeIfAbsent(type.id(), id -> DbEnumeration.values(type).stream()
                .map(EnumerationValue::path).collect(Collectors.toUnmodifiableSet()));
    }

    void requireReferenceTargets(BasketId basket, CancellationToken token) {
        for (var reference : references) {
            token.throwIfCancellationRequested();
            var target = projectedObjects.get(reference.targetTid());
            if (target == null || !mapping.isAssignableTo(target, reference.targetType())) {
                throw new DbException("Missing projected target " + reference.targetTid() + " at " + reference.role()
                        + " of " + reference.source() + " in basket " + basket.value());
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
