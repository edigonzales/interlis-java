package guru.interlis.validator.cli;

import guru.interlis.execution.CancellationToken;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static javax.xml.stream.XMLStreamConstants.*;

final class XtfHeaderProbe {
    private static final String ILI23 = "http://www.interlis.ch/INTERLIS2.3";
    private static final String ILI24 = "http://www.interlis.ch/xtf/2.4/INTERLIS";
    private static final int MAX_MODELS = 4096;
    private static final int MAX_NAME = 512;
    private static final int MAX_HEADER_BYTES = 8 * 1024 * 1024;

    record Header(boolean xtf23, Map<String, Optional<String>> models) { }

    static final class InvalidHeader extends Exception {
        InvalidHeader(String message) { super(message); }
    }

    private XtfHeaderProbe() { }

    static Header inspect(Path input, CancellationToken token) throws IOException, InvalidHeader {
        token.throwIfCancellationRequested();
        var factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML resolution is disabled");
        });
        try (var stream = new HeaderStream(Files.newInputStream(input), token)) {
            var reader = factory.createXMLStreamReader(stream);
            try {
                return inspect(reader, token);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException failure) {
            if (failure.getNestedException() instanceof IOException io) throw io;
            throw new InvalidHeader(failure.getMessage());
        }
    }

    private static Header inspect(XMLStreamReader reader, CancellationToken token)
            throws XMLStreamException, InvalidHeader {
        if (tag(reader, token) != START_ELEMENT) throw new InvalidHeader("Expected an XTF transfer");
        boolean legacy = named(reader, ILI23, "TRANSFER");
        if (!legacy && !named(reader, ILI24, "transfer")) throw new InvalidHeader("Expected XTF 2.3 or 2.4");
        String namespace = legacy ? ILI23 : ILI24;
        start(reader, token, namespace, legacy ? "HEADERSECTION" : "headersection");
        if (legacy) {
            if (!"2.3".equals(required(reader, "VERSION"))) throw new InvalidHeader("Expected XTF header VERSION 2.3");
            required(reader, "SENDER");
        }
        String modelsTag = legacy ? "MODELS" : "models";
        String modelTag = legacy ? "MODEL" : "model";
        start(reader, token, namespace, modelsTag);
        var models = new LinkedHashMap<String, Optional<String>>();
        while (tag(reader, token) == START_ELEMENT) {
            if (!named(reader, namespace, modelTag)) throw new InvalidHeader("Expected " + modelTag);
            if (models.size() == MAX_MODELS) throw new InvalidHeader("Too many header models");
            String name;
            Optional<String> version;
            if (legacy) {
                name = required(reader, "NAME");
                version = Optional.of(required(reader, "VERSION"));
                required(reader, "URI");
                if (tag(reader, token) != END_ELEMENT) throw new InvalidHeader("Expected end of MODEL");
            } else {
                name = modelText(reader, token);
                version = Optional.empty();
            }
            if (name.isBlank() || name.length() > MAX_NAME || models.putIfAbsent(name, version) != null) {
                throw new InvalidHeader("Invalid or repeated header model name");
            }
        }
        if (!reader.isEndElement() || !named(reader, namespace, modelsTag) || models.isEmpty()) {
            throw new InvalidHeader("Expected at least one header model");
        }
        return new Header(legacy, java.util.Collections.unmodifiableMap(models));
    }

    private static String modelText(XMLStreamReader reader, CancellationToken token)
            throws XMLStreamException, InvalidHeader {
        var text = new StringBuilder();
        while (reader.hasNext()) {
            token.throwIfCancellationRequested();
            switch (reader.next()) {
                case END_ELEMENT -> { return text.toString().trim(); }
                case CHARACTERS, CDATA, SPACE -> {
                    if (text.length() + reader.getTextLength() > MAX_NAME) throw new InvalidHeader("Header model name is too long");
                    text.append(reader.getText());
                }
                case COMMENT, PROCESSING_INSTRUCTION -> { }
                default -> throw new InvalidHeader("Invalid model name element");
            }
        }
        throw new InvalidHeader("Unexpected end of model name element");
    }

    private static boolean named(XMLStreamReader reader, String namespace, String local) {
        return reader.getName().equals(new QName(namespace, local));
    }

    private static void start(XMLStreamReader reader, CancellationToken token, String namespace, String local)
            throws XMLStreamException, InvalidHeader {
        if (tag(reader, token) != START_ELEMENT || !named(reader, namespace, local)) {
            throw new InvalidHeader("Expected " + local);
        }
    }

    private static String required(XMLStreamReader reader, String name) throws InvalidHeader {
        var value = reader.getAttributeValue(null, name);
        if (value == null) throw new InvalidHeader("Missing XTF attribute " + name);
        return value;
    }

    private static int tag(XMLStreamReader reader, CancellationToken token) throws XMLStreamException, InvalidHeader {
        while (reader.hasNext()) {
            token.throwIfCancellationRequested();
            int event = reader.next();
            switch (event) {
                case START_ELEMENT, END_ELEMENT, END_DOCUMENT -> { return event; }
                case COMMENT, PROCESSING_INSTRUCTION, SPACE -> { }
                case CHARACTERS, CDATA -> {
                    if (!reader.isWhiteSpace()) throw new InvalidHeader("Unexpected XML text in header");
                }
                default -> throw new InvalidHeader("Unsupported XML event in header: " + event);
            }
        }
        return END_DOCUMENT;
    }

    private static final class HeaderStream extends FilterInputStream {
        private final CancellationToken token;
        private int remaining = MAX_HEADER_BYTES;

        HeaderStream(java.io.InputStream input, CancellationToken token) {
            super(input);
            this.token = token;
        }

        private void check() throws IOException {
            token.throwIfCancellationRequested();
            if (remaining == 0) throw new IOException("XTF header exceeds the byte limit");
        }

        @Override public int read() throws IOException {
            check();
            int value = in.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            check();
            int count = in.read(buffer, offset, Math.min(length, remaining));
            if (count > 0) remaining -= count;
            return count;
        }
    }
}
