package guru.interlis.xtf23;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.Modifier;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferWriter;

import javax.xml.stream.XMLStreamException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.transfer.TransferEvent.*;
import static guru.interlis.transfer.TransferGeometry.*;
import static guru.interlis.transfer.TransferIdentity.*;
import static guru.interlis.transfer.TransferValue.*;
import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static guru.interlis.xtf23.Xtf23Output.error;

/**
 * Writes one XTF 2.3 FULL transfer in model order to a caller-owned stream. Events
 * are written synchronously. Successful {@link EndTransfer} completes and flushes
 * the XML document. Close releases the writer without closing the stream or
 * completing an abandoned document. A destination failure after output begins
 * can leave partial XML and makes the writer terminal.
 *
 * <p>Host source identities and the source/synthetic provenance of IDs are not
 * encoded in XTF. Transport IDs, stable OID spellings and references must agree
 * with the supplied OIDSPACE declarations.</p>
 */
public final class Xtf23Writer extends TransferWriter {
    private final TransferPlan plan;
    private final Xtf23Layout layout;
    private final Xtf23Output xml;
    private final List<Xtf23OidSpace> oidSpaces;
    private final Set<String> oidAliases;
    private final Xtf23IdentityScope identities = new Xtf23IdentityScope();
    private final Map<SymbolRef, List<Xtf23Layout.Field>> fields = new HashMap<>();
    private Set<SymbolRef> activeTypes = Set.of();

    /** Creates a writer with a nonnull plan and output and no OIDSPACE declarations. */
    public Xtf23Writer(TransferPlan plan, OutputStream output) {
        this(plan, output, List.of());
    }

    /**
     * Creates a writer with a nonnull plan and caller-owned output.
     * The nonnull list is copied in declaration order and must have unique aliases.
     * The constructor does not write to the stream.
     */
    public Xtf23Writer(TransferPlan plan, OutputStream output, List<Xtf23OidSpace> oidSpaces) {
        super(plan);
        this.plan = plan;
        this.layout = new Xtf23Layout(plan);
        this.xml = new Xtf23Output(output);
        this.oidSpaces = List.copyOf(oidSpaces);
        var aliases = new HashSet<String>();
        if (this.oidSpaces.size() > Xtf23Reader.MAX_OID_SPACES) {
            throw new IllegalArgumentException("Too many XTF OIDSPACE declarations");
        }
        for (var space : this.oidSpaces) {
            if (!aliases.add(space.alias())) {
                throw new IllegalArgumentException("Repeated XTF OIDSPACE alias " + space.alias());
            }
        }
        oidAliases = Set.copyOf(aliases);
    }

    @Override
    protected void writeEvent(TransferEvent event, CancellationToken cancellation) {
        xml.begin(cancellation);
        try {
            switch (event) {
                case StartTransfer start -> startTransfer(start);
                case StartBasket start -> startBasket(start);
                case ObjectEvent occurrence -> writeObject(occurrence);
                case EndBasket ignored -> {
                    xml.end();
                    clearBasket();
                }
                case EndTransfer ignored -> {
                    xml.end(); // DATASECTION
                    xml.end(); // TRANSFER
                    xml.endDocument();
                    identities.release();
                }
            }
            cancellation.throwIfCancellationRequested();
        } catch (XMLStreamException failure) {
            clearTransfer();
            throw Xtf23Output.translate(failure);
        } catch (RuntimeException failure) {
            clearTransfer();
            throw failure;
        } finally {
            xml.finish();
        }
    }

    @Override
    protected void release() {
        clearTransfer();
        xml.close();
    }

    private void clearTransfer() {
        clearBasket();
        identities.release();
    }

    private void clearBasket() {
        activeTypes = Set.of();
        fields.clear();
    }

    private void startTransfer(StartTransfer start) throws XMLStreamException {
        if (start.models().isEmpty()) {
            throw error(INVALID_WIRE, "XTF MODELS requires at least one MODEL");
        }
        var sender = required(start.sender(), "SENDER");
        xml.open();
        xml.start("HEADERSECTION");
        xml.attribute("VERSION", "2.3");
        xml.attribute("SENDER", sender);
        xml.start("MODELS");
        var seen = new HashSet<SymbolRef>();
        for (var declaration : start.models()) {
            if (!seen.add(declaration.model())) {
                throw error(INVALID_WIRE, "Repeated XTF model declaration");
            }
            var model = plan.model(declaration.model()).orElseThrow();
            var symbol = (ModelSymbol) plan.symbol(declaration.model()).known().orElseThrow();
            if (model.languageVersion() != LanguageVersion.ILI23) {
                throw error(UNSUPPORTED, "Model " + model.name().declared() + " does not use INTERLIS 2.3");
            }
            if (symbol.translation().isPresent()) {
                throw error(UNSUPPORTED, "Translated models require an XTF ALIAS table");
            }
            String version = required(declaration.modelVersion(), "MODEL VERSION");
            String issuer = required(declaration.issuer(), "MODEL URI");
            if (!issuer.equals(symbol.issuer())) {
                throw error(INVALID_WIRE, "XTF declaration conflicts with model " + model.name().declared());
            }
            xml.start("MODEL");
            xml.attribute("NAME", model.name().declared().toString());
            xml.attribute("VERSION", version);
            xml.attribute("URI", issuer);
            xml.end();
        }
        xml.end(); // MODELS
        if (!oidSpaces.isEmpty()) {
            xml.start("OIDSPACES");
            for (var space : oidSpaces) {
                xml.start("OIDSPACE");
                xml.attribute("NAME", space.alias());
                xml.attribute("OIDDOMAIN", space.domain());
                xml.end();
            }
            xml.end();
        }
        if (start.comment().isPresent()) {
            textElement("COMMENT", start.comment().orElseThrow());
        }
        xml.end(); // HEADERSECTION
        xml.start("DATASECTION");
    }

    private void startBasket(StartBasket start) throws XMLStreamException {
        if (start.kind() != BasketKind.FULL || !start.domainBindings().isEmpty()) {
            throw error(UNSUPPORTED, "Only XTF 2.3 FULL baskets without domain bindings are supported");
        }
        if (start.startState().isPresent() || start.endState().isPresent()) {
            throw error(INVALID_WIRE, "A FULL basket cannot carry incremental state");
        }
        String bid = identifier(start.id().value());
        if (!Xtf23Reader.stableId(bid).equals(start.stableOid())) {
            throw error(INVALID_WIRE, "Stable basket identity must agree with the prefixed BID");
        }
        if (!identities.claim(start.id())) {
            throw error(DUPLICATE_ID, "XTF FULL BID/TID collision for " + bid);
        }
        var topic = plan.topic(start.topic()).orElseThrow();
        var types = new HashSet<>(topic.objectTypes());
        var additional = start.additionalTopics();
        if (new HashSet<>(additional).size() != additional.size()) {
            throw error(INVALID_WIRE, "Repeated XTF extension topic");
        }
        var names = additional.stream().map(ref -> {
            var extension = plan.topic(ref).orElseThrow();
            types.addAll(extension.objectTypes());
            return extension.name().declared().toString();
        }).toList();
        activeTypes = Set.copyOf(types);
        xml.start(topic.name().declared().toString());
        xml.attribute("BID", bid);
        if (!names.isEmpty()) {
            xml.attribute("TOPICS", String.join(",", names));
        }
        writeConsistency(start.consistency());
    }

    private void writeObject(ObjectEvent occurrence) throws XMLStreamException {
        var object = occurrence.object();
        var descriptor = plan.object(object.type()).orElseThrow();
        if (layout.embedded(descriptor).isPresent()) {
            throw error(INVALID_WIRE, "Embedded XTF association cannot occur as a standalone object");
        }
        if (occurrence.operation().isPresent()) {
            throw error(INVALID_WIRE, "A FULL object cannot carry an incremental OPERATION");
        }
        var identity = object.identity();
        var tid = identity.tid();
        boolean requiresTid = descriptor.kind() == SymbolKind.CLASS || layout.identified(descriptor);
        if (requiresTid != tid.isPresent()) {
            throw error(INVALID_WIRE, requiresTid ? "Missing XTF TID" : "A link without its own identification cannot carry TID");
        }
        var wireTid = tid.map(id -> identifier(id.value()));
        if (!wireTid.flatMap(Xtf23Reader::stableId).equals(identity.oid().map(ObjectOid::value))) {
            throw error(INVALID_WIRE, "Stable object identity must agree with the prefixed TID");
        }
        if (tid.isPresent() && !identities.claim(tid.orElseThrow())) {
            throw error(DUPLICATE_ID, "XTF FULL BID/TID collision for " + wireTid.orElseThrow());
        }
        xml.start(descriptor.name().declared().toString());
        if (wireTid.isPresent()) {
            xml.attribute("TID", wireTid.orElseThrow());
        }
        if (identity.originalBasket().isPresent()) {
            xml.attribute("BID", identifier(identity.originalBasket().orElseThrow().value()));
        }
        writeConsistency(occurrence.consistency());
        writeFields(descriptor, object.data(), Optional.empty());
        xml.end();
    }

    private void writeConsistency(Consistency consistency) throws XMLStreamException {
        if (consistency != Consistency.COMPLETE) {
            xml.attribute("CONSISTENCY", consistency.name());
        }
    }

    private void writeFields(ObjectDescriptor descriptor, Structured data, Optional<SymbolRef> embeddedReference)
            throws XMLStreamException {
        var available = embeddedReference.isPresent() ? layout.fields(descriptor, activeTypes, true)
                : fields.computeIfAbsent(descriptor.symbol(), key -> layout.fields(descriptor, activeTypes, false));
        var remaining = new HashSet<>(data.values().keySet());
        embeddedReference.ifPresent(remaining::remove);
        var names = new HashMap<String, Integer>();
        for (var field : available) {
            names.merge(field.name(), 1, Integer::sum);
        }
        for (var field : available) {
            var value = data.values().get(field.slot().symbol());
            if (value == null) {
                continue;
            }
            if (!remaining.remove(field.slot().symbol()) || names.get(field.name()) != 1) {
                throw error(UNSUPPORTED, "Ambiguous XTF field " + field.name());
            }
            if (value == Undefined.INSTANCE) {
                throw error(UNSUPPORTED, "XTF has no explicit undefined value for " + field.name());
            }
            xml.start(field.name());
            if (!field.embeddings().isEmpty()) {
                writeEmbedded(field, value);
            } else {
                switch (field.slot()) {
                    case AttributeSlot attribute -> writeValue(layout.type(attribute.effectiveType()), value);
                    case RoleSlot role -> writeReference(requireValue(value, Reference.class),
                            role.role().external(), role.role().ordered());
                }
            }
            xml.end();
        }
        if (!remaining.isEmpty()) {
            throw error(INVALID_WIRE, "Object supplies a field outside its XTF layout");
        }
    }

    private void writeValue(TypeDescriptor type, TransferValue value) throws XMLStreamException {
        switch (type.kind()) {
            case TEXT -> writeText(requireValue(value, Text.class), ((TextType) type).textKind() == TextKind.MTEXT);
            case FORMATTED -> writeText(requireValue(value, Text.class), false);
            case BOOLEAN -> xml.text(Boolean.toString(requireValue(value, BooleanValue.class).value()));
            case ENUMERATION, ALIGNMENT -> writeEnumeration(requireValue(value, Enumeration.class));
            case NUMERIC -> xml.text(number(requireValue(value, NumberValue.class)));
            case REFERENCE -> writeReference(requireValue(value, Reference.class), ((ReferenceType) type).external(), false);
            case STRUCTURE_VALUE -> writeStructure((StructureValueType) type, requireValue(value, Structured.class));
            case COLLECTION -> writeCollection((CollectionType) type, requireValue(value, Repeated.class));
            case COORDINATE, GEOMETRY -> writeGeometry(type, value);
            default -> throw error(UNSUPPORTED, "Unsupported XTF type " + type.kind());
        }
    }

    private void writeText(Text text, boolean multiline) throws XMLStreamException {
        if (!multiline && !Xtf23Lexemes.singleLine(text.value())) {
            throw error(INVALID_WIRE, "Expected single-line XTF text");
        }
        xml.text(text.value());
    }

    private void writeEnumeration(Enumeration enumeration) throws XMLStreamException {
        String path = String.join(".", enumeration.path());
        if (!Xtf23Lexemes.enumeration(path) || !List.of(path.split("\\.")).equals(enumeration.path())) {
            throw error(INVALID_WIRE, "Invalid XTF enumeration path");
        }
        xml.text(path);
    }

    private String number(NumberValue number) {
        String lexeme = number.lexeme();
        if (lexeme.length() > Xtf23Lexemes.MAX_NUMERIC_LENGTH) {
            throw error(LIMIT_EXCEEDED, "XTF numeric lexeme exceeds the numeric length limit");
        }
        if (!Xtf23Lexemes.decimal(lexeme)) {
            throw error(INVALID_WIRE, "Invalid XTF decimal lexeme");
        }
        try {
            if (!Xtf23Lexemes.number(lexeme).equals(number)) {
                throw error(INVALID_WIRE, "XTF numeric lexeme differs from its exact value");
            }
        } catch (NumberFormatException e) {
            throw error(LIMIT_EXCEEDED, "XTF decimal exceeds the supported exact exponent range");
        }
        return lexeme;
    }

    private void writeStructure(StructureValueType expected, Structured value) throws XMLStreamException {
        var descriptor = plan.object(value.type()).orElseThrow(() -> error(INVALID_WIRE, "Unknown XTF structure type"));
        if (descriptor.kind() != SymbolKind.STRUCTURE || descriptor.modifiers().contains(Modifier.ABSTRACT)
                || !layout.matchesRoots(descriptor, expected.targetRoots())) {
            throw error(INVALID_WIRE, "Unexpected concrete XTF structure " + descriptor.name().declared());
        }
        xml.start(descriptor.name().declared().toString());
        writeFields(descriptor, value, Optional.empty());
        xml.end();
    }

    private void writeCollection(CollectionType type, Repeated collection) throws XMLStreamException {
        if (collection.kind() != type.collectionKind()) {
            throw error(INVALID_WIRE, "XTF collection kind differs from the model");
        }
        if (collection.values().isEmpty()) {
            throw error(UNSUPPORTED, "XTF cannot encode an explicit empty BAG or LIST");
        }
        if (!(layout.type(new TypeRef(plan.snapshotId(), type.elementType())) instanceof StructureValueType structure)) {
            throw error(UNSUPPORTED, "XTF 2.3 collections require structure values");
        }
        for (var value : collection.values()) {
            writeStructure(structure, requireValue(value, Structured.class));
        }
    }

    private void writeEmbedded(Xtf23Layout.Field field, TransferValue value) throws XMLStreamException {
        var variants = field.embeddings();
        var first = variants.getFirst();
        if (variants.stream().anyMatch(variant -> !variant.rootOpposite().symbol().equals(first.rootOpposite().symbol()))) {
            throw error(UNSUPPORTED, "Ambiguous embedded XTF role " + field.name());
        }
        if (value instanceof Reference reference) {
            var role = first.opposite().role();
            if (variants.stream().anyMatch(variant -> variant.opposite().role().external() != role.external()
                    || variant.opposite().role().ordered() != role.ordered())) {
                throw error(UNSUPPORTED, "Embedded XTF role needs a concrete association type: " + field.name());
            }
            writeReference(reference, role.external(), role.ordered());
            return;
        }
        var payload = requireValue(value, Structured.class);
        var variant = variants.stream().filter(candidate -> candidate.association().symbol().equals(payload.type()))
                .findFirst().orElseThrow(() -> error(INVALID_WIRE, "Unexpected embedded XTF association type"));
        var role = variant.opposite();
        var reference = requireValue(payload.values().get(role.symbol()), Reference.class);
        writeReference(reference, role.role().external(), role.role().ordered());
        xml.start(variant.association().name().declared().toString());
        writeFields(variant.association(), payload, Optional.of(role.symbol()));
        xml.end();
    }

    private void writeReference(Reference reference, boolean external, boolean ordered) throws XMLStreamException {
        var target = reference.target();
        String ref = identifier(target.value());
        if ((target instanceof ObjectOid) != Xtf23Reader.stableId(ref).isPresent()
                || reference.external() != external) {
            throw error(INVALID_WIRE, "XTF reference identity or EXTERNAL flag conflicts with its encoding");
        }
        xml.attribute("REF", ref);
        if (reference.targetBasket().isPresent()) {
            xml.attribute("BID", identifier(reference.targetBasket().orElseThrow().value()));
        }
        if (reference.orderPosition().isPresent()) {
            if (!ordered) {
                throw error(INVALID_WIRE, "ORDER_POS requires an ordered role");
            }
            var position = reference.orderPosition().orElseThrow();
            if (position.bitLength() > 4 * Xtf23Lexemes.MAX_NUMERIC_LENGTH) {
                throw error(LIMIT_EXCEEDED, "XTF ORDER_POS exceeds the numeric length limit");
            }
            String value = position.toString();
            if (value.length() > Xtf23Lexemes.MAX_NUMERIC_LENGTH) {
                throw error(LIMIT_EXCEEDED, "XTF ORDER_POS exceeds the numeric length limit");
            }
            xml.attribute("ORDER_POS", value);
        }
    }

    private void writeGeometry(TypeDescriptor type, TransferValue value) throws XMLStreamException {
        switch (type) {
            case CoordinateType coordinate -> writeCoordinate(requireValue(value, Coordinate.class), layout.dimensions(coordinate));
            case GeometryType line -> {
                var curve = layout.curveEncoding(line);
                switch (line.geometryKind()) {
                    case POLYLINE -> writePolyline(requireValue(value, Polyline.class), curve);
                    case SURFACE, AREA -> writeSurface(requireValue(value, Surface.class), curve);
                    default -> throw error(UNSUPPORTED, "XTF 2.3 does not encode " + line.geometryKind());
                }
            }
            default -> throw new IllegalStateException("Expected a plan geometry type");
        }
    }

    private void writeCoordinate(Coordinate coordinate, int dimensions) throws XMLStreamException {
        xml.start("COORD");
        writePosition(coordinate, "C", dimensions);
        xml.end();
    }

    private void writePosition(Coordinate coordinate, String prefix, int dimensions) throws XMLStreamException {
        if (coordinate.ordinates().size() != dimensions) {
            throw error(INVALID_WIRE, "Coordinate dimension differs from the XTF line or coordinate type");
        }
        for (int i = 0; i < dimensions; i++) {
            textElement(prefix + (i + 1), number(coordinate.ordinates().get(i)));
        }
    }

    private void writePolyline(Polyline line, Xtf23Layout.CurveEncoding curve) throws XMLStreamException {
        if (line.parts().size() != 1) {
            throw error(UNSUPPORTED, "XTF POLYLINE requires one unclipped sequence");
        }
        var part = line.parts().getFirst();
        if (part.segments().isEmpty()) {
            throw error(INVALID_WIRE, "XTF POLYLINE requires at least one segment after its start position");
        }
        xml.start("POLYLINE");
        writeCoordinate(part.start(), curve.dimensions());
        for (var segment : part.segments()) {
            switch (segment) {
                case Straight straight -> {
                    if (!curve.straights()) {
                        throw error(INVALID_WIRE, "Straight segment is not declared by the line type");
                    }
                    writeCoordinate(straight.end(), curve.dimensions());
                }
                case Arc arc -> {
                    if (!curve.arcs()) {
                        throw error(INVALID_WIRE, "ARC is not declared by the line type");
                    }
                    xml.start("ARC");
                    writePosition(arc.end(), "C", curve.dimensions());
                    writePosition(arc.support(), "A", 2);
                    if (arc.radius().isPresent()) {
                        textElement("R", number(arc.radius().orElseThrow()));
                    }
                    xml.end();
                }
            }
        }
        xml.end();
    }

    private void writeSurface(Surface surface, Xtf23Layout.CurveEncoding curve) throws XMLStreamException {
        xml.start("SURFACE");
        writeBoundary(surface.shell(), curve);
        for (var hole : surface.holes()) {
            writeBoundary(hole, curve);
        }
        xml.end();
    }

    private void writeBoundary(Ring boundary, Xtf23Layout.CurveEncoding curve) throws XMLStreamException {
        xml.start("BOUNDARY");
        for (var line : boundary.lines()) {
            writePolyline(line, curve);
        }
        xml.end();
    }

    private void textElement(String name, String value) throws XMLStreamException {
        xml.start(name);
        xml.text(value);
        xml.end();
    }

    private String identifier(String value) {
        if (value.length() > Xtf23Reader.MAX_IDENTIFIER) {
            throw error(LIMIT_EXCEEDED, "XTF identifier exceeds the length limit");
        }
        if (!Xtf23Reader.XML_ID.matcher(value).matches()) {
            throw error(INVALID_WIRE, "Invalid XTF identifier");
        }
        int separator = value.indexOf(':');
        if (separator >= 0 && !oidAliases.contains(value.substring(0, separator))) {
            throw error(INVALID_WIRE, "Unknown XTF OIDSPACE prefix");
        }
        return value;
    }

    private static String required(Optional<String> value, String name) {
        return value.orElseThrow(() -> error(INVALID_WIRE, "Missing XTF " + name));
    }

    private static <T extends TransferValue> T requireValue(TransferValue value, Class<T> expected) {
        if (value == Undefined.INSTANCE) {
            throw error(UNSUPPORTED, "XTF has no explicit undefined value");
        }
        if (!expected.isInstance(value)) {
            throw error(INVALID_WIRE, "XTF field requires " + expected.getSimpleName());
        }
        return expected.cast(value);
    }
}
