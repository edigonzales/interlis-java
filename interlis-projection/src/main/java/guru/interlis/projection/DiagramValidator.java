package guru.interlis.projection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Structural validation of DiagramDocument 1.0. */
public final class DiagramValidator {
    /**
     * Executes {@code validate}.
     *
     * @param document the {@code document} argument
     *
     * @return the result
     */
    public List<String> validate(DiagramDocument document) {
        Objects.requireNonNull(document, "document");
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> containers = new HashSet<>();
        for (DiagramContainer container : document.containers()) {
            if (!ids.add(container.id())) {
                errors.add("duplicate id " + container.id());
            }
            containers.add(container.id());
        }
        for (DiagramContainer container : document.containers()) {
            if (container.parentId().isPresent() && !containers.contains(container.parentId().get())) {
                errors.add("missing parent " + container.parentId().get());
            }
        }
        for (DiagramNode node : document.nodes()) {
            if (!ids.add(node.id())) {
                errors.add("duplicate id " + node.id());
            }
            if (!containers.contains(node.containerId())) {
                errors.add("missing container " + node.containerId());
            }
        }
        Set<String> nodeIds = new HashSet<>();
        document.nodes().forEach(n -> nodeIds.add(n.id()));
        for (DiagramEdge edge : document.edges()) {
            if (!ids.add(edge.id())) {
                errors.add("duplicate id " + edge.id());
            }
            if (!nodeIds.contains(edge.source().elementId())) {
                errors.add("missing source " + edge.source().elementId());
            }
            if (!nodeIds.contains(edge.target().elementId())) {
                errors.add("missing target " + edge.target().elementId());
            }
        }
        return List.copyOf(errors);
    }
}
