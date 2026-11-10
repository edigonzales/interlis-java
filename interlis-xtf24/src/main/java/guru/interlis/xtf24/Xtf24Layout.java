package guru.interlis.xtf24;

import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class Xtf24Layout {
    record Field(TransferSlot slot, QName name, int inheritanceLevel) {}

    private final TransferPlan plan;
    private final Xtf24Names names;

    Xtf24Layout(TransferPlan plan) {
        this.plan = plan;
        names = new Xtf24Names(plan);
    }

    Xtf24Names names() {
        return names;
    }

    List<Field> fields(ObjectDescriptor object) {
        var lineage = new ArrayList<ObjectDescriptor>();
        for (Optional<ObjectDescriptor> current = plan.object(object.symbol()); current.isPresent();
             current = current.orElseThrow().baseType().flatMap(plan::object)) {
            lineage.addFirst(current.orElseThrow());
        }
        var fields = new ArrayList<Field>();
        for (TransferSlot slot : object.slots()) {
            if (slot instanceof AttributeSlot attribute && attribute.attribute().transientAttribute()) {
                continue;
            }
            int level = 0;
            while (level < lineage.size() - 1 && !containsPosition(lineage.get(level), slot)) {
                level++;
            }
            fields.add(new Field(slot, names.slotName(slot), level));
        }
        fields.sort(Comparator.comparingInt(Field::inheritanceLevel)
                .thenComparingInt(field -> field.slot() instanceof RoleSlot ? 0 : 1)
                .thenComparingInt(field -> field.slot().member().position()));
        return List.copyOf(fields);
    }

    private static boolean containsPosition(ObjectDescriptor ancestor, TransferSlot slot) {
        // Effective positions are inherited along this one object lineage. Local declaration
        // positions and positions belonging to a different object type cannot be compared.
        return ancestor.slots().stream().anyMatch(base -> base.member().position() == slot.member().position());
    }
}
