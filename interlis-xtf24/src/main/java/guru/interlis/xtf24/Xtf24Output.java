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
import java.util.Objects;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;

final class Xtf24Output {
    private final BorrowedOutput output;
    private final Xtf24Limits limits;
    private XMLStreamWriter xml;
    private int depth;

    Xtf24Output(OutputStream output, Xtf24Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.output = new BorrowedOutput(Objects.requireNonNull(output, "output"));
    }

    void begin(CancellationToken cancellation) {
        output.cancellation = cancellation;
    }

    void finish() {
        output.cancellation = CancellationToken.none();
    }

    void open() throws XMLStreamException {
        xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(output, "UTF-8");
        xml.writeStartDocument("UTF-8", "1.0");
    }

    void start(QName name) throws XMLStreamException {
        output.cancellation.throwIfCancellationRequested();
        if (depth == limits.maxDepth()) {
            throw error(LIMIT_EXCEEDED, "XTF output exceeds the supported XML depth");
        }
        String uri = name.getNamespaceURI();
        String prefix = prefix(uri);
        xml.writeStartElement(prefix, name.getLocalPart(), uri);
        if (uri.equals(Xtf24Wire.NAMESPACE) && depth == 0 || prefix.equals("m")) {
            xml.writeNamespace(prefix, uri);
        }
        depth++;
    }

    void end() throws XMLStreamException {
        output.cancellation.throwIfCancellationRequested();
        xml.writeEndElement();
        depth--;
    }

    void attribute(QName name, String value) throws XMLStreamException {
        checkCharacters(value);
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\t') >= 0) {
            throw error(INVALID_WIRE, "XTF output attribute must be single-line text");
        }
        xml.writeAttribute(prefix(name.getNamespaceURI()), name.getNamespaceURI(), name.getLocalPart(), value);
    }

    private static String prefix(String uri) {
        if (uri.equals(Xtf24Wire.NAMESPACE)) {
            return "ili";
        }
        return uri.isEmpty() ? "" : "m";
    }

    void text(String value) throws XMLStreamException {
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
        if (!value.codePoints().allMatch(Xtf24Output::xmlCharacter)) {
            throw error(INVALID_WIRE, "Text contains a character outside XML 1.0");
        }
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
        return new Xtf24Exception(CONFIGURATION, "Cannot produce XTF XML: " + failure.getMessage(),
                -1, -1, failure);
    }

    static Xtf24Exception error(Xtf24Exception.Reason reason, String message) {
        return new Xtf24Exception(reason, message, -1, -1, null);
    }

    private static final class BorrowedOutput extends FilterOutputStream {
        private CancellationToken cancellation = CancellationToken.none();

        BorrowedOutput(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            cancellation.throwIfCancellationRequested();
            out.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            cancellation.throwIfCancellationRequested();
            out.write(values, offset, length);
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
