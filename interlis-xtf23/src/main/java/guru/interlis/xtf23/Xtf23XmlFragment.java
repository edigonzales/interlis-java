package guru.interlis.xtf23;

import guru.interlis.execution.CancellationToken;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static guru.interlis.xtf23.Xtf23Exception.Reason.*;
import static javax.xml.stream.XMLStreamConstants.*;

/** Captures payload events as self-contained XML, including namespace bindings inherited from XTF. */
final class Xtf23XmlFragment {
    private static final String WRAPPER = "blackboxFragment";

    private Xtf23XmlFragment() { }

    static String read(Xtf23Input input) throws XMLStreamException {
        return read(input, 0);
    }

    static String validate(String value, int outerDepth, CancellationToken cancellation) {
        String wrapped = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><" + WRAPPER + ">"
                + value + "</" + WRAPPER + ">";
        var input = new Xtf23Input(new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8)));
        input.begin(cancellation);
        try {
            input.open();
            input.nextTag();
            String checked = read(input, outerDepth);
            if (input.next() != END_DOCUMENT) {
                throw input.error(INVALID_WIRE, "Unexpected content after the XML fragment");
            }
            return checked;
        } catch (XMLStreamException failure) {
            throw input.translate(failure);
        } finally {
            input.close();
        }
    }

    private static String read(Xtf23Input input, int outerDepth) throws XMLStreamException {
        var fragment = new Fragment(input);
        var cursor = input.cursor();
        int depth = 0;
        while (true) {
            switch (input.next()) {
                case START_ELEMENT -> {
                    boolean root = depth == 0;
                    if (++depth + outerDepth > Xtf23Input.MAX_DEPTH) {
                        throw input.error(LIMIT_EXCEEDED, "XML fragment exceeds the output depth limit");
                    }
                    fragment.append("<");
                    fragment.name(cursor.getName());
                    var namespaces = input.fragmentNamespaces(root);
                    if (namespaces.size() + cursor.getAttributeCount() > Xtf23Input.MAX_ATTRIBUTES) {
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
        private final Xtf23Input input;
        private final StringBuilder value = new StringBuilder();

        Fragment(Xtf23Input input) {
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
            if (end - start > Xtf23Input.MAX_TEXT - value.length()) {
                throw input.error(LIMIT_EXCEEDED, "XML fragment exceeds the XTF text limit");
            }
            value.append(text, start, end);
        }
    }
}
