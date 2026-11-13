package guru.interlis.documentation;

import guru.interlis.projection.*;

import java.util.*;

/**
 * GraphML over the exact neutral diagram, with containers, semantic members and endpoint metadata.
 * No layout is invented. N-ary associations remain nodes with one edge per role.
 */
public final class GraphMlRenderer {
    public String render(DiagramDocument d) {
        var b =
                new StringBuilder(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><graphml"
                                + " xmlns=\"http://graphml.graphdrawing.org/xmlns\">");
        for (String key :
                List.of(
                        "label",
                        "kind",
                        "parent",
                        "members",
                        "sourceRole",
                        "targetRole",
                        "sourceCardinality",
                        "targetCardinality",
                        "sourceAggregation",
                        "targetAggregation",
                        "snapshot",
                        "completeness",
                        "canonicalName"))
            b.append("<key id=\"")
                    .append(key)
                    .append("\" for=\"all\" attr.name=\"")
                    .append(key)
                    .append("\" attr.type=\"string\"/>");
        b.append("<graph id=\"g\" edgedefault=\"directed\">");
        data(b, "snapshot", d.snapshotId().value());
        data(b, "completeness", d.completeness().name());
        var ids = new HashSet<String>();
        for (var c : d.containers()) {
            if (!ids.add(c.id())) throw new IllegalArgumentException("duplicate GraphML identity");
            b.append("<node id=\"").append(id(c.id())).append("\">");
            data(b, "label", c.label());
            data(b, "canonicalName", c.canonicalName());
            data(b, "kind", c.kind().name());
            c.parentId().ifPresent(p -> data(b, "parent", id(p)));
            b.append("</node>");
        }
        for (var n : d.nodes()) {
            if (!ids.add(n.id())) throw new IllegalArgumentException("duplicate GraphML identity");
            b.append("<node id=\"").append(id(n.id())).append("\">");
            data(b, "label", n.label());
            data(b, "canonicalName", n.canonicalName());
            data(b, "kind", n.kind().name());
            data(b, "parent", id(n.containerId()));
            var lines = new ArrayList<String>();
            for (var c : n.compartments())
                for (var m : c.members())
                    lines.add(
                            m.label()
                                    + m.typeLabel().map(t -> " : " + t).orElse("")
                                    + m.cardinality().map(t -> " " + t).orElse("")
                                    + (m.inherited() ? " {inherited}" : ""));
            data(b, "members", String.join("\n", lines));
            b.append("</node>");
        }
        for (var e : d.edges()) {
            if (!ids.contains(e.source().elementId()) || !ids.contains(e.target().elementId()))
                throw new IllegalArgumentException("dangling GraphML endpoint");
            b.append("<edge id=\"")
                    .append(id(e.id()))
                    .append("\" source=\"")
                    .append(id(e.source().elementId()))
                    .append("\" target=\"")
                    .append(id(e.target().elementId()))
                    .append("\">");
            data(b, "label", e.label());
            data(b, "kind", e.kind().name());
            end(b, "source", e.sourceEnd());
            end(b, "target", e.targetEnd());
            b.append("</edge>");
        }
        b.append("</graph></graphml>");
        DocumentationText.checkSize(b.length());
        return b.toString();
    }

    private static String id(String id) {
        return DocumentationText.anchor(id);
    }

    private static void data(StringBuilder b, String key, String value) {
        b.append("<data key=\"")
                .append(key)
                .append("\">")
                .append(DocumentationText.xml(value))
                .append("</data>");
    }

    private static void end(StringBuilder b, String prefix, DiagramEdgeEnd e) {
        data(b, prefix + "Role", e.roleName().orElse(""));
        data(b, prefix + "Cardinality", e.cardinality().map(SemanticDescriptions::card).orElse(""));
        data(b, prefix + "Aggregation", e.aggregation().name());
    }
}
