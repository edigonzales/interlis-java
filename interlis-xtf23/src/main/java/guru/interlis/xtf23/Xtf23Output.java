package guru.interlis.xtf23;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static guru.interlis.xtf23.Xtf23Exception.Reason.*;

final class Xtf23Output {
    private final BorrowedOutput output;
    private XMLStreamWriter xml;
    private int depth;

    Xtf23Output(OutputStream output) {
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
        start("TRANSFER");
        xml.writeDefaultNamespace(Xtf23Reader.NAMESPACE);
    }

    void start(String name) throws XMLStreamException {
        output.cancellation.throwIfCancellationRequested();
        if (depth == Xtf23Input.MAX_DEPTH) {
            throw error(LIMIT_EXCEEDED, "XTF output exceeds the supported XML depth");
        }
        xml.writeStartElement(name);
        depth++;
    }

    void end() throws XMLStreamException {
        output.cancellation.throwIfCancellationRequested();
        xml.writeEndElement();
        depth--;
    }

    void attribute(String name, String value) throws XMLStreamException {
        if (!Xtf23Lexemes.singleLine(value)) {
            throw error(INVALID_WIRE, "XTF attribute " + name + " must be single-line text");
        }
        checkCharacters(value);
        xml.writeAttribute(name, value);
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

    void fragment(String value) throws XMLStreamException {
        checkCharacters(value);
        String checked = Xtf23XmlFragment.validate(value, depth, output.cancellation);
        // Finish the pending wrapper start tag before inserting checked XML events serialized as UTF-8.
        xml.writeCharacters("");
        xml.flush();
        try {
            output.write(checked.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new XMLStreamException(failure);
        }
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
        if (value.length() > Xtf23Input.MAX_TEXT) {
            throw error(LIMIT_EXCEEDED, "XTF output text exceeds the scalar text limit");
        }
        if (!value.codePoints().allMatch(Xtf23Output::xmlCharacter)) {
            throw error(INVALID_WIRE, "Text contains a character outside XML 1.0");
        }
    }

    private static boolean xmlCharacter(int code) {
        return code == 9 || code == 10 || code == 13 || (code >= 0x20 && code <= 0xd7ff)
                || (code >= 0xe000 && code <= 0xfffd) || (code >= 0x10000 && code <= 0x10ffff);
    }

    static Xtf23Exception translate(XMLStreamException failure) {
        var cause = failure.getNestedException();
        if (cause instanceof OperationCancelledException cancelled) {
            throw cancelled;
        }
        if (cause instanceof IOException) {
            return new Xtf23Exception(IO, "Cannot write the XTF output stream", -1, -1, failure);
        }
        return new Xtf23Exception(CONFIGURATION, "Cannot produce XTF XML: " + failure.getMessage(),
                -1, -1, failure);
    }

    static Xtf23Exception error(Xtf23Exception.Reason reason, String message) {
        return new Xtf23Exception(reason, message, -1, -1, null);
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
