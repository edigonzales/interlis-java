package guru.interlis.projection;

import java.util.Map;
import java.util.Objects;

/** Deterministic Mermaid text renderer. */
public final class MermaidRenderer {
    /**
     * Executes {@code render}.
     *
     * @param document the {@code document} argument
     *
     * @return the result
     */
    public String render(DiagramDocument document) {
        Objects.requireNonNull(document, "document");
        Map<String, String> names = GraphTextFormatter.safeNames(document);
        StringBuilder out = new StringBuilder("classDiagram\n");
        for (DiagramNode node : document.nodes()) {
            String name = names.get(node.id());
            out.append("  class ").append(name).append("[\"")
                    .append(GraphTextFormatter.mermaidEscape(node.label())).append("\"] {\n");
            for (DiagramCompartment compartment : node.compartments()) {
                for (DiagramMember member : compartment.members()) {
                    out.append("    ")
                            .append(GraphTextFormatter.mermaidEscape(
                                    GraphTextFormatter.memberLine(member, false)))
                            .append('\n');
                }
            }
            out.append("  }\n");
        }
        for (DiagramEdge edge : document.edges()) {
            String source = names.get(edge.source().elementId());
            String target = names.get(edge.target().elementId());
            if (source == null || target == null) {
                continue;
            }
            if (edge.kind() == DiagramEdge.EdgeKind.INHERITANCE) {
                out.append("  ").append(source).append(" <|-- ").append(target).append('\n');
            } else {
                out.append("  ").append(source).append(" --> ").append(target);
                if (!edge.label().isBlank()) {
                    out.append(" : ").append(GraphTextFormatter.mermaidEscape(edge.label()));
                }
                out.append('\n');
            }
        }
        return out.toString();
    }
}
