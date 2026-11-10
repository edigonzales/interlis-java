package guru.interlis.xtf24;

import guru.interlis.model.CollectionType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.util.Map;
import java.util.Set;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;

final class Xtf24ValueWriter {
    private final TransferPlan plan;
    private final Xtf24Output output;
    private final Xtf24Layout layout;
    private final Xtf24Limits limits;

    Xtf24ValueWriter(TransferPlan plan, Xtf24Output output, Xtf24Layout layout, Xtf24Limits limits) {
        this.plan = plan;
        this.output = output;
        this.layout = layout;
        this.limits = limits;
    }

    void write(ObjectDescriptor object, Structured data, Map<SymbolRef, TypeRef> concreteTypes,
               Set<SymbolRef> structureTypes) throws XMLStreamException {
        int written = 0;
        for (var field : layout.fields(object)) {
            TransferValue value = data.values().get(field.slot().symbol());
            if (value == null) {
                continue;
            }
            if (!(field.slot() instanceof AttributeSlot attribute)) {
                throw Xtf24Wire.failure(UNSUPPORTED, "Association roles are outside the current XTF 2.4 codec");
            }
            if (value == TransferValue.Undefined.INSTANCE) {
                throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 cannot express an explicit undefined value");
            }
            var type = layout.type(attribute.declaredType(), concreteTypes);
            if (type instanceof CollectionType collection) {
                var repeated = Xtf24Scalars.require(value, Repeated.class);
                if (repeated.kind() != collection.collectionKind()) {
                    throw Xtf24Wire.failure(INVALID_WIRE, "BAG or LIST does not match the field's wire type");
                }
                if (repeated.values().isEmpty()) {
                    throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 cannot express an explicit empty collection");
                }
                var element = layout.type(new TypeRef(plan.snapshotId(), collection.elementType()), concreteTypes);
                for (var item : repeated.values()) {
                    writeValue(field.name(), element, item, concreteTypes, structureTypes);
                }
            } else {
                writeValue(field.name(), type, value, concreteTypes, structureTypes);
            }
            written++;
        }
        if (written != data.values().size()) {
            throw Xtf24Wire.failure(UNSUPPORTED, "XTF object contains fields outside its wire layout");
        }
    }

    private void writeValue(QName name, TypeDescriptor type, TransferValue value,
                            Map<SymbolRef, TypeRef> concreteTypes, Set<SymbolRef> structureTypes)
            throws XMLStreamException {
        if (value == TransferValue.Undefined.INSTANCE) {
            throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 cannot express an explicit undefined value");
        }
        output.start(name);
        if (type.kind() == guru.interlis.model.TypeKind.STRUCTURE_VALUE) {
            var structured = Xtf24Scalars.require(value, Structured.class);
            if (!structureTypes.contains(structured.type())) {
                throw Xtf24Wire.failure(INVALID_WIRE, "Unknown or abstract XTF structure type");
            }
            var structure = plan.object(structured.type()).orElseThrow();
            output.start(layout.names().objectName(structure));
            write(structure, structured, concreteTypes, structureTypes);
            output.end();
        } else {
            output.text(Xtf24Scalars.write(type, value, limits));
        }
        output.end();
    }
}
