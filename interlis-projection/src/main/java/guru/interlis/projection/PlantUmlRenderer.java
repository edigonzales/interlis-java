package guru.interlis.projection;

import java.util.Map;
import java.util.Objects;

/** Deterministic PlantUML text renderer. */
public final class PlantUmlRenderer {
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
        StringBuilder out = new StringBuilder("@startuml\n");
        for (DiagramNode node : document.nodes()) {
            String name = names.get(node.id());
            out.append("class \"").append(GraphTextFormatter.escape(node.label()))
                    .append("\" as ").append(name).append(" {\n");
            for (DiagramCompartment compartment : node.compartments()) {
                for (DiagramMember member : compartment.members()) {
                    out.append("  ").append(GraphTextFormatter.escape(
                            GraphTextFormatter.memberLine(member, true))).append('\n');
                }
            }
            out.append("}\n");
        }
        for (DiagramEdge edge : document.edges()) {
            String source = names.get(edge.source().elementId());
            String target = names.get(edge.target().elementId());
            if (source == null || target == null) {
                continue;
            }
            if (edge.kind() == DiagramEdge.EdgeKind.INHERITANCE) {
                out.append(target).append(" <|-- ").append(source).append('\n');
            } else {
                out.append(source).append(" --> ").append(target);
                if (!edge.label().isBlank()) {
                    out.append(" : ").append(GraphTextFormatter.escape(edge.label()));
                }
                out.append('\n');
            }
        }
        out.append("@enduml\n");
        return out.toString();
    }
}
