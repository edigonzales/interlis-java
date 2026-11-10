package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;

final class Xtf24Output {
    private final BorrowedOutput output;
    private final Xtf24Limits limits;
    private XMLStreamWriter xml;
    private int depth;
    private long remainingEvents;
    private long remainingUnits;

    Xtf24Output(OutputStream output, Xtf24Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.output = new BorrowedOutput(Objects.requireNonNull(output, "output"));
    }

    void begin(CancellationToken cancellation) {
        output.cancellation = cancellation;
        remainingEvents = limits.maxObjectEvents();
        remainingUnits = limits.maxObjectCodeUnits();
    }

    void finish() {
        output.cancellation = CancellationToken.none();
    }

    void open() throws XMLStreamException {
        xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(output, "UTF-8");
        xml.writeStartDocument("UTF-8", "1.0");
    }

    void beginObject() {
        remainingEvents = limits.maxObjectEvents();
        remainingUnits = limits.maxObjectCodeUnits();
    }

    void start(QName name) throws XMLStreamException {
        start(name, Map.of());
    }

    void start(QName name, Map<String, String> bindings) throws XMLStreamException {
        String uri = name.getNamespaceURI();
        String prefix = prefix(uri);
        if (!bindings.isEmpty()) {
            String preferred = prefix;
            if (!uri.equals(bindings.get(preferred))) {
                prefix = bindings.entrySet().stream().filter(binding -> binding.getValue().equals(uri))
                        .map(Map.Entry::getKey).sorted().findFirst().orElse(null);
            }
            if (prefix == null) {
                int suffix = 1;
                do {
                    prefix = "m" + suffix++;
                } while (bindings.containsKey(prefix));
            }
        }
        startElement(new QName(uri, name.getLocalPart(), prefix));
        if (depth == 1 || !uri.equals(Xtf24Wire.NAMESPACE) || !bindings.isEmpty()) {
            namespace(prefix, uri);
        }
        if (bindings.size() > 64) {
            throw error(LIMIT_EXCEEDED, "Too many fragment namespace bindings");
        }
        for (var binding : new TreeMap<>(bindings).entrySet()) {
            if (!binding.getKey().equals(prefix)) {
                namespace(binding.getKey(), binding.getValue());
            }
        }
    }

    void fragmentStart(QName name, Map<String, String> bindings) throws XMLStreamException {
        startElement(name);
        for (var binding : new TreeMap<>(bindings).entrySet()) {
            namespace(binding.getKey(), binding.getValue());
        }
    }

    private void startElement(QName name) throws XMLStreamException {
        event();
        if (depth == limits.maxDepth()) {
            throw error(LIMIT_EXCEEDED, "XTF output exceeds the supported XML depth");
        }
        technical(name.getLocalPart());
        technical(name.getPrefix());
        checkCharacters(name.getNamespaceURI());
        xml.writeStartElement(name.getPrefix(), name.getLocalPart(), name.getNamespaceURI());
        depth++;
    }

    private void namespace(String prefix, String uri) throws XMLStreamException {
        technical(prefix);
        checkCharacters(uri);
        if (prefix.equals("xmlns") || prefix.equals("xml")
                && !uri.equals(javax.xml.XMLConstants.XML_NS_URI)) {
            throw error(INVALID_WIRE, "Reserved fragment namespace prefix");
        }
        xml.writeNamespace(prefix, uri);
    }

    void fragmentAttribute(QName name, String value) throws XMLStreamException {
        technical(name.getLocalPart());
        technical(name.getPrefix());
        checkCharacters(value);
        xml.writeAttribute(name.getPrefix(), name.getNamespaceURI(), name.getLocalPart(), value);
    }

    void comment(String text) throws XMLStreamException {
        event();
        checkCharacters(text);
        xml.writeComment(text);
    }

    void processingInstruction(String target, String data) throws XMLStreamException {
        event();
        technical(target);
        checkCharacters(data);
        xml.writeProcessingInstruction(target, data);
    }

    Xtf24Limits limits() {
        return limits;
    }

    CancellationToken cancellation() {
        return output.cancellation;
    }

    void end() throws XMLStreamException {
        event();
        xml.writeEndElement();
        depth--;
    }

    void attribute(QName name, String value) throws XMLStreamException {
        checkCharacters(value);
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\t') >= 0) {
            throw error(INVALID_WIRE, "XTF output attribute must be single-line text");
        }
        technical(name.getLocalPart());
        xml.writeAttribute(prefix(name.getNamespaceURI()), name.getNamespaceURI(), name.getLocalPart(), value);
    }

    private static String prefix(String uri) {
        if (uri.equals(Xtf24Wire.NAMESPACE)) {
            return "ili";
        }
        return uri.isEmpty() ? "" : "m";
    }

    void text(String value) throws XMLStreamException {
        event();
        checkCharacters(value);
        int start = 0;
        for (int carriage = value.indexOf('\r'); carriage >= 0; carriage = value.indexOf('\r', start)) {
            xml.writeCharacters(value.substring(start, carriage));
            xml.writeEntityRef("#xD");
            start = carriage + 1;
        }
        xml.writeCharacters(value.substring(start));
    }

    void endDocument() throws XMLStreamException {
        xml.writeEndDocument();
        xml.flush();
    }

    void close() {
        if (xml != null) {
            try {
                xml.close();
            } catch (XMLStreamException failure) {
                throw translate(failure);
            } finally {
                xml = null;
            }
        }
    }

    private void checkCharacters(String value) {
        output.cancellation.throwIfCancellationRequested();
        if (value.length() > limits.maxScalarCodeUnits()) {
            throw error(LIMIT_EXCEEDED, "XTF output text exceeds the scalar text limit");
        }
        charge(value.length());
        for (int offset = 0; offset < value.length();) {
            if ((offset & 1023) == 0) {
                output.cancellation.throwIfCancellationRequested();
            }
            int code = value.codePointAt(offset);
            if (!xmlCharacter(code)) {
                throw error(INVALID_WIRE, "Text contains a character outside XML 1.0");
            }
            offset += Character.charCount(code);
        }
    }

    private void event() {
        output.cancellation.throwIfCancellationRequested();
        if (remainingEvents == 0) {
            throw error(LIMIT_EXCEEDED, "XTF output exceeds its event budget");
        }
        remainingEvents--;
    }

    private void technical(String value) {
        if (value.length() > limits.maxIdentifierCodeUnits()) {
            throw error(LIMIT_EXCEEDED, "XTF technical name is too long");
        }
        charge(value.length());
    }

    private void charge(int units) {
        if (remainingUnits < units) {
            throw error(LIMIT_EXCEEDED, "XTF output exceeds its aggregate UTF-16 budget");
        }
        remainingUnits -= units;
    }

    static String escapeAttribute(String value) {
        var escaped = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); offset++) {
            char code = value.charAt(offset);
            switch (code) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\r' -> escaped.append("&#xD;");
                case '\n' -> escaped.append("&#xA;");
                case '\t' -> escaped.append("&#x9;");
                default -> escaped.append(code);
            }
        }
        return escaped.toString();
    }

    private static boolean xmlCharacter(int code) {
        return code == 9 || code == 10 || code == 13 || (code >= 0x20 && code <= 0xd7ff)
                || (code >= 0xe000 && code <= 0xfffd) || (code >= 0x10000 && code <= 0x10ffff);
    }

    static Xtf24Exception translate(XMLStreamException failure) {
        var cause = failure.getNestedException();
        if (cause instanceof OperationCancelledException cancelled) {
            throw cancelled;
        }
        if (cause instanceof IOException) {
            return new Xtf24Exception(IO, "Cannot write the XTF output stream", -1, -1, failure);
        }
        return new Xtf24Exception(CONFIGURATION, "Cannot produce XTF XML",
                -1, -1, failure);
    }

    static Xtf24Exception error(Xtf24Exception.Reason reason, String message) {
        return new Xtf24Exception(reason, message, -1, -1, null);
    }

    private static final class BorrowedOutput extends FilterOutputStream {
        private enum Markup { TEXT, OPEN, TAG, ATTRIBUTE, COMMENT, PI }
        private static final byte[] CR = "&#xD;".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        private static final byte[] LF = "&#xA;".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        private static final byte[] TAB = "&#x9;".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        private Markup markup = Markup.TEXT;
        private int commentDashes;
        private boolean question;
        private CancellationToken cancellation = CancellationToken.none();

        BorrowedOutput(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            cancellation.throwIfCancellationRequested();
            byte[] replacement = attributeWhitespace(value & 255);
            if (replacement == null) {
                out.write(value);
            } else {
                out.write(replacement);
            }
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            cancellation.throwIfCancellationRequested();
            Objects.checkFromIndexSize(offset, length, values.length);
            int run = offset;
            int end = offset + length;
            for (int index = offset; index < end; index++) {
                if ((index & 1023) == 0) {
                    cancellation.throwIfCancellationRequested();
                }
                byte[] replacement = attributeWhitespace(values[index] & 255);
                if (replacement != null) {
                    out.write(values, run, index - run);
                    out.write(replacement);
                    run = index + 1;
                }
            }
            out.write(values, run, end - run);
        }

        /**
         * StAX supplies all XML syntax and escaping. This UTF-8 filter replaces
         * only literal attribute whitespace, which the JDK writer otherwise
         * emits in a form that a subsequent XML parser normalizes to spaces.
         * The writer emits comments and PIs, but never DTD or CDATA markup.
         */
        private byte[] attributeWhitespace(int value) {
            switch (markup) {
                case TEXT -> {
                    if (value == '<') {
                        markup = Markup.OPEN;
                    }
                }
                case OPEN -> {
                    if (value == '!') {
                        markup = Markup.COMMENT;
                        commentDashes = 0;
                    } else if (value == '?') {
                        markup = Markup.PI;
                        question = false;
                    } else {
                        markup = value == '>' ? Markup.TEXT : Markup.TAG;
                    }
                }
                case TAG -> {
                    if (value == '"') {
                        markup = Markup.ATTRIBUTE;
                    } else if (value == '>') {
                        markup = Markup.TEXT;
                    }
                }
                case ATTRIBUTE -> {
                    if (value == '"') {
                        markup = Markup.TAG;
                    } else if (value == '\r') {
                        return CR;
                    } else if (value == '\n') {
                        return LF;
                    } else if (value == '\t') {
                        return TAB;
                    }
                }
                case COMMENT -> {
                    if (value == '>' && commentDashes >= 2) {
                        markup = Markup.TEXT;
                    }
                    commentDashes = value == '-' ? Math.min(2, commentDashes + 1) : 0;
                }
                case PI -> {
                    if (value == '>' && question) {
                        markup = Markup.TEXT;
                    }
                    question = value == '?';
                }
            }
            return null;
        }

        @Override
        public void flush() throws IOException {
            cancellation.throwIfCancellationRequested();
            out.flush();
        }

        @Override
        public void close() {
            // The caller owns the stream on completion, cancellation and failure.
        }
    }
}
