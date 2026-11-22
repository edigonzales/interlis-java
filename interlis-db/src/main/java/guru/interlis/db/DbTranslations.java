package guru.interlis.db;

import guru.interlis.model.EnumerationType;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.Structured;

import java.util.LinkedHashMap;
import java.util.List;

/** Maps the names of checked translations at the transfer boundary. */
final class DbTranslations {
    private final TransferPlan plan;

    DbTranslations(TransferPlan plan) {
        this.plan = plan;
    }

    TransferObject toOriginal(TransferObject object) {
        var source = plan.object(object.type()).orElseThrow();
        return remap(object, source.name().canonical());
    }

    TransferObject inTopic(TransferObject object, SymbolRef topic) {
        var destination = plan.topic(topic).orElseThrow();
        if (destination.name().canonical().equals(topic)) return object;
        var type = plan.objects().stream().filter(candidate -> candidate.model().equals(destination.model())
                && candidate.name().canonical().equals(object.type())).findFirst()
                .orElseThrow(() -> new DbException("No translated object type for " + object.type()
                        + " in " + destination.name().declared()));
        return remap(object, type.symbol());
    }

    private TransferObject remap(TransferObject object, SymbolRef type) {
        if (object.type().equals(type)) return object;
        var source = plan.object(object.type()).orElseThrow();
        var target = plan.object(type).orElseThrow();
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        object.data().values().forEach((symbol, value) -> {
            var field = source.slots().stream().filter(slot -> slot.symbol().equals(symbol)).findFirst()
                    .orElseThrow(() -> new DbException("No source field in " + source.name().declared() + " for " + symbol));
            var translated = target.slots().stream().filter(slot -> slot.name().canonical().equals(field.name().canonical()))
                    .findFirst().orElseThrow(() -> new DbException("No translated field for " + field.name().declared()));
            values.put(translated.symbol(), translatedValue(field, translated, value));
        });
        return new TransferObject(object.identity(), new Structured(type, values));
    }

    private TransferValue translatedValue(TransferSlot source, TransferSlot target, TransferValue value) {
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
