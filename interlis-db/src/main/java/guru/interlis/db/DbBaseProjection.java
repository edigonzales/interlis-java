package guru.interlis.db;

import guru.interlis.model.CoordinateType;
import guru.interlis.model.OidRule;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Structured;

import java.util.LinkedHashMap;
import java.util.Optional;

/** Projects decoded export events through the model's checked inheritance. */
final class DbBaseProjection {
    private DbBaseProjection() { }

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

    static Optional<TransferObject> object(TransferPlan plan, TransferObject object, TopicDescriptor topic) {
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
            var type = plan.type(attribute.effectiveType()).known().orElseThrow();
            switch (type.kind()) {
                case TEXT, BOOLEAN, NUMERIC, ENUMERATION, FORMATTED, OID, BLACKBOX -> { }
                case COORDINATE -> {
                    if (((CoordinateType) type).multiCoordinate()) {
                        throw DbMapping.unsupported("base-model export of " + field.name().declared());
                    }
                }
                default -> throw DbMapping.unsupported("base-model export of " + field.name().declared());
            }
            object.value(field.symbol()).ifPresent(value -> values.put(field.symbol(), value));
        }
        for (var field : source.slots()) {
            if (field instanceof AttributeSlot attribute && attribute.member().overridden() && target != source
                    && target.slots().stream().anyMatch(base -> base.name().declared().last().equals(field.name().declared().last())
                            && !base.symbol().equals(field.symbol()))) {
                throw DbMapping.unsupported("base-model export of extended attribute " + field.name().declared());
            }
        }
        for (var symbol : object.data().values().keySet()) {
            var field = plan.symbol(symbol).known().orElseThrow();
            if (field.kind() == SymbolKind.ROLE && field.owner().map(owner -> new SymbolRef(plan.snapshotId(), owner))
                    .flatMap(plan::object).flatMap(association -> association.topic())
                    .filter(declared -> belongsTo(plan, declared, topic)).isPresent()) {
                throw DbMapping.unsupported("base-model export of role " + field.header().name());
            }
        }
        return Optional.of(new TransferObject(object.identity(), new Structured(target.symbol(), values)));
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
