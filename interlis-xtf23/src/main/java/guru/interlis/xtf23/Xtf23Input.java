package guru.interlis.xtf23;

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
import java.util.Objects;

import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static javax.xml.stream.XMLStreamConstants.*;

/** Secure cursor over the XTF XML stream. */
final class Xtf23Input {
    static final int MAX_TEXT = 1_048_576;
    static final int MAX_TOKEN_BYTES = 8 * MAX_TEXT;
    private static final int MAX_DEPTH = 128;
    private static final int MAX_ATTRIBUTES = 64;
    private static final int MAX_NAME = 1024;

    private final BorrowedInput input;
    private XMLStreamReader xml;

    Xtf23Input(InputStream input) {
        this.input = new BorrowedInput(Objects.requireNonNull(input, "input"));
    }

    void begin(CancellationToken cancellation) {
        input.cancellation = cancellation;
    }

    void finish() {
        input.cancellation = CancellationToken.none();
    }

    void open() throws XMLStreamException {
        var factory = XMLInputFactory.newDefaultFactory();
        try {
            requireProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
            requireProperty(factory, XMLInputFactory.IS_COALESCING, false);
            requireProperty(factory, XMLInputFactory.IS_VALIDATING, false);
            requireProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
            requireProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            requireProperty(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            requireProperty(factory, "jdk.xml.maxElementDepth", MAX_DEPTH);
            requireProperty(factory, "jdk.xml.elementAttributeLimit", MAX_ATTRIBUTES);
            requireProperty(factory, "jdk.xml.maxXMLNameLimit", MAX_NAME);
        } catch (IllegalArgumentException e) {
            throw failure(CONFIGURATION, "Cannot configure the JDK XML input factory", null, e);
        }
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw error(UNSAFE_XML, "External XML resolution is forbidden");
        });
        input.remaining = MAX_TOKEN_BYTES;
        xml = factory.createXMLStreamReader(input);
        if (!"1.0".equals(xml.getVersion()) || !"UTF-8".equalsIgnoreCase(xml.getCharacterEncodingScheme())
                || !"UTF-8".equalsIgnoreCase(xml.getEncoding())) {
            throw error(INVALID_WIRE, "XTF 2.3 requires an XML 1.0 declaration and UTF-8 encoding");
        }
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

    int next() throws XMLStreamException {
        input.cancellation.throwIfCancellationRequested();
        input.remaining = MAX_TOKEN_BYTES;
        int token = xml.next();
        if (token == DTD || token == ENTITY_REFERENCE) {
            throw error(UNSAFE_XML, "DTD declarations and custom XML entities are forbidden");
        }
        return token;
    }

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
                    if (xml.getTextLength() > MAX_TEXT - value.length()) {
                        throw error(LIMIT_EXCEEDED, "XTF text value exceeds " + MAX_TEXT + " UTF-16 units");
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
            }
        }
    }

    Xtf23Exception error(Xtf23Exception.Reason reason, String message) {
        return failure(reason, message, xml == null ? null : xml.getLocation(), null);
    }

    Xtf23Exception translate(XMLStreamException e) {
        Throwable nested = e.getNestedException();
        if (nested instanceof Xtf23Exception failure) {
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
        return failure(MALFORMED_XML, "Malformed XML: " + message, e.getLocation(), e);
    }

    private static Xtf23Exception failure(Xtf23Exception.Reason reason, String message,
                                          Location location, Throwable cause) {
        return new Xtf23Exception(reason, message, location == null ? -1 : location.getLineNumber(),
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
        private int remaining;

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
                count = in.read(buffer, offset, Math.min(length, remaining));
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
