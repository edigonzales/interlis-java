package guru.interlis.xtf24;

import guru.interlis.model.CollectionType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;
import static javax.xml.stream.XMLStreamConstants.*;

final class Xtf24ValueReader {
    private record Slot(int order, AttributeSlot attribute) {}

    private final Xtf24Input input;
    private final Xtf24Layout layout;
    private final Xtf24Limits limits;

    Xtf24ValueReader(Xtf24Input input, Xtf24Layout layout, Xtf24Limits limits) {
        this.input = input;
        this.layout = layout;
        this.limits = limits;
    }

    Structured read(ObjectDescriptor object, Map<SymbolRef, TypeRef> concreteTypes,
                    Set<SymbolRef> structureTypes) throws XMLStreamException {
        var slots = new HashMap<QName, Slot>();
        var fields = layout.fields(object);
        for (int order = 0; order < fields.size(); order++) {
            var field = fields.get(order);
            if (field.slot() instanceof AttributeSlot attribute
                    && slots.putIfAbsent(field.name(), new Slot(order, attribute)) != null) {
                throw input.error(UNSUPPORTED, "Ambiguous XTF field layout");
            }
        }

        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        int previousOrder = -1;
        int event = input.nextTag();
        while (event == START_ELEMENT) {
            QName name = input.cursor().getName();
            Slot slot = slots.get(name);
            if (slot == null) {
                throw input.error(INVALID_WIRE, "Unknown XTF attribute " + name);
            }
            if (slot.order() < previousOrder) {
                throw input.error(INVALID_WIRE, "XTF attributes are out of transfer order");
            }
            previousOrder = slot.order();
            var attribute = slot.attribute();
            if (values.containsKey(attribute.symbol())) {
                throw input.error(INVALID_WIRE, "Repeated noncollection attribute " + name);
            }
            var type = layout.type(attribute.declaredType(), concreteTypes);
            if (type instanceof CollectionType collection) {
                var element = layout.type(new TypeRef(attribute.symbol().snapshot(), collection.elementType()), concreteTypes);
                var items = new ArrayList<TransferValue>();
                do {
                    items.add(readValue(element, concreteTypes, structureTypes));
                    event = input.nextTag();
                } while (event == START_ELEMENT && input.cursor().getName().equals(name));
                values.put(attribute.symbol(), new Repeated(collection.collectionKind(), items));
            } else {
                values.put(attribute.symbol(), readValue(type, concreteTypes, structureTypes));
                event = input.nextTag();
            }
        }
        if (event != END_ELEMENT) {
            throw input.error(INVALID_WIRE, "Expected the end of an XTF object or structure");
        }
        return new Structured(object.symbol(), values);
    }

    private TransferValue readValue(TypeDescriptor type, Map<SymbolRef, TypeRef> concreteTypes,
                                    Set<SymbolRef> structureTypes) throws XMLStreamException {
        if (input.cursor().getAttributeCount() != 0) {
            throw input.error(UNSUPPORTED, "Unexpected attribute on XTF value element");
        }
        return switch (type.kind()) {
            case STRUCTURE_VALUE -> {
                if (input.nextTag() != START_ELEMENT) {
                    throw input.error(INVALID_WIRE, "Expected one structure element");
                }
                var structure = layout.names().object(input.cursor().getName(), structureTypes);
                if (input.cursor().getAttributeCount() != 0) {
                    throw input.error(UNSUPPORTED, "Unexpected attribute on XTF structure");
                }
                var value = read(structure, concreteTypes, structureTypes);
                if (input.nextTag() != END_ELEMENT) {
                    throw input.error(INVALID_WIRE, "Expected exactly one structure element");
                }
                yield value;
            }
            case TEXT, FORMATTED, OID, BOOLEAN, ENUMERATION, ALIGNMENT, NUMERIC, CLASS_VALUE, ATTRIBUTE_PATH ->
                    Xtf24Scalars.read(type, input.text(), limits);
            default -> throw input.error(UNSUPPORTED, "XTF 2.4 value type " + type.kind());
        };
    }
}
