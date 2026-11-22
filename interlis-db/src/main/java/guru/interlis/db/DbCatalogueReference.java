package guru.interlis.db;

import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.Modifier;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;

import java.util.Optional;

/** A CHBase V1 structure encoded in SQL by its single reference field. */
record DbCatalogueReference(SymbolRef structure, AttributeSlot field, ReferenceType reference) {
    static Optional<DbCatalogueReference> describe(TransferPlan plan, AttributeSlot outer) {
        if (!CardinalityUpper.finite(1).equals(outer.cardinality().upper())
                || !(plan.type(outer.effectiveType()).known().orElseThrow() instanceof StructureValueType value)
                || value.target().isEmpty() || !value.restrictions().isEmpty()) {
            return Optional.empty();
        }
        var structure = plan.object(new SymbolRef(plan.snapshotId(), value.target().orElseThrow())).orElseThrow();
        if (structure.modifiers().contains(Modifier.ABSTRACT) || !isWrapper(plan, structure) || structure.slots().size() != 1
                || !(structure.slots().getFirst() instanceof AttributeSlot field)
                || !isReferenceField(field)
                || !(plan.type(field.effectiveType()).known().orElseThrow() instanceof ReferenceType reference)
                || !reference.external() || reference.target().isEmpty() || !reference.restrictions().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DbCatalogueReference(structure.symbol(), field, reference));
    }

    static boolean isWrapper(TransferPlan plan, ObjectDescriptor structure) {
        for (var current = structure; ; current = plan.object(current.baseType().orElseThrow()).orElseThrow()) {
            switch (current.name().declared().toString()) {
                case "CatalogueObjects_V1.Catalogues.CatalogueReference",
                     "CatalogueObjects_V1.Catalogues.MandatoryCatalogueReference" -> { return true; }
                default -> {
                    if (current.baseType().isEmpty()) return false;
                }
            }
        }
    }

    static boolean isReferenceField(AttributeSlot field) {
        return field.name().declared().last().equals("Reference") && !field.attribute().transientAttribute()
                && CardinalityUpper.finite(1).equals(field.cardinality().upper());
    }
}
