package guru.interlis.xtf23;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.BlackboxType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.Modifier;
import guru.interlis.model.OidType;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferName;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static guru.interlis.transfer.TransferEvent.*;
import static guru.interlis.transfer.TransferIdentity.*;
import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static javax.xml.stream.XMLStreamConstants.*;

/**
 * Pulls one XTF 2.3 FULL transfer from a caller-owned byte stream. Constructing this reader
 * performs no input. Close releases the parser without closing the supplied stream.
 * Events are immutable and can be retained after the reader advances.
 * The XML reader bounds depth to 128 elements, scalar text to 1,048,576 UTF-16
 * code units, and input read during any individual parser advance to 8 MiB.
 * One object, including its nested values, is limited to 1,000,000 XML events and
 * 16,777,216 UTF-16 code units of XML text and attribute values.
 * The reader holds a transfer-wide index of occurrence IDs whose size grows with
 * the number of distinct BIDs and TIDs.
 */
public final class Xtf23Reader extends TransferReader {
    static final String NAMESPACE = "http://www.interlis.ch/INTERLIS2.3";
    static final Pattern XML_ID = Pattern.compile("(?:" + Xtf23OidSpace.NC_NAME + ":)?[A-Za-z0-9_][A-Za-z0-9_.-]*");
    private static final String VERSION = "2.3";
    static final int MAX_OID_SPACES = 4096;
    static final int MAX_IDENTIFIER = 4096;

    private final TransferPlan plan;
    private final Xtf23Input xml;
    private final Optional<SourceId> source;
    private final Map<String, ModelDescriptor> models;
    private final Map<String, TopicDescriptor> topics;
    private final Map<String, ObjectDescriptor> objects;
    private final Xtf23Layout layout;
    private final Xtf23IdentityScope identities = new Xtf23IdentityScope();
    private final Map<SymbolRef, Map<String, PositionedField>> fieldIndexes = new HashMap<>();
    private Map<String, Xtf23OidSpace> oidSpaces = Map.of();
    private Set<SymbolRef> activeTypes = Set.of();
    private List<Xtf23OidSpace> oidSpaceDeclarations = List.of();
    private State state = State.BEFORE_TRANSFER;
    private BasketId basket;

    /** Creates a reader over the nonnull plan and caller-owned input. */
    public Xtf23Reader(TransferPlan plan, InputStream input) {
        this(plan, input, Optional.empty());
    }

    /** Creates a reader with an optional nonnull opaque source identity for events. */
    public Xtf23Reader(TransferPlan plan, InputStream input, Optional<SourceId> source) {
        super(plan);
        this.plan = plan;
        this.source = Objects.requireNonNull(source, "source");
        xml = new Xtf23Input(input);
        models = index(plan.models(), ModelDescriptor::name);
        topics = index(plan.topics(), TopicDescriptor::name);
        objects = index(plan.objects(), ObjectDescriptor::name);
        layout = new Xtf23Layout(plan);
    }

    /** Returns the immutable, ordered OIDSPACE declarations after reading the header.
     * An empty list represents no declarations, including before the first read. */
    public List<Xtf23OidSpace> oidSpaces() {
        return oidSpaceDeclarations;
    }

    private static <T> Map<String, T> index(List<T> descriptors, Function<T, TransferName> names) {
        return descriptors.stream().collect(Collectors.toUnmodifiableMap(
                value -> names.apply(value).declared().toString(), Function.identity()));
    }

    @Override
    protected Optional<TransferEvent> readEvent(CancellationToken cancellation) {
        xml.begin(cancellation);
        try {
            return switch (state) {
                case BEFORE_TRANSFER -> Optional.of(startTransfer());
                case BETWEEN_BASKETS -> Optional.of(basketOrEndTransfer());
                case IN_BASKET -> Optional.of(objectOrEndBasket());
                case DONE -> Optional.empty();
            };
        } catch (XMLStreamException failure) {
            clearTransferState();
            throw xml.translate(failure);
        } catch (RuntimeException failure) {
            clearTransferState();
            throw failure instanceof Xtf23Exception xtfFailure ? xml.locate(xtfFailure) : failure;
        } finally {
            xml.finish();
        }
    }

    @Override
    protected void release() {
        clearTransferState();
        xml.close();
    }

    private void clearTransferState() {
        basket = null;
        oidSpaces = Map.of();
        identities.release();
        clearBasketState();
    }

    private void clearBasketState() {
        activeTypes = Set.of();
        fieldIndexes.clear();
    }

    private StartTransfer startTransfer() throws XMLStreamException {
        xml.open();
        require(START_ELEMENT, xml.nextTag(), "TRANSFER");
        attributes(true);
        require(START_ELEMENT, xml.nextTag(), "HEADERSECTION");
        var header = attributes(false, "VERSION", "SENDER");
        if (!VERSION.equals(required(header, "VERSION"))) {
            throw xml.error(INVALID_WIRE, "Expected XTF header VERSION " + VERSION);
        }
        String sender = required(header, "SENDER");
        require(START_ELEMENT, xml.nextTag(), "MODELS");
        attributes(false);
        var declarations = readModels();

        int token = xml.nextTag();
        if (is(START_ELEMENT, token, "ALIAS")) {
            attributes(false);
            if (!is(END_ELEMENT, xml.nextTag(), "ALIAS")) {
                throw xml.error(UNSUPPORTED, "Nonempty polymorphic ALIAS tables are unsupported");
            }
            token = xml.nextTag();
        }
        Map<String, Xtf23OidSpace> spaces = Map.of();
        if (is(START_ELEMENT, token, "OIDSPACES")) {
            spaces = readOidSpaces();
            token = xml.nextTag();
        }
        Optional<String> comment = Optional.empty();
        if (is(START_ELEMENT, token, "COMMENT")) {
            attributes(false);
            comment = Optional.of(xml.text());
            token = xml.nextTag();
        }
        require(END_ELEMENT, token, "HEADERSECTION");
        require(START_ELEMENT, xml.nextTag(), "DATASECTION");
        attributes(false);
        oidSpaces = spaces;
        oidSpaceDeclarations = List.copyOf(spaces.values());
        state = State.BETWEEN_BASKETS;
        return new StartTransfer(source, Optional.of(sender), comment, declarations);
    }

    private List<ModelDeclaration> readModels() throws XMLStreamException {
        var declarations = new ArrayList<ModelDeclaration>();
        var declared = new HashSet<SymbolRef>();
        int token = xml.nextTag();
        while (!is(END_ELEMENT, token, "MODELS")) {
            require(START_ELEMENT, token, "MODEL");
            var attributes = attributes(false, "NAME", "VERSION", "URI");
            String name = required(attributes, "NAME");
            String version = required(attributes, "VERSION");
            String issuer = required(attributes, "URI");
            var model = models.get(name);
            if (model == null) {
                throw xml.error(INVALID_WIRE, "Unknown XTF model " + name);
            }
            if (model.languageVersion() != LanguageVersion.ILI23) {
                throw xml.error(UNSUPPORTED, "Model " + name + " does not use INTERLIS 2.3");
            }
            var symbol = (ModelSymbol) plan.symbol(model.symbol()).known().orElseThrow();
            if (symbol.translation().isPresent()) {
                throw xml.error(UNSUPPORTED, "Translated models require an XTF ALIAS table");
            }
            if (!version.equals(model.modelVersion()) || !issuer.equals(symbol.issuer())) {
                throw xml.error(INVALID_WIRE, "XTF declaration conflicts with model " + name);
            }
            if (!declared.add(model.symbol())) {
                throw xml.error(INVALID_WIRE, "Repeated XTF model declaration " + name);
            }
            require(END_ELEMENT, xml.nextTag(), "MODEL");
            declarations.add(new ModelDeclaration(model.symbol(), model.languageVersion(),
                    Optional.of(version), Optional.of(issuer)));
            token = xml.nextTag();
        }
        if (declarations.isEmpty()) {
            throw xml.error(INVALID_WIRE, "MODELS requires at least one MODEL");
        }
        return declarations;
    }

    private Map<String, Xtf23OidSpace> readOidSpaces() throws XMLStreamException {
        attributes(false);
        var spaces = new LinkedHashMap<String, Xtf23OidSpace>();
        int token = xml.nextTag();
        while (!is(END_ELEMENT, token, "OIDSPACES")) {
            if (spaces.size() == MAX_OID_SPACES) {
                throw xml.error(LIMIT_EXCEEDED, "Too many XTF OIDSPACE declarations");
            }
            require(START_ELEMENT, token, "OIDSPACE");
            var attributes = attributes(false, "NAME", "OIDDOMAIN");
            String alias = required(attributes, "NAME");
            String domain = required(attributes, "OIDDOMAIN");
            Xtf23OidSpace space;
            try {
                space = new Xtf23OidSpace(alias, domain);
            } catch (IllegalArgumentException e) {
                throw xml.error(INVALID_WIRE, e.getMessage());
            }
            if (spaces.putIfAbsent(alias, space) != null) {
                throw xml.error(INVALID_WIRE, "Repeated OIDSPACE alias " + alias);
            }
            require(END_ELEMENT, xml.nextTag(), "OIDSPACE");
            token = xml.nextTag();
        }
        if (spaces.isEmpty()) {
            throw xml.error(INVALID_WIRE, "OIDSPACES requires at least one OIDSPACE");
        }
        return spaces;
    }

    private TransferEvent basketOrEndTransfer() throws XMLStreamException {
        int token = xml.nextTag();
        if (is(END_ELEMENT, token, "DATASECTION")) {
            require(END_ELEMENT, xml.nextTag(), "TRANSFER");
            if (xml.nextTag() != END_DOCUMENT) {
                throw xml.error(INVALID_WIRE, "Unexpected XML after the XTF transfer");
            }
            state = State.DONE;
            clearTransferState();
            return new EndTransfer();
        }
        String tag = elementName(token);
        var topic = topic(tag);
        var attributes = attributes(false, "BID", "TOPICS", "KIND", "STARTSTATE", "ENDSTATE", "CONSISTENCY");
        switch (attributes.getOrDefault("KIND", "FULL")) {
            case "FULL" -> { }
            case "INITIAL", "UPDATE" -> throw xml.error(UNSUPPORTED, "Incremental XTF baskets are unsupported");
            default -> throw xml.error(INVALID_WIRE, "Unknown XTF basket KIND");
        }
        if (attributes.containsKey("STARTSTATE") || attributes.containsKey("ENDSTATE")) {
            throw xml.error(INVALID_WIRE, "A FULL basket cannot carry incremental state");
        }
        var id = basketId(required(attributes, "BID"));
        var extensions = new ArrayList<SymbolRef>();
        if (attributes.containsKey("TOPICS")) {
            var seen = new HashSet<SymbolRef>();
            for (String name : attributes.get("TOPICS").split(",", -1)) {
                SymbolRef extension = topic(name).symbol();
                if (!seen.add(extension)) {
                    throw xml.error(INVALID_WIRE, "Repeated XTF extension topic " + name);
                }
                extensions.add(extension);
            }
        }
        var consistency = consistency(attributes);
        if (!identities.claim(id)) {
            throw xml.error(DUPLICATE_ID, "XTF FULL BID/TID collision for " + id.value());
        }
        var allowed = new HashSet<>(topic.objectTypes());
        for (var extension : extensions) {
            allowed.addAll(plan.topic(extension).orElseThrow().objectTypes());
        }
        activeTypes = Set.copyOf(allowed);
        basket = id;
        state = State.IN_BASKET;
        return new StartBasket(id, topic.symbol(), extensions, BasketKind.FULL, consistency,
                Optional.empty(), Optional.empty(), stableId(id.value()), List.of());
    }

    private TransferEvent objectOrEndBasket() throws XMLStreamException {
        int token = xml.nextTag();
        if (token == END_ELEMENT) {
            var event = new EndBasket(basket);
            basket = null;
            clearBasketState();
            state = State.BETWEEN_BASKETS;
            return event;
        }
        String tag = elementName(token);
        var descriptor = objects.get(tag);
        if (descriptor == null || !descriptor.transferable()) {
            throw xml.error(INVALID_WIRE, "Unknown or nontransferable XTF object tag " + tag);
        }
        if (layout.embedded(descriptor).isPresent()) {
            throw xml.error(INVALID_WIRE, "Embedded XTF association cannot occur as a standalone object: " + tag);
        }
        try {
            xml.beginObject();
            return readObject(descriptor, tag);
        } finally {
            xml.endObject();
        }
    }

    private ObjectEvent readObject(ObjectDescriptor descriptor, String tag) throws XMLStreamException {
        var attributes = attributes(false, "TID", "BID", "OPERATION", "CONSISTENCY");
        if (attributes.containsKey("OPERATION")) {
            throw xml.error(INVALID_WIRE, "A FULL object cannot carry an incremental OPERATION");
        }
        boolean requiresTid = descriptor.kind() == SymbolKind.CLASS || layout.identified(descriptor);
        if (requiresTid != attributes.containsKey("TID")) {
            throw xml.error(INVALID_WIRE, requiresTid ? "Missing XTF attribute TID"
                    : "A link without its own identification cannot carry TID");
        }
        var tid = Optional.ofNullable(attributes.get("TID"))
                .map(value -> new TransferId(identifier(value), IdOrigin.SOURCE));
        tid.ifPresent(value -> {
            if (!identities.claim(value)) {
                throw xml.error(DUPLICATE_ID, "XTF FULL BID/TID collision for " + value.value());
            }
        });
        var originalBasket = Optional.ofNullable(attributes.get("BID")).map(this::basketId);
        var consistency = consistency(attributes);
        var identity = new TransferIdentity(basket, originalBasket, tid,
                tid.flatMap(value -> stableId(value.value())).map(ObjectOid::new), source);
        var data = readFields(descriptor, tag, false);
        return new ObjectEvent(new TransferObject(identity,
                new TransferValue.Structured(descriptor.symbol(), data)), Optional.empty(), consistency);
    }

    private record PositionedField(Xtf23Layout.Field field, int position) { }

    private static Map<String, PositionedField> indexFields(List<Xtf23Layout.Field> fields) {
        var index = new HashMap<String, PositionedField>();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            if (index.putIfAbsent(field.name(), new PositionedField(field, i)) != null) {
                index.put(field.name(), new PositionedField(field, -1));
            }
        }
        return index;
    }

    private Map<SymbolRef, TransferValue> readFields(ObjectDescriptor descriptor, String tag, boolean payloadOnly)
            throws XMLStreamException {
        var fields = payloadOnly ? indexFields(layout.fields(descriptor, activeTypes, true))
                : fieldIndexes.computeIfAbsent(descriptor.symbol(), unused -> indexFields(layout.fields(descriptor, activeTypes, false)));
        var data = new LinkedHashMap<SymbolRef, TransferValue>();
        int lastPosition = -1;
        int token = xml.nextTag();
        while (!is(END_ELEMENT, token, tag)) {
            String name = elementName(token);
            var positioned = fields.get(name);
            if (positioned == null) {
                throw xml.error(INVALID_WIRE, "Unknown XTF field " + name + " on " + tag);
            }
            if (positioned.position() < 0) {
                throw xml.error(UNSUPPORTED, "Ambiguous XTF field " + name + " on " + tag);
            }
            if (positioned.position() <= lastPosition) {
                throw xml.error(INVALID_WIRE, "Repeated or out-of-order XTF field " + name);
            }
            lastPosition = positioned.position();
            var field = positioned.field();
            var value = field.embeddings().isEmpty() ? switch (field.slot()) {
                case AttributeSlot attribute -> readValue(layout.type(attribute.effectiveType()), name);
                case RoleSlot role -> readRole(role, name);
            } : readEmbedded(field, name);
            data.put(field.slot().symbol(), value);
            token = xml.nextTag();
        }
        return data;
    }

    private TransferValue readValue(TypeDescriptor type, String tag) throws XMLStreamException {
        return switch (type.kind()) {
            case TEXT -> readText(((TextType) type).textKind() == TextKind.MTEXT);
            case FORMATTED -> readText(false);
            case BOOLEAN -> readBoolean();
            case ENUMERATION, ALIGNMENT -> readEnumeration();
            case NUMERIC -> {
                attributes(false);
                yield readNumber();
            }
            case REFERENCE -> {
                var result = reference(attributes(false, "REF", "BID"), ((ReferenceType) type).external(), false);
                require(END_ELEMENT, xml.nextTag(), tag);
                yield result;
            }
            case STRUCTURE_VALUE -> {
                attributes(false);
                var result = readStructure(((StructureValueType) type).targetRoots(), xml.nextTag());
                require(END_ELEMENT, xml.nextTag(), tag);
                yield result;
            }
            case COLLECTION -> readCollection((CollectionType) type, tag);
            case BLACKBOX -> readBlackbox((BlackboxType) type, tag);
            case OID -> readOid((OidType) type, tag);
            case CLASS_VALUE -> new TransferValue.ClassName(readModelPath(false));
            case ATTRIBUTE_PATH -> new TransferValue.AttributePath(readModelPath(true));
            case COORDINATE, GEOMETRY -> readGeometry(type, tag);
            default -> throw xml.error(UNSUPPORTED, "Unsupported XTF type " + type.kind() + " for " + tag);
        };
    }

    private TransferValue.Oid readOid(OidType type, String tag) throws XMLStreamException {
        String value = identifier(required(attributes(false, "OID"), "OID"));
        if (!Xtf23Lexemes.oidValue(type, value)) {
            throw xml.error(INVALID_WIRE, "Invalid XTF OID value for " + type.oidKind());
        }
        require(END_ELEMENT, xml.nextTag(), tag);
        return new TransferValue.Oid(value);
    }

    private QualifiedName readModelPath(boolean attribute) throws XMLStreamException {
        attributes(false);
        String value = xml.text();
        if (value.length() > Xtf23Lexemes.MAX_MODEL_PATH_LENGTH) {
            throw xml.error(LIMIT_EXCEEDED, "XTF model path exceeds the length limit");
        }
        var segments = List.of(value.split("\\.", -1));
        if (!Xtf23Lexemes.modelPath(segments, attribute)) {
            throw xml.error(INVALID_WIRE, "Invalid qualified XTF model path");
        }
        return new QualifiedName(segments);
    }

    private TransferValue readBlackbox(BlackboxType type, String tag) throws XMLStreamException {
        attributes(false);
        int token = xml.nextTag();
        var value = switch (type.blackboxKind()) {
            case BINARY -> {
                require(START_ELEMENT, token, "BINBLBOX");
                attributes(false);
                String encoded = xml.text();
                var compact = new StringBuilder(encoded.length());
                for (int i = 0; i < encoded.length(); i++) {
                    char c = encoded.charAt(i);
                    if (c != ' ' && c != '\r' && c != '\n' && c != '\t') {
                        compact.append(c);
                    }
                }
                String base64 = compact.toString();
                byte[] decoded;
                try {
                    decoded = Base64.getDecoder().decode(base64);
                } catch (IllegalArgumentException failure) {
                    throw xml.error(INVALID_WIRE, "Invalid XTF BINBLBOX Base64");
                }
                if (!Base64.getEncoder().encodeToString(decoded).equals(base64)) {
                    throw xml.error(INVALID_WIRE, "Invalid padding or unused bits in XTF BINBLBOX Base64");
                }
                yield new TransferValue.Binary(decoded);
            }
            case XML -> {
                require(START_ELEMENT, token, "XMLBLBOX");
                attributes(false);
                yield new TransferValue.XmlFragment(Xtf23XmlFragment.read(xml));
            }
        };
        require(END_ELEMENT, xml.nextTag(), tag);
        return value;
    }

    private TransferValue.Text readText(boolean multiline) throws XMLStreamException {
        attributes(false);
        String value = xml.text();
        if (!multiline && !Xtf23Lexemes.singleLine(value)) {
            throw xml.error(INVALID_WIRE, "Expected single-line XTF text");
        }
        return new TransferValue.Text(value);
    }

    private TransferValue.BooleanValue readBoolean() throws XMLStreamException {
        attributes(false);
        return switch (xml.text()) {
            case "true" -> new TransferValue.BooleanValue(true);
            case "false" -> new TransferValue.BooleanValue(false);
            default -> throw xml.error(INVALID_WIRE, "Expected XTF BOOLEAN true or false");
        };
    }

    private TransferValue.Enumeration readEnumeration() throws XMLStreamException {
        attributes(false);
        String value = xml.text();
        if (!Xtf23Lexemes.enumeration(value)) {
            throw xml.error(INVALID_WIRE, "Invalid XTF enumeration path");
        }
        return new TransferValue.Enumeration(List.of(value.split("\\.")));
    }

    private TransferValue.NumberValue readNumber() throws XMLStreamException {
        String value = xml.text();
        if (value.length() > Xtf23Lexemes.MAX_NUMERIC_LENGTH) {
            throw xml.error(LIMIT_EXCEEDED, "XTF numeric lexeme exceeds the numeric length limit");
        }
        if (Xtf23Lexemes.nonDecimalNumber(value)) {
            throw xml.error(UNSUPPORTED, "Nondecimal XTF numbers are unsupported");
        }
        if (!Xtf23Lexemes.decimal(value)) {
            throw xml.error(INVALID_WIRE, "Invalid XTF decimal value");
        }
        try {
            return Xtf23Lexemes.number(value);
        } catch (NumberFormatException e) {
            throw xml.error(LIMIT_EXCEEDED, "XTF decimal exceeds the supported exact exponent range");
        }
    }

    private TransferGeometry readGeometry(TypeDescriptor type, String tag) throws XMLStreamException {
        attributes(false);
        int token = xml.nextTag();
        TransferGeometry geometry = switch (type) {
            case CoordinateType coordinate -> readCoordinate(token, layout.dimensions(coordinate));
            case GeometryType line -> {
                var curve = layout.curveEncoding(line);
                yield switch (line.geometryKind()) {
                    case POLYLINE -> readPolyline(token, curve);
                    case SURFACE, AREA -> readSurface(token, curve);
                    default -> throw xml.error(UNSUPPORTED, "XTF 2.3 does not encode " + line.geometryKind());
                };
            }
            default -> throw new IllegalStateException("Expected a plan geometry type");
        };
        require(END_ELEMENT, xml.nextTag(), tag);
        return geometry;
    }

    private TransferGeometry.Coordinate readCoordinate(int token, int dimensions) throws XMLStreamException {
        require(START_ELEMENT, token, "COORD");
        attributes(false);
        var coordinate = readPosition("C", dimensions);
        require(END_ELEMENT, xml.nextTag(), "COORD");
        return coordinate;
    }

    private TransferGeometry.Coordinate readPosition(String prefix, int dimensions) throws XMLStreamException {
        var ordinates = new ArrayList<TransferValue.NumberValue>(dimensions);
        for (int axis = 1; axis <= dimensions; axis++) {
            require(START_ELEMENT, xml.nextTag(), prefix + axis);
            attributes(false);
            ordinates.add(readNumber());
        }
        return new TransferGeometry.Coordinate(ordinates);
    }

    private TransferGeometry.Polyline readPolyline(int token, Xtf23Layout.CurveEncoding curve) throws XMLStreamException {
        require(START_ELEMENT, token, "POLYLINE");
        attributes(false);
        token = xml.nextTag();
        rejectGeometryExtension(token);
        Optional<TransferValue.Structured> lineAttributes = Optional.empty();
        if (!curve.lineAttributeRoots().isEmpty()) {
            require(START_ELEMENT, token, "LINEATTR");
            attributes(false);
            lineAttributes = Optional.of(readStructure(curve.lineAttributeRoots(), xml.nextTag()));
            require(END_ELEMENT, xml.nextTag(), "LINEATTR");
            token = xml.nextTag();
            rejectGeometryExtension(token);
        }
        var start = readCoordinate(token, curve.dimensions());
        var segments = new ArrayList<TransferGeometry.Segment>();
        token = xml.nextTag();
        while (!is(END_ELEMENT, token, "POLYLINE")) {
            rejectGeometryExtension(token);
            String segment = elementName(token);
            segments.add(switch (segment) {
                case "COORD" -> new TransferGeometry.Straight(readCoordinate(token, curve.dimensions()));
                case "ARC" -> readArc(curve.dimensions());
                default -> throw xml.error(INVALID_WIRE, "Unexpected XTF line segment " + segment);
            });
            token = xml.nextTag();
        }
        if (segments.isEmpty()) {
            throw xml.error(INVALID_WIRE, "XTF POLYLINE requires at least one segment after its start position");
        }
        return new TransferGeometry.Polyline(List.of(new TransferGeometry.CurvePart(start, segments)), lineAttributes);
    }

    private TransferGeometry.Arc readArc(int dimensions) throws XMLStreamException {
        attributes(false);
        var end = readPosition("C", dimensions);
        var support = readPosition("A", 2);
        Optional<TransferValue.NumberValue> radius = Optional.empty();
        int token = xml.nextTag();
        if (is(START_ELEMENT, token, "R")) {
            attributes(false);
            var value = readNumber();
            if (value.exactValue().signum() == 0) {
                throw xml.error(INVALID_WIRE, "XTF ARC radius must be nonzero");
            }
            radius = Optional.of(value);
            token = xml.nextTag();
        }
        require(END_ELEMENT, token, "ARC");
        return new TransferGeometry.Arc(support, end, radius);
    }

    private TransferGeometry.Surface readSurface(int token, Xtf23Layout.CurveEncoding curve) throws XMLStreamException {
        require(START_ELEMENT, token, "SURFACE");
        attributes(false);
        var shell = readBoundary(xml.nextTag(), curve);
        var holes = new ArrayList<TransferGeometry.Ring>();
        token = xml.nextTag();
        while (!is(END_ELEMENT, token, "SURFACE")) {
            holes.add(readBoundary(token, curve));
            token = xml.nextTag();
        }
        return new TransferGeometry.Surface(shell, holes);
    }

    private TransferGeometry.Ring readBoundary(int token, Xtf23Layout.CurveEncoding curve) throws XMLStreamException {
        rejectGeometryExtension(token);
        require(START_ELEMENT, token, "BOUNDARY");
        attributes(false);
        var lines = new ArrayList<TransferGeometry.Polyline>();
        token = xml.nextTag();
        while (!is(END_ELEMENT, token, "BOUNDARY")) {
            lines.add(readPolyline(token, curve));
            token = xml.nextTag();
        }
        if (lines.isEmpty()) {
            throw xml.error(INVALID_WIRE, "XTF BOUNDARY requires at least one POLYLINE");
        }
        return new TransferGeometry.Ring(lines);
    }

    private void rejectGeometryExtension(int token) {
        if (is(START_ELEMENT, token, "CLIPPED")) {
            throw xml.error(UNSUPPORTED, "Unsupported XTF geometry element " + elementName(token));
        }
    }

    private TransferValue.Structured readStructure(List<SymbolId> roots, int token) throws XMLStreamException {
        String tag = elementName(token);
        var descriptor = objects.get(tag);
        if (descriptor == null || descriptor.kind() != SymbolKind.STRUCTURE
                || descriptor.modifiers().contains(Modifier.ABSTRACT) || !layout.matchesRoots(descriptor, roots)) {
            throw xml.error(INVALID_WIRE, "Unexpected concrete XTF structure " + tag);
        }
        attributes(false);
        return new TransferValue.Structured(descriptor.symbol(), readFields(descriptor, tag, false));
    }

    private TransferValue.Repeated readCollection(CollectionType collection, String tag) throws XMLStreamException {
        attributes(false);
        if (!(layout.type(new TypeRef(plan.snapshotId(), collection.elementType())) instanceof StructureValueType structure)) {
            throw xml.error(UNSUPPORTED, "XTF 2.3 collections require structure values");
        }
        var values = new ArrayList<TransferValue>();
        int token = xml.nextTag();
        while (!is(END_ELEMENT, token, tag)) {
            values.add(readStructure(structure.targetRoots(), token));
            token = xml.nextTag();
        }
        if (values.isEmpty()) {
            throw xml.error(INVALID_WIRE, "Present XTF collection requires at least one structure");
        }
        return new TransferValue.Repeated(collection.collectionKind(), values);
    }

    private TransferValue.Reference readRole(RoleSlot role, String tag) throws XMLStreamException {
        var value = reference(attributes(false, "REF", "BID", "ORDER_POS"), role.role().external(), role.role().ordered());
        require(END_ELEMENT, xml.nextTag(), tag);
        return value;
    }

    private TransferValue readEmbedded(Xtf23Layout.Field field, String tag) throws XMLStreamException {
        var attributes = attributes(false, "REF", "BID", "ORDER_POS");
        var variants = field.embeddings();
        var first = variants.getFirst();
        if (variants.stream().anyMatch(value -> !value.rootOpposite().symbol().equals(first.rootOpposite().symbol()))) {
            throw xml.error(UNSUPPORTED, "Ambiguous embedded XTF role " + tag);
        }
        int token = xml.nextTag();
        if (is(END_ELEMENT, token, tag)) {
            var role = first.opposite().role();
            if (variants.stream().anyMatch(value -> value.opposite().role().external() != role.external()
                    || value.opposite().role().ordered() != role.ordered())) {
                throw xml.error(UNSUPPORTED, "Embedded XTF role needs a concrete association tag: " + tag);
            }
            return reference(attributes, role.external(), role.ordered());
        }
        String associationTag = elementName(token);
        var variant = variants.stream().filter(value -> value.association().name().declared().toString().equals(associationTag))
                .findFirst().orElseThrow(() -> xml.error(INVALID_WIRE, "Unexpected embedded XTF association " + associationTag));
        attributes(false);
        var role = variant.opposite();
        var reference = reference(attributes, role.role().external(), role.role().ordered());
        var fields = readFields(variant.association(), associationTag, true);
        require(END_ELEMENT, xml.nextTag(), tag);
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        values.put(role.symbol(), reference);
        values.putAll(fields);
        return new TransferValue.Structured(variant.association().symbol(), values);
    }

    private TransferValue.Reference reference(Map<String, String> attributes, boolean external, boolean ordered) {
        String ref = identifier(required(attributes, "REF"));
        ObjectKey key = stableId(ref).<ObjectKey>map(ObjectOid::new).orElseGet(() -> new TransferId(ref, IdOrigin.SOURCE));
        var targetBasket = Optional.ofNullable(attributes.get("BID")).map(this::basketId);
        var position = Optional.ofNullable(attributes.get("ORDER_POS")).map(value -> {
            if (value.length() > Xtf23Lexemes.MAX_NUMERIC_LENGTH) {
                throw xml.error(LIMIT_EXCEEDED, "XTF ORDER_POS exceeds the numeric length limit");
            }
            if (!ordered || !Xtf23Lexemes.POSITION.matcher(value).matches()) {
                throw xml.error(INVALID_WIRE, "ORDER_POS requires an ordered role and a positive integer");
            }
            var parsed = new BigInteger(value);
            if (parsed.signum() <= 0) {
                throw xml.error(INVALID_WIRE, "ORDER_POS requires a positive integer");
            }
            return parsed;
        });
        return new TransferValue.Reference(key, targetBasket, external, position);
    }

    private TopicDescriptor topic(String name) {
        var topic = topics.get(name);
        if (topic == null) {
            throw xml.error(INVALID_WIRE, "Unknown XTF topic " + name);
        }
        return topic;
    }

    private BasketId basketId(String value) {
        return new BasketId(identifier(value), IdOrigin.SOURCE);
    }

    private String identifier(String value) {
        if (value.length() > MAX_IDENTIFIER) {
            throw xml.error(LIMIT_EXCEEDED, "XTF identifier exceeds " + MAX_IDENTIFIER + " characters");
        }
        if (!XML_ID.matcher(value).matches()) {
            throw xml.error(INVALID_WIRE, "Invalid XTF identifier");
        }
        int separator = value.indexOf(':');
        if (separator >= 0 && !oidSpaces.containsKey(value.substring(0, separator))) {
            throw xml.error(INVALID_WIRE, "Unknown XTF OIDSPACE prefix");
        }
        return value;
    }

    static Optional<String> stableId(String value) {
        return value.indexOf(':') >= 0 ? Optional.of(value) : Optional.empty();
    }

    private Consistency consistency(Map<String, String> attributes) {
        try {
            return Consistency.valueOf(attributes.getOrDefault("CONSISTENCY", "COMPLETE"));
        } catch (IllegalArgumentException e) {
            throw xml.error(INVALID_WIRE, "Unknown XTF CONSISTENCY value");
        }
    }

    private boolean is(int event, int token, String tag) {
        var cursor = xml.cursor();
        return event == token && NAMESPACE.equals(cursor.getNamespaceURI()) && tag.equals(cursor.getLocalName());
    }

    private void require(int event, int token, String tag) {
        if (!is(event, token, tag)) {
            throw xml.error(INVALID_WIRE, "Expected " + (event == END_ELEMENT ? "end of " : "") + tag);
        }
    }

    private String elementName(int token) {
        var cursor = xml.cursor();
        if (token != START_ELEMENT || !NAMESPACE.equals(cursor.getNamespaceURI())) {
            throw xml.error(INVALID_WIRE, "Expected an element in the XTF 2.3 namespace");
        }
        return cursor.getLocalName();
    }

    private Map<String, String> attributes(boolean allowSchemaLocation, String... names) {
        var allowed = List.of(names);
        var values = new HashMap<String, String>();
        var cursor = xml.cursor();
        for (int index = 0; index < cursor.getAttributeCount(); index++) {
            var attribute = cursor.getAttributeName(index);
            String name = attribute.getLocalPart();
            String value = cursor.getAttributeValue(index);
            if (value.length() > Xtf23Input.MAX_TEXT) {
                throw xml.error(LIMIT_EXCEEDED, "XTF attribute exceeds the text limit");
            }
            if (allowSchemaLocation && XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(attribute.getNamespaceURI())
                    && name.equals("schemaLocation")) {
                continue;
            }
            if (!attribute.getNamespaceURI().isEmpty() || !allowed.contains(name)) {
                throw xml.error(INVALID_WIRE, "Unexpected XTF attribute " + attribute);
            }
            if (!Xtf23Lexemes.singleLine(value)) {
                throw xml.error(INVALID_WIRE, "XTF attribute " + name + " must be single-line text");
            }
            values.put(name, value);
        }
        return values;
    }

    private String required(Map<String, String> attributes, String name) {
        var value = attributes.get(name);
        if (value == null) {
            throw xml.error(INVALID_WIRE, "Missing XTF attribute " + name);
        }
        return value;
    }

    private enum State {
        BEFORE_TRANSFER, BETWEEN_BASKETS, IN_BASKET, DONE
    }
}
