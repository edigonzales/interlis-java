package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;

import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;
import static javax.xml.stream.XMLStreamConstants.*;

/** Secure cursor over the XTF XML stream. */
final class Xtf24Input {
    private static final int MAX_ATTRIBUTES = 64;

    private final BorrowedInput input;
    private final Xtf24Limits limits;
    private final XMLInputFactory factory;
    private XMLStreamReader xml;
    private long objectTokens = -1;
    private long objectUnits;
    private long operationTokens;
    private long operationUnits;
    private int depth;
    private final ArrayDeque<NamespaceScope> namespaces = new ArrayDeque<>();

    Xtf24Input(InputStream input, Xtf24Limits limits) {
        this.input = new BorrowedInput(Objects.requireNonNull(input, "input"));
        this.limits = Objects.requireNonNull(limits, "limits");
        this.factory = secureFactory(limits);
    }

    void begin(CancellationToken cancellation) {
        input.cancellation = cancellation;
        operationTokens = limits.maxObjectEvents();
        operationUnits = limits.maxObjectCodeUnits();
    }

    void finish() {
        input.cancellation = CancellationToken.none();
    }

    void open() throws XMLStreamException {
        input.remaining = limits.maxParserAdvanceBytes();
        xml = factory.createXMLStreamReader(input);
        if (xml.getVersion() != null && !"1.0".equals(xml.getVersion())
                || xml.getEncoding() != null && !"UTF-8".equalsIgnoreCase(xml.getEncoding())
                || xml.getCharacterEncodingScheme() != null
                && !"UTF-8".equalsIgnoreCase(xml.getCharacterEncodingScheme())) {
            throw error(INVALID_WIRE, "XTF 2.4 requires XML 1.0 and UTF-8 encoding");
        }
    }

    static XMLInputFactory secureFactory(Xtf24Limits limits) {
        var factory = XMLInputFactory.newDefaultFactory();
        try {
            requireProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
            requireProperty(factory, XMLInputFactory.IS_COALESCING, false);
            requireProperty(factory, XMLInputFactory.IS_VALIDATING, false);
            requireProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
            requireProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            requireProperty(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            requireProperty(factory, "jdk.xml.maxElementDepth", limits.maxDepth());
            requireProperty(factory, "jdk.xml.maxXMLNameLimit",
                    Math.max(limits.maxScalarCodeUnits(), limits.maxIdentifierCodeUnits()));
            requireProperty(factory, "jdk.xml.elementAttributeLimit", MAX_ATTRIBUTES);
            factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
                throw new Xtf24Exception(UNSAFE_XML, "External XML resolution is forbidden", -1, -1, null);
            });
        } catch (IllegalArgumentException e) {
            throw new Xtf24Exception(CONFIGURATION, "Cannot configure the JDK XML input factory", -1, -1, e);
        }
        return factory;
    }

    private static void requireProperty(XMLInputFactory factory, String property, Object value) {
        factory.setProperty(property, value);
        if (!value.toString().equals(Objects.toString(factory.getProperty(property), null))) {
            throw new IllegalArgumentException("JDK XML property did not take effect: " + property);
        }
    }

    XMLStreamReader cursor() {
        return xml;
    }

    void beginObject() {
        objectTokens = limits.maxObjectEvents();
        objectUnits = limits.maxObjectCodeUnits();
        accountToken(START_ELEMENT);
    }

    void endObject() {
        objectTokens = -1;
    }

    private void accountToken(int token) {
        boolean object = objectTokens >= 0;
        long tokens = object ? objectTokens : operationTokens;
        long units = object ? objectUnits : operationUnits;
        if (tokens == 0) {
            throw error(LIMIT_EXCEEDED, "XTF content exceeds its XML event budget");
        }
        if (token == START_ELEMENT) {
            units = charge(units, xml.getLocalName().length());
            units = charge(units, xml.getNamespaceURI() == null ? 0 : xml.getNamespaceURI().length());
            for (int i = 0; i < xml.getAttributeCount(); i++) {
                units = charge(units, xml.getAttributeValue(i).length());
                units = charge(units, xml.getAttributeLocalName(i).length());
                units = charge(units, Objects.toString(xml.getAttributeNamespace(i), "").length());
            }
            for (int i = 0; i < xml.getNamespaceCount(); i++) {
                units = charge(units, Objects.toString(xml.getNamespaceURI(i), "").length());
                units = charge(units, Objects.toString(xml.getNamespacePrefix(i), "").length());
            }
        } else if (token == CHARACTERS || token == CDATA || token == SPACE || token == COMMENT) {
            units = charge(units, xml.getTextLength());
        } else if (token == PROCESSING_INSTRUCTION) {
            units = charge(units, Objects.toString(xml.getPIData(), "").length());
            units = charge(units, xml.getPITarget().length());
        }
        if (object) {
            objectTokens = tokens - 1;
            objectUnits = units;
        } else {
            operationTokens = tokens - 1;
            operationUnits = units;
        }
    }

    private long charge(long remaining, int units) {
        if (remaining < units) {
            throw error(LIMIT_EXCEEDED, "XTF content exceeds its aggregate UTF-16 budget");
        }
        return remaining - units;
    }

    int next() throws XMLStreamException {
        input.cancellation.throwIfCancellationRequested();
        input.remaining = limits.maxParserAdvanceBytes();
        if (xml.getEventType() == END_ELEMENT) {
            if (!namespaces.isEmpty() && namespaces.peek().depth() == depth) {
                namespaces.pop();
            }
            depth--;
        }
        int token = xml.next();
        if (token == DTD || token == ENTITY_REFERENCE) {
            throw error(UNSAFE_XML, "DTD declarations and custom XML entities are forbidden");
        }
        if (token == START_ELEMENT) {
            technical(xml.getLocalName());
            technical(Objects.toString(xml.getPrefix(), ""));
            for (int i = 0; i < xml.getAttributeCount(); i++) {
                technical(xml.getAttributeLocalName(i));
                technical(Objects.toString(xml.getAttributePrefix(i), ""));
            }
            if (xml.getNamespaceCount() > MAX_ATTRIBUTES) {
                throw error(LIMIT_EXCEEDED, "Too many namespace declarations on an XTF element");
            }
            for (int i = 0; i < xml.getNamespaceCount(); i++) {
                technical(Objects.toString(xml.getNamespacePrefix(i), ""));
                if (Objects.toString(xml.getNamespaceURI(i), "").length() > limits.maxScalarCodeUnits()) {
                    throw error(LIMIT_EXCEEDED, "XTF namespace URI exceeds the text limit");
                }
            }
            for (int i = 0; i < xml.getAttributeCount(); i++) {
                if (xml.getAttributeValue(i).length() > limits.maxScalarCodeUnits()) {
                    throw error(LIMIT_EXCEEDED, "XTF attribute exceeds the text limit");
                }
            }
        }
        accountToken(token);
        if (token == START_ELEMENT) {
            depth++;
            if (xml.getNamespaceCount() > 0) {
                var declarations = new TreeMap<String, String>();
                for (int i = 0; i < xml.getNamespaceCount(); i++) {
                    declarations.put(Objects.toString(xml.getNamespacePrefix(i), ""),
                            Objects.toString(xml.getNamespaceURI(i), ""));
                }
                namespaces.push(new NamespaceScope(depth, Map.copyOf(declarations)));
            }
        }
        return token;
    }

    private void technical(String name) {
        if (name.length() > limits.maxIdentifierCodeUnits()) {
            throw error(LIMIT_EXCEEDED, "XML technical name exceeds the identifier limit");
        }
    }

    Map<String, String> fragmentNamespaces(boolean root) {
        if (!root) {
            return !namespaces.isEmpty() && namespaces.peek().depth() == depth
                    ? namespaces.peek().declarations() : Map.of();
        }
        var bindings = new TreeMap<String, String>();
        bindings.put("", "");
        for (var scope : namespaces.reversed()) {
            bindings.putAll(scope.declarations());
        }
        return Map.copyOf(bindings);
    }

    int depth() {
        return depth;
    }

    Xtf24Limits limits() {
        return limits;
    }

    private record NamespaceScope(int depth, Map<String, String> declarations) {}

    int nextTag() throws XMLStreamException {
        while (true) {
            int token = next();
            switch (token) {
                case START_ELEMENT, END_ELEMENT, END_DOCUMENT -> { return token; }
                case COMMENT, PROCESSING_INSTRUCTION -> { }
                case CHARACTERS, CDATA, SPACE -> {
                    if (!xml.isWhiteSpace()) {
                        throw error(INVALID_WIRE, "Unexpected text between XTF elements");
                    }
                }
                default -> throw error(INVALID_WIRE, "Unexpected XML event " + token);
            }
        }
    }

    String text() throws XMLStreamException {
        var value = new StringBuilder();
        while (true) {
            int token = next();
            switch (token) {
                case END_ELEMENT -> { return value.toString(); }
                case COMMENT, PROCESSING_INSTRUCTION -> { }
                case CHARACTERS, CDATA, SPACE -> {
                    if (xml.getTextLength() > limits.maxScalarCodeUnits() - value.length()) {
                        throw error(LIMIT_EXCEEDED, "XTF text value exceeds " + limits.maxScalarCodeUnits() + " UTF-16 units");
                    }
                    value.append(xml.getTextCharacters(), xml.getTextStart(), xml.getTextLength());
                }
                default -> throw error(INVALID_WIRE, "Expected text without child elements");
            }
        }
    }

    void close() {
        if (xml != null) {
            try {
                xml.close();
            } catch (XMLStreamException e) {
                throw translate(e);
            } finally {
                xml = null;
                namespaces.clear();
            }
        }
    }

    Xtf24Exception error(Xtf24Exception.Reason reason, String message) {
        return failure(reason, message, xml == null ? null : xml.getLocation(), null);
    }

    Xtf24Exception locate(Xtf24Exception error) {
        if (xml == null || error.line() > 0) {
            return error;
        }
        return failure(error.reason(), error.getMessage(), xml.getLocation(), error);
    }

    Xtf24Exception translate(XMLStreamException e) {
        Throwable nested = e.getNestedException();
        if (nested instanceof Xtf24Exception failure) {
            return failure;
        }
        if (nested instanceof OperationCancelledException cancelled) {
            throw cancelled;
        }
        String message = e.getMessage();
        if (nested instanceof TokenLimit || message != null && message.contains("JAXP0001")) {
            return failure(LIMIT_EXCEEDED, "XML structure or token exceeds the XTF reader limits", e.getLocation(), e);
        }
        if (nested instanceof SourceFailure) {
            return failure(IO, "Cannot read the XTF input stream", e.getLocation(), e);
        }
        return failure(MALFORMED_XML, "Malformed XTF 2.4 XML", e.getLocation(), e);
    }

    private static Xtf24Exception failure(Xtf24Exception.Reason reason, String message,
                                          Location location, Throwable cause) {
        return new Xtf24Exception(reason, message, location == null ? -1 : location.getLineNumber(),
                location == null ? -1 : location.getColumnNumber(), cause);
    }

    private static final class TokenLimit extends IOException { }

    private static final class SourceFailure extends IOException {
        SourceFailure(IOException cause) {
            super(cause);
        }
    }

    /** StAX can close the source at EOF. Limit parser reads within an individual XML advance. */
    private static final class BorrowedInput extends FilterInputStream {
        private CancellationToken cancellation = CancellationToken.none();
        private long remaining;

        BorrowedInput(InputStream input) {
            super(input);
        }

        private void requireBudget() throws TokenLimit {
            cancellation.throwIfCancellationRequested();
            if (remaining == 0) {
                throw new TokenLimit();
            }
        }

        @Override
        public int read() throws IOException {
            requireBudget();
            int value;
            try {
                value = in.read();
            } catch (IOException failure) {
                throw new SourceFailure(failure);
            }
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            requireBudget();
            int count;
            try {
                count = in.read(buffer, offset, (int) Math.min(length, remaining));
            } catch (IOException failure) {
                throw new SourceFailure(failure);
            }
            if (count > 0) {
                remaining -= count;
            }
            return count;
        }

        @Override
        public void close() {
            // The caller owns the source even when StAX reaches EOF or abandons parsing.
        }
    }
}
