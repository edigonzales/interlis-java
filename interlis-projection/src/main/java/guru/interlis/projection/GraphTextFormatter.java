package guru.interlis.projection;

import java.util.HashMap;
import java.util.Map;

/** Deterministic renderer support and escaping. */
final class GraphTextFormatter {
    /**
     * Returns the {@code GraphTextFormatter} value.
     *
     * @return the value
     */
    private GraphTextFormatter() {
    }

    static Map<String, String> safeNames(DiagramDocument document) {
        Map<String, String> names = new HashMap<>();
        int index = 1;
        for (DiagramNode node : document.nodes()) {
            names.put(node.id(), "N" + index++);
        }
        return names;
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    static String mermaidEscape(String value) {
        return value.replace("\"", "#quot;").replace("\n", " ")
                .replace("\r", " ").replace("{", "&#123;").replace("}", "&#125;");
    }

    static String memberLine(DiagramMember member, boolean plantUml) {
        String type = member.typeLabel().orElse("");
        String cardinality = member.cardinality().map(c -> " " + c).orElse("");
        String suffix = type.isEmpty() ? "" : (plantUml ? " : " : " : ") + type;
        return member.label() + suffix + cardinality + (member.inherited() ? " {inherited}" : "");
    }
}
