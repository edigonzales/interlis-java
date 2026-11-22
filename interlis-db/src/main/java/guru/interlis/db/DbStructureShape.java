package guru.interlis.db;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;

import java.util.Optional;

final class DbStructureShape {
    private DbStructureShape() { }

    static TypeDescriptor type(TransferPlan plan, AttributeSlot field) {
        return plan.type(field.effectiveType()).known().orElseThrow();
    }

    static Optional<ObjectDescriptor> target(TransferPlan plan, TypeDescriptor type) {
        if (!(type instanceof StructureValueType value) || !value.restrictions().isEmpty()) return Optional.empty();
        return value.target().flatMap(id -> plan.object(new SymbolRef(plan.snapshotId(), id)));
    }

    static Optional<ObjectDescriptor> bagElement(TransferPlan plan, AttributeSlot field) {
        if (!(type(plan, field) instanceof CollectionType collection) || collection.collectionKind() != CollectionKind.BAG) {
            return Optional.empty();
        }
        return target(plan, plan.type(new TypeRef(plan.snapshotId(), collection.elementType())).known().orElseThrow());
    }

    static Optional<AttributeSlot> field(ObjectDescriptor structure, String name) {
        return structure.slots().stream().filter(AttributeSlot.class::isInstance).map(AttributeSlot.class::cast)
                .filter(field -> !field.attribute().transientAttribute() && field.name().declared().last().equals(name))
                .findFirst();
    }

}
