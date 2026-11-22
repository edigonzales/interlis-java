package guru.interlis.db;

import guru.interlis.model.EnumerationType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps the names of checked translations at the transfer boundary. */
final class DbTranslations {
    private record Variant(SymbolRef model, SymbolRef original) { }

    private final TransferPlan plan;
    private final Map<Variant, ObjectDescriptor> objects = new HashMap<>();
    private final Map<Variant, TransferSlot> members = new HashMap<>();
    private final Map<SymbolRef, TransferSlot> slots = new HashMap<>();

    DbTranslations(TransferPlan plan) {
        this.plan = plan;
        if (plan.models().stream().allMatch(model -> model.symbol().equals(model.name().canonical()))) return;
        for (var object : plan.objects()) {
            objects.put(new Variant(object.model(), object.name().canonical()), object);
            for (var slot : object.slots()) {
                slots.putIfAbsent(slot.symbol(), slot);
                var owner = plan.symbol(slot.symbol()).known().orElseThrow().owner().orElseThrow();
                if (owner.equals(object.symbol().symbol())) {
                    members.put(new Variant(object.model(), slot.name().canonical()), slot);
                }
            }
        }
    }

    TransferObject toOriginal(TransferObject object) {
        var source = plan.object(object.type()).orElseThrow();
        return remap(object, source.name().canonical());
    }

    TransferObject inTopic(TransferObject object, SymbolRef topic) {
        var destination = plan.topic(topic).orElseThrow();
        if (destination.name().canonical().equals(topic)) return object;
        var type = objects.get(new Variant(destination.model(), object.type()));
        if (type == null) throw new DbException("No translated object type for " + object.type() + " in "
                + destination.name().declared());
        return remap(object, type.symbol());
    }

    TopicDescriptor outputTopic(TopicDescriptor source, SymbolRef model) {
        if (source.model().equals(model)) return source;
        return plan.topics().stream().filter(candidate -> candidate.model().equals(model)
                        && candidate.name().canonical().equals(source.name().canonical())).findFirst()
                .orElseThrow(() -> new DbException("Export model has no translation of " + source.name().declared()));
    }

    private TransferObject remap(TransferObject object, SymbolRef type) {
        if (object.type().equals(type)) return object;
        return new TransferObject(object.identity(), remap(object.data(), type));
    }

    private Structured remap(Structured data, SymbolRef type) {
        if (data.type().equals(type)) return data;
        var source = plan.object(data.type()).orElseThrow();
        var target = plan.object(type).orElseThrow();
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        data.values().forEach((symbol, value) -> {
            var field = source.slots().stream().filter(slot -> slot.symbol().equals(symbol)).findFirst()
                    .orElseGet(() -> slots.get(symbol));
            if (field == null) throw new DbException("No source field in " + source.name().declared() + " for " + symbol);
            var translated = target.slots().stream().filter(slot -> slot.name().canonical().equals(field.name().canonical()))
                    .findFirst().orElseGet(() -> members.get(new Variant(target.model(), field.name().canonical())));
            if (translated == null) throw new DbException("No translated field for " + field.name().declared());
            values.put(translated.symbol(), translatedValue(field, translated, target.model(), value));
        });
        return new Structured(type, values);
    }

    private TransferValue translatedValue(TransferSlot source, TransferSlot target, SymbolRef model, TransferValue value) {
        if (value instanceof Structured structured) {
            var declaration = plan.object(structured.type()).orElseThrow();
            var expectedModel = model;
            if (target instanceof AttributeSlot attribute) {
                var type = plan.type(attribute.effectiveType()).known().orElseThrow();
                if (type instanceof CollectionType collection) {
                    type = plan.type(new TypeRef(plan.snapshotId(), collection.elementType())).known().orElseThrow();
                }
                if (type instanceof StructureValueType structure && structure.target().isPresent()) {
                    expectedModel = plan.object(new SymbolRef(plan.snapshotId(), structure.target().orElseThrow()))
                            .orElseThrow().model();
                }
            }
            var variant = objects.get(new Variant(expectedModel, declaration.name().canonical()));
            return variant == null ? value : remap(structured, variant.symbol());
        }
        if (value instanceof Repeated repeated) {
            return new Repeated(repeated.kind(), repeated.values().stream()
                    .map(item -> translatedValue(source, target, model, item)).toList());
        }
        if (value instanceof Enumeration enumeration && source instanceof AttributeSlot from
                && target instanceof AttributeSlot to
                && plan.type(from.effectiveType()).known().orElseThrow() instanceof EnumerationType a
                && plan.type(to.effectiveType()).known().orElseThrow() instanceof EnumerationType b) {
            if (a.id().equals(b.id())) return value;
            String path = String.join(".", enumeration.path());
            for (int i = 0; i < a.values().size(); i++) {
                if (a.values().get(i).path().equals(path)) {
                    return new Enumeration(List.of(b.values().get(i).path().split("\\.", -1)));
                }
            }
            throw new DbException("No translation of enum value " + path + " at " + source.name().declared());
        }
        return value;
    }
}
