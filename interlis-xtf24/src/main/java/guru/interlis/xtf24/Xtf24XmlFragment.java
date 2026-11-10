package guru.interlis.xtf24;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;
import static javax.xml.stream.XMLStreamConstants.*;

/** Captures payload events as self-contained XML, including namespace bindings inherited from XTF. */
final class Xtf24XmlFragment {
    private static final String WRAPPER = "fragment";

    private Xtf24XmlFragment() { }

    static guru.interlis.transfer.TransferValue.XmlFragment read(Xtf24Input input) throws XMLStreamException {
        var bindings = input.fragmentNamespaces(true);
        if (bindings.size() > 64) {
            throw input.error(LIMIT_EXCEEDED, "Too many inherited fragment namespace bindings");
        }
        String value = capture(input, 0);
        long units = value.length();
        for (var binding : bindings.entrySet()) {
            units += (long) binding.getKey().length() + binding.getValue().length();
            if (units > input.limits().maxScalarCodeUnits()) {
                throw input.error(LIMIT_EXCEEDED, "XML fragment context exceeds its text limit");
            }
        }
        return new guru.interlis.transfer.TransferValue.XmlFragment(value, bindings);
    }

    static void write(QName field, guru.interlis.transfer.TransferValue.XmlFragment value, Xtf24Output output)
            throws XMLStreamException {
        long units = value.xml().length();
        if (value.namespaceBindings().size() > 64) {
            throw Xtf24Wire.failure(LIMIT_EXCEEDED, "Too many fragment namespace bindings");
        }
        for (var binding : value.namespaceBindings().entrySet()) {
            units += (long) binding.getKey().length() + binding.getValue().length();
            if (units > output.limits().maxScalarCodeUnits()) {
                throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XML fragment exceeds its text limit");
            }
        }
        if (units > output.limits().maxScalarCodeUnits()) {
            throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XML fragment exceeds its text limit");
        }
        var wrapped = new StringBuilder("<" + WRAPPER);
        for (var binding : new java.util.TreeMap<>(value.namespaceBindings()).entrySet()) {
            wrapped.append(binding.getKey().isEmpty() ? " xmlns" : " xmlns:" + binding.getKey());
            wrapped.append("=\"").append(Xtf24Output.escapeAttribute(binding.getValue())).append("\"");
        }
        wrapped.append(">").append(value.xml()).append("</").append(WRAPPER).append(">");
        var input = new Xtf24Input(new ByteArrayInputStream(wrapped.toString().getBytes(StandardCharsets.UTF_8)),
                output.limits());
        input.begin(output.cancellation());
        try {
            input.open();
            if (input.nextTag() != START_ELEMENT || !input.cursor().getLocalName().equals(WRAPPER)
                    || input.cursor().getAttributeCount() != 0) {
                throw input.error(INVALID_WIRE, "Invalid fragment wrapper");
            }
            for (var binding : value.namespaceBindings().entrySet()) {
                if (!binding.getValue().equals(java.util.Objects.toString(input.cursor().getNamespaceURI(binding.getKey()), ""))) {
                    throw input.error(INVALID_WIRE, "Invalid fragment namespace binding");
                }
            }
            output.start(field, value.namespaceBindings());
            int depth = 0;
            while (true) {
                switch (input.next()) {
                    case START_ELEMENT -> {
                        var bindings = input.fragmentNamespaces(depth == 0);
                        if (bindings.size() + input.cursor().getAttributeCount() > 64) {
                            throw input.error(LIMIT_EXCEEDED, "Too many XML fragment attributes and bindings");
                        }
                        output.fragmentStart(input.cursor().getName(), bindings);
                        for (int i = 0; i < input.cursor().getAttributeCount(); i++) {
                            output.fragmentAttribute(input.cursor().getAttributeName(i), input.cursor().getAttributeValue(i));
                        }
                        depth++;
                    }
                    case END_ELEMENT -> {
                        if (depth == 0) {
                            if (input.next() != END_DOCUMENT) {
                                throw input.error(INVALID_WIRE, "Unexpected content after XML fragment");
                            }
                            output.end();
                            return;
                        }
                        output.end();
                        depth--;
                    }
                    case CHARACTERS, CDATA, SPACE -> output.text(input.cursor().getText());
                    case COMMENT -> output.comment(input.cursor().getText());
                    case PROCESSING_INSTRUCTION -> output.processingInstruction(input.cursor().getPITarget(),
                            java.util.Objects.toString(input.cursor().getPIData(), ""));
                    default -> throw input.error(INVALID_WIRE, "Unexpected event inside XML fragment");
                }
            }
        } catch (XMLStreamException failure) {
            throw input.translate(failure);
        } finally {
            input.close();
        }
    }

    private static String capture(Xtf24Input input, int outerDepth) throws XMLStreamException {
        var fragment = new Fragment(input);
        var cursor = input.cursor();
        int depth = 0;
        while (true) {
            switch (input.next()) {
                case START_ELEMENT -> {
                    boolean root = depth == 0;
                    if (++depth + outerDepth > input.limits().maxDepth()) {
                        throw input.error(LIMIT_EXCEEDED, "XML fragment exceeds the output depth limit");
                    }
                    fragment.append("<");
                    fragment.name(cursor.getName());
                    var namespaces = input.fragmentNamespaces(root);
                    if (namespaces.size() + cursor.getAttributeCount() > 64) {
                        throw input.error(LIMIT_EXCEEDED, "Too many attributes and namespace declarations in XML fragment");
                    }
                    for (var binding : namespaces.entrySet()) {
                        String prefix = binding.getKey();
                        fragment.append(prefix.isEmpty() ? " xmlns" : " xmlns:" + prefix);
                        fragment.attributeValue(binding.getValue());
                    }
                    for (int i = 0; i < cursor.getAttributeCount(); i++) {
                        fragment.append(" ");
                        fragment.name(cursor.getAttributeName(i));
                        fragment.attributeValue(cursor.getAttributeValue(i));
                    }
                    fragment.append(">");
                }
                case END_ELEMENT -> {
                    if (depth == 0) {
                        return fragment.value.toString();
                    }
                    fragment.append("</");
                    fragment.name(cursor.getName());
                    fragment.append(">");
                    depth--;
                }
                case CHARACTERS, CDATA, SPACE -> fragment.escape(cursor.getText(), false);
                case COMMENT -> {
                    fragment.append("<!--");
                    fragment.append(cursor.getText());
                    fragment.append("-->");
                }
                case PROCESSING_INSTRUCTION -> {
                    fragment.append("<?");
                    fragment.append(cursor.getPITarget());
                    String data = cursor.getPIData();
                    if (data != null && !data.isEmpty()) {
                        fragment.append(" ");
                        fragment.append(data);
                    }
                    fragment.append("?>");
                }
                default -> throw input.error(INVALID_WIRE, "Unexpected event inside XML fragment");
            }
        }
    }

    private static final class Fragment {
        private final Xtf24Input input;
        private final StringBuilder value = new StringBuilder();

        Fragment(Xtf24Input input) {
            this.input = input;
        }

        void name(QName name) {
            if (!name.getPrefix().isEmpty()) {
                append(name.getPrefix());
                append(":");
            }
            append(name.getLocalPart());
        }

        void attributeValue(String text) {
            append("=\"");
            escape(text, true);
            append("\"");
        }

        void escape(String text, boolean attribute) {
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                String replacement = switch (text.charAt(i)) {
                    case '&' -> "&amp;";
                    case '<' -> "&lt;";
                    case '>' -> "&gt;";
                    case '\r' -> "&#xD;";
                    case '\n' -> attribute ? "&#xA;" : null;
                    case '\t' -> attribute ? "&#x9;" : null;
                    case '"' -> attribute ? "&quot;" : null;
                    default -> null;
                };
                if (replacement != null) {
                    append(text, start, i);
                    append(replacement);
                    start = i + 1;
                }
            }
            append(text, start, text.length());
        }

        void append(String text) {
            append(text, 0, text.length());
        }

        void append(String text, int start, int end) {
            if (end - start > input.limits().maxScalarCodeUnits() - value.length()) {
                throw input.error(LIMIT_EXCEEDED, "XML fragment exceeds the XTF text limit");
            }
            value.append(text, start, end);
        }
    }
}
