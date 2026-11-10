package guru.interlis.xtf24;

import guru.interlis.model.CollectionType;
import guru.interlis.model.BlackboxType;
import guru.interlis.transfer.TransferEvent.Consistency;
import guru.interlis.model.ReferenceType;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferValue.Reference;
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
    private final Xtf24References references;
    private final Xtf24GeometryCodec geometry;
    private Consistency consistency = Consistency.COMPLETE;

    Xtf24ValueWriter(TransferPlan plan, Xtf24Output output, Xtf24Layout layout, Xtf24Limits limits) {
        this.plan = plan;
        this.output = output;
        this.layout = layout;
        this.limits = limits;
        references = new Xtf24References(limits);
        geometry = new Xtf24GeometryCodec(plan, layout);
    }

    void basketConsistency(Consistency consistency) {
        this.consistency = consistency;
    }

    void write(ObjectDescriptor object, Structured data, Map<SymbolRef, TypeRef> concreteTypes,
               Set<SymbolRef> structureTypes, Set<SymbolRef> activeTypes) throws XMLStreamException {
        write(object, data, concreteTypes, structureTypes, activeTypes, java.util.Optional.empty());
    }

    private void write(ObjectDescriptor object, Structured data, Map<SymbolRef, TypeRef> concreteTypes,
                       Set<SymbolRef> structureTypes, Set<SymbolRef> activeTypes,
                       java.util.Optional<SymbolRef> embeddedReference) throws XMLStreamException {
        int written = embeddedReference.isPresent() ? 1 : 0;
        for (var field : layout.fields(object, activeTypes, embeddedReference.isPresent())) {
            TransferValue value = data.values().get(field.slot().symbol());
            if (value == null) {
                continue;
            }
            if (!field.embeddings().isEmpty()) {
                output.start(field.name());
                writeEmbedded(field, value, concreteTypes, structureTypes, activeTypes);
                output.end();
                written++;
                continue;
            }
            if (field.slot() instanceof RoleSlot role) {
                output.start(field.name());
                references.write(output, Xtf24Scalars.require(value, Reference.class), role.role().external(),
                        role.role().ordered(), layout.stableTargets(role.role().targetRoots()));
                output.end();
                written++;
                continue;
            }
            var attribute = (AttributeSlot) field.slot();
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
                    writeValue(field.name(), element, item, concreteTypes, structureTypes, activeTypes);
                }
            } else {
                writeValue(field.name(), type, value, concreteTypes, structureTypes, activeTypes);
            }
            written++;
        }
        if (written != data.values().size()) {
            throw Xtf24Wire.failure(UNSUPPORTED, "XTF object contains fields outside its wire layout");
        }
    }

    private void writeEmbedded(Xtf24Layout.Field field, TransferValue value,
                               Map<SymbolRef, TypeRef> concreteTypes, Set<SymbolRef> structureTypes,
                               Set<SymbolRef> activeTypes) throws XMLStreamException {
        var variants = field.embeddings();
        var first = variants.getFirst();
        if (variants.stream().anyMatch(variant ->
                !variant.rootOpposite().name().canonical().equals(first.rootOpposite().name().canonical()))) {
            throw Xtf24Wire.failure(UNSUPPORTED, "Ambiguous embedded XTF field");
        }
        if (value instanceof Reference reference) {
            var role = first.opposite().role();
            boolean stable = layout.stableTargets(role.targetRoots());
            if (variants.stream().anyMatch(variant -> variant.opposite().role().external() != role.external()
                    || variant.opposite().role().ordered() != role.ordered()
                    || layout.stableTargets(variant.opposite().role().targetRoots()) != stable)) {
                throw Xtf24Wire.failure(UNSUPPORTED, "Embedded role needs an explicit association type");
            }
            references.write(output, reference, role.external(), role.ordered(), stable);
            return;
        }
        var payload = Xtf24Scalars.require(value, Structured.class);
        var variant = variants.stream().filter(candidate -> candidate.association().symbol().equals(payload.type()))
                .findFirst().orElseThrow(() -> Xtf24Wire.failure(INVALID_WIRE, "Unexpected embedded association type"));
        var role = variant.opposite();
        var reference = Xtf24Scalars.require(payload.values().get(role.symbol()), Reference.class);
        references.write(output, reference, role.role().external(), role.role().ordered(),
                layout.stableTargets(role.role().targetRoots()));
        output.start(layout.names().objectName(variant.association()));
        write(variant.association(), payload, concreteTypes, structureTypes, activeTypes,
                java.util.Optional.of(role.symbol()));
        output.end();
    }

    private void writeValue(QName name, TypeDescriptor type, TransferValue value,
                            Map<SymbolRef, TypeRef> concreteTypes, Set<SymbolRef> structureTypes,
                            Set<SymbolRef> activeTypes) throws XMLStreamException {
        if (value == TransferValue.Undefined.INSTANCE) {
            throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 cannot express an explicit undefined value");
        }
        if (type instanceof BlackboxType blackbox) {
            Xtf24Blackbox.write(name, output, blackbox, value);
            return;
        }
        output.start(name);
        if (type.kind() == guru.interlis.model.TypeKind.COORDINATE
                || type.kind() == guru.interlis.model.TypeKind.GEOMETRY) {
            geometry.write(output, type, value, concreteTypes, consistency);
        } else if (type instanceof ReferenceType reference) {
            references.write(output, Xtf24Scalars.require(value, Reference.class), reference.external(), false,
                    layout.stableTargets(reference.targetRoots()));
        } else if (type.kind() == guru.interlis.model.TypeKind.STRUCTURE_VALUE) {
            var structured = Xtf24Scalars.require(value, Structured.class);
            if (!structureTypes.contains(structured.type())) {
                throw Xtf24Wire.failure(INVALID_WIRE, "Unknown or abstract XTF structure type");
            }
            var structure = plan.object(structured.type()).orElseThrow();
            output.start(layout.names().objectName(structure));
            write(structure, structured, concreteTypes, structureTypes, activeTypes);
            output.end();
        } else {
            output.text(Xtf24Scalars.write(type, value, limits));
        }
        output.end();
    }
}
