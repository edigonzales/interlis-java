package guru.interlis.xtf23;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferName;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 */
public final class Xtf23Reader extends TransferReader {
    static final String NAMESPACE = "http://www.interlis.ch/INTERLIS2.3";
    static final Pattern XML_ID = Pattern.compile("(?:" + Xtf23OidSpace.NC_NAME + ":)?[A-Za-z0-9_][A-Za-z0-9_.-]*");
    private static final String VERSION = "2.3";
    private static final int MAX_OID_SPACES = 4096;
    private static final int MAX_IDENTIFIER = 4096;

    private final TransferPlan plan;
    private final Xtf23Input xml;
    private final Optional<SourceId> source;
    private final Map<String, ModelDescriptor> models;
    private final Map<String, TopicDescriptor> topics;
    private final Map<String, ObjectDescriptor> objects;
    private Map<String, Xtf23OidSpace> oidSpaces = Map.of();
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
            throw xml.translate(failure);
        } finally {
            xml.finish();
        }
    }

    @Override
    protected void release() {
        basket = null;
        oidSpaces = Map.of();
        xml.close();
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
            oidSpaces = Map.of();
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
            state = State.BETWEEN_BASKETS;
            return event;
        }
        String tag = elementName(token);
        var descriptor = objects.get(tag);
        if (descriptor == null || !descriptor.transferable()) {
            throw xml.error(INVALID_WIRE, "Unknown or nontransferable XTF object tag " + tag);
        }
        if (descriptor.kind() != SymbolKind.CLASS) {
            throw xml.error(UNSUPPORTED, "XTF association payloads are unsupported");
        }
        var attributes = attributes(false, "TID", "BID", "OPERATION", "CONSISTENCY");
        if (attributes.containsKey("OPERATION")) {
            throw xml.error(INVALID_WIRE, "A FULL object cannot carry an incremental OPERATION");
        }
        String tid = identifier(required(attributes, "TID"));
        var originalBasket = Optional.ofNullable(attributes.get("BID")).map(this::basketId);
        var consistency = consistency(attributes);
        var identity = new TransferIdentity(basket, originalBasket, Optional.of(new TransferId(tid, IdOrigin.SOURCE)),
                stableId(tid).map(ObjectOid::new), source);
        token = xml.nextTag();
        if (token == START_ELEMENT) {
            throw xml.error(UNSUPPORTED, "XTF object field " + elementName(token) + " is unsupported");
        }
        require(END_ELEMENT, token, tag);
        return new ObjectEvent(new TransferObject(identity,
                new TransferValue.Structured(descriptor.symbol(), Map.of())), Optional.empty(), consistency);
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

    private static Optional<String> stableId(String value) {
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
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\t') >= 0) {
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
