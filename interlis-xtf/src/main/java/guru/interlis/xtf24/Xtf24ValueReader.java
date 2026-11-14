package guru.interlis.xtf24;

import guru.interlis.model.CollectionType;
import guru.interlis.model.BlackboxType;
import guru.interlis.transfer.TransferEvent.Consistency;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.StructureValueType;
import guru.interlis.transfer.RoleSlot;
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
    private record Slot(int order, Xtf24Layout.Field field) {}

    private final Xtf24Input input;
    private final Xtf24Layout layout;
    private final Xtf24Limits limits;
    private final Xtf24References references;
    private final Xtf24GeometryCodec geometry;
    private Consistency consistency = Consistency.COMPLETE;

    Xtf24ValueReader(guru.interlis.transfer.TransferPlan plan, Xtf24Input input, Xtf24Layout layout, Xtf24Limits limits) {
        this.input = input;
        this.layout = layout;
        this.limits = limits;
        references = new Xtf24References(limits);
        geometry = new Xtf24GeometryCodec(plan, layout);
    }

    void basketConsistency(Consistency consistency) {
        this.consistency = consistency;
    }

    Structured read(ObjectDescriptor object, Map<SymbolRef, TypeRef> concreteTypes,
                    Set<SymbolRef> structureTypes, Set<SymbolRef> activeTypes) throws XMLStreamException {
        return read(object, concreteTypes, structureTypes, activeTypes, false);
    }

    private Structured read(ObjectDescriptor object, Map<SymbolRef, TypeRef> concreteTypes,
                            Set<SymbolRef> structureTypes, Set<SymbolRef> activeTypes, boolean payloadOnly)
            throws XMLStreamException {
        var slots = new HashMap<QName, Slot>();
        var fields = layout.fields(object, activeTypes, payloadOnly);
        for (int order = 0; order < fields.size(); order++) {
            var field = fields.get(order);
            if (slots.putIfAbsent(field.name(), new Slot(order, field)) != null) {
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
            var field = slot.field();
            if (values.containsKey(field.slot().symbol())) {
                throw input.error(INVALID_WIRE, "Repeated noncollection attribute " + name);
            }
            if (!field.embeddings().isEmpty()) {
                values.put(field.slot().symbol(), readEmbedded(field, concreteTypes, structureTypes, activeTypes));
                event = input.nextTag();
                continue;
            }
            if (field.slot() instanceof RoleSlot role) {
                values.put(role.symbol(), readReference(role.role().external(), role.role().ordered(),
                        layout.stableTargets(role.role().targetRoots())));
                event = input.nextTag();
                continue;
            }
            var attribute = (AttributeSlot) field.slot();
            var type = layout.type(attribute.declaredType(), concreteTypes);
            if (type instanceof CollectionType collection) {
                var element = layout.type(new TypeRef(attribute.symbol().snapshot(), collection.elementType()), concreteTypes);
                var items = new ArrayList<TransferValue>();
                do {
                    items.add(readValue(element, object.model(), concreteTypes, structureTypes, activeTypes));
                    event = input.nextTag();
                } while (event == START_ELEMENT && input.cursor().getName().equals(name));
                values.put(attribute.symbol(), new Repeated(collection.collectionKind(), items));
            } else {
                values.put(attribute.symbol(), readValue(type, object.model(), concreteTypes, structureTypes, activeTypes));
                event = input.nextTag();
            }
        }
        if (event != END_ELEMENT) {
            throw input.error(INVALID_WIRE, "Expected the end of an XTF object or structure");
        }
        return new Structured(object.symbol(), values);
    }

    private TransferValue.Reference readReference(boolean external, boolean ordered, boolean stable)
            throws XMLStreamException {
        var raw = references.read(input);
        if (input.nextTag() != END_ELEMENT) {
            throw input.error(INVALID_WIRE, "Reference cannot contain child values");
        }
        return references.interpret(raw, external, ordered, stable);
    }

    private TransferValue readEmbedded(Xtf24Layout.Field field, Map<SymbolRef, TypeRef> concreteTypes,
                                       Set<SymbolRef> structureTypes, Set<SymbolRef> activeTypes)
            throws XMLStreamException {
        var variants = field.embeddings();
        var first = variants.getFirst();
        if (variants.stream().anyMatch(variant ->
                !variant.rootOpposite().name().canonical().equals(first.rootOpposite().name().canonical()))) {
            throw input.error(UNSUPPORTED, "Ambiguous embedded XTF field");
        }
        var raw = references.read(input);
        if (input.nextTag() == END_ELEMENT) {
            var role = first.opposite().role();
            boolean stable = layout.stableTargets(role.targetRoots());
            if (variants.stream().anyMatch(variant -> variant.opposite().role().external() != role.external()
                    || variant.opposite().role().ordered() != role.ordered()
                    || layout.stableTargets(variant.opposite().role().targetRoots()) != stable)) {
                throw input.error(UNSUPPORTED, "Embedded role needs an explicit association type");
            }
            return references.interpret(raw, role.external(), role.ordered(), stable);
        }
        if (input.cursor().getEventType() != START_ELEMENT) {
            throw input.error(INVALID_WIRE, "Expected embedded association payload");
        }
        var available = variants.stream().map(variant -> variant.association().symbol())
                .collect(java.util.stream.Collectors.toSet());
        var association = layout.names().object(input.cursor().getName(), available);
        var variant = variants.stream().filter(value -> value.association().symbol().equals(association.symbol()))
                .findFirst().orElseThrow();
        if (input.cursor().getAttributeCount() != 0) {
            throw input.error(UNSUPPORTED, "Embedded association has technical attributes");
        }
        var payload = read(association, concreteTypes, structureTypes, activeTypes, true);
        if (input.nextTag() != END_ELEMENT) {
            throw input.error(INVALID_WIRE, "Expected the end of the embedded role");
        }
        var role = variant.opposite();
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        values.put(role.symbol(), references.interpret(raw, role.role().external(), role.role().ordered(),
                layout.stableTargets(role.role().targetRoots())));
        values.putAll(payload.values());
        return new Structured(association.symbol(), values);
    }

    private TransferValue readValue(TypeDescriptor type, SymbolRef containingModel, Map<SymbolRef, TypeRef> concreteTypes,
                                    Set<SymbolRef> structureTypes, Set<SymbolRef> activeTypes) throws XMLStreamException {
        if (type instanceof ReferenceType reference) {
            return readReference(reference.external(), false, layout.stableTargets(reference.targetRoots()));
        }
        if (input.cursor().getAttributeCount() != 0) {
            throw input.error(UNSUPPORTED, "Unexpected attribute on XTF value element");
        }
        return switch (type.kind()) {
            case BLACKBOX -> Xtf24Blackbox.read(input, (BlackboxType) type);
            case COORDINATE, GEOMETRY -> geometry.read(input, type, concreteTypes, consistency);
            case STRUCTURE_VALUE -> {
                if (input.nextTag() != START_ELEMENT) {
                    throw input.error(INVALID_WIRE, "Expected one structure element");
                }
                var structure = layout.structure(input.cursor().getName(), structureTypes,
                        (StructureValueType) type, containingModel);
                if (input.cursor().getAttributeCount() != 0) {
                    throw input.error(UNSUPPORTED, "Unexpected attribute on XTF structure");
                }
                var value = read(structure, concreteTypes, structureTypes, activeTypes);
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
