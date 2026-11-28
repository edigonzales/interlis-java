package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.OidRule;
import guru.interlis.model.ReferenceType;
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
    private record ProjectedReference(SymbolRef targetType, String field, String source, String targetTid) { }

    private final DbMapping mapping;
    private final TopicDescriptor topic;
    private final Set<SymbolRef> outputModels;
    private final boolean checksTargets;
    private final Map<SymbolRef, DbMapping.EmbeddedAssociation> roles = new HashMap<>();
    private final Map<SymbolRef, List<DbMapping.AssociationRole>> links = new HashMap<>();
    private final Map<String, SymbolRef> projectedObjects = new HashMap<>();
    private final List<ProjectedReference> references = new ArrayList<>();
    private final Map<TypeId, Set<String>> enumLeaves = new HashMap<>();

    DbBaseProjection(DbMapping mapping, TopicDescriptor topic) {
        this.mapping = mapping;
        this.topic = topic;
        var plan = mapping.plan();
        outputModels = plan.modelImportClosure(Set.of(topic.model()));
        for (var association : mapping.associations()) {
            var owner = association.ownerRole();
            var target = association.targetRole();
            if ((!association.linkTable() || !owner.single() && hasOnlyScalarAttributes(plan, association))
                    && target.single()
                    && owner.targets().size() == 1 && target.targets().size() == 1
                    && !owner.external() && (!target.external() || supportsExternalTarget(association))
                    && association.object().baseType().isEmpty()
                    && association.object().topic().filter(declared -> belongsTo(plan, declared, topic)).isPresent()) {
                roles.put(association.role().symbol(), association);
            }
        }
        for (var table : mapping.tables()) {
            var association = table.object();
            if (association.kind() != SymbolKind.ASSOCIATION || table.carriesObjectId()
                    || mapping.isEmbeddedLink(table) || association.baseType().isPresent()
                    || association.topic().filter(declared -> belongsTo(plan, declared, topic)).isEmpty()) {
                continue;
            }
            var ends = DbMapping.describeRoles(plan, association);
            if (ends.size() == 2 && ends.stream().allMatch(end -> !end.single() && end.targets().size() == 1
                    && !end.external()
                    && end.slot().role().aggregation() == RoleAggregation.NONE)) {
                links.put(association.symbol(), ends);
            }
        }
        checksTargets = roles.values().stream().anyMatch(role -> !role.targetRole().external())
                || !links.isEmpty() || mapping.tables().stream()
                .filter(table -> table.object().kind() == SymbolKind.STRUCTURE && outputModels.contains(table.object().model()))
                .flatMap(table -> table.columns().stream())
                .anyMatch(column -> column.kind() == DbMapping.Kind.REFERENCE && !column.external());
    }

    private static boolean hasOnlyScalarAttributes(TransferPlan plan, DbMapping.EmbeddedAssociation association) {
        return association.object().slots().stream().filter(AttributeSlot.class::isInstance).map(AttributeSlot.class::cast)
                .filter(slot -> !slot.attribute().transientAttribute())
                .allMatch(slot -> switch (plan.type(slot.effectiveType()).known().orElseThrow().kind()) {
                    case TEXT, BOOLEAN, NUMERIC, ENUMERATION, FORMATTED, OID, BLACKBOX -> true;
                    default -> false;
                });
    }

    private boolean supportsExternalTarget(DbMapping.EmbeddedAssociation association) {
        if (association.linkTable() || association.ownerRole().single() || association.ordered()
                || !mapping.hasBasketColumn()) return false;
        var target = mapping.plan().object(association.target()).orElseThrow();
        return target.transferable() && target.topic()
                .filter(declared -> !declared.equals(topic.symbol()))
                .flatMap(mapping.plan()::topic)
                .filter(declared -> declared.model().equals(topic.model()))
                // Every stored target belongs to this base topic, so it needs no projection.
                .filter(declared -> mapping.storedTopicNames(declared.symbol()).size() == 1)
                .isPresent();
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
                case TEXT, BOOLEAN, NUMERIC, ENUMERATION, FORMATTED, OID, BLACKBOX, COORDINATE, GEOMETRY -> { }
                case STRUCTURE_VALUE, COLLECTION -> {
                    var composition = mapping.compositionsForObject(target.symbol()).stream()
                            .filter(binding -> binding.slot().symbol().equals(field.symbol()))
                            .filter(binding -> outputModels.contains(plan.object(binding.child()).orElseThrow().model()))
                            .findFirst();
                    if (composition.isEmpty()) throw DbMapping.unsupported("base-model export of " + field.name().declared());
                    value.ifPresent(structure -> checkStructure(structure, attribute, attribute.name().declared().toString(), object));
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
                references.add(new ProjectedReference(end.target(), role.name().declared().toString(),
                        "link " + target.name().declared(), reference.target().value()));
                values.put(role.symbol(), reference);
            }
            return Optional.of(new TransferObject(object.identity(), new Structured(target.symbol(), values)));
        }
        if (checksTargets) {
            var targetType = target.symbol();
            object.identity().tid().ifPresent(tid -> projectedObjects.put(tid.value(), targetType));
        }
        for (var entry : object.data().values().entrySet()) {
            var field = plan.symbol(entry.getKey()).known().orElseThrow();
            if (field.kind() == SymbolKind.ROLE && field.owner().map(owner -> new SymbolRef(plan.snapshotId(), owner))
                    .flatMap(plan::object).flatMap(association -> association.topic())
                    .filter(declared -> belongsTo(plan, declared, topic)).isPresent()) {
                var association = roles.get(entry.getKey());
                if (association == null || !mapping.isAssignableTo(target.symbol(), association.owner())) {
                    throw DbMapping.unsupported("base-model export of role " + field.header().name());
                }
                var reference = embeddedReference(association, entry.getValue());
                if (!association.targetRole().external()) {
                    references.add(new ProjectedReference(association.target(), association.role().name().declared().toString(),
                            "object " + object.identity().tid().orElseThrow().value(), reference.target().value()));
                }
                values.put(entry.getKey(), entry.getValue());
            }
        }
        return Optional.of(new TransferObject(object.identity(), new Structured(target.symbol(), values)));
    }

    private static Reference embeddedReference(DbMapping.EmbeddedAssociation association, TransferValue value) {
        if (association.linkTable() && value instanceof Structured payload
                && payload.type().equals(association.object().symbol())) {
            value = payload.values().get(association.role().symbol());
        }
        if (value instanceof Reference reference) return reference;
        throw DbMapping.unsupported("base-model export of role " + association.role().name().declared());
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
                throw new DbException("Missing projected target " + reference.targetTid() + " at " + reference.field()
                        + " of " + reference.source() + " in basket " + basket.value());
            }
        }
    }

    private void checkStructure(TransferValue value, AttributeSlot field, String path, TransferObject owner) {
        var plan = mapping.plan();
        switch (value) {
            case Structured structure -> {
                var type = plan.object(structure.type()).orElseThrow();
                if (!outputModels.contains(type.model())) {
                    throw DbMapping.unsupported("base-model export of structure " + type.name().declared() + " at " + path);
                }
                for (var slot : type.slots()) {
                    if (slot instanceof AttributeSlot component) {
                        structure.value(component.symbol()).ifPresent(part ->
                                checkStructure(part, component, path + "." + component.name().declared().last(), owner));
                    }
                }
            }
            case TransferValue.Repeated elements ->
                    elements.values().forEach(part -> checkStructure(part, field, path, owner));
            case Reference reference -> {
                var type = plan.type(field.effectiveType()).known().orElseThrow();
                if (!(type instanceof ReferenceType link) || link.external() || link.target().isEmpty()
                        || !link.restrictions().isEmpty()) {
                    throw DbMapping.unsupported("base-model export of reference in " + path);
                }
                var target = new SymbolRef(plan.snapshotId(), link.target().orElseThrow());
                references.add(new ProjectedReference(target, path, "object " + owner.identity().tid().orElseThrow().value(),
                        reference.target().value()));
            }
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
