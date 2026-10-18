package guru.interlis.projection;

import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Renderer-independent node.
 *
 * @param id stable identifier
 * @param kind kind discriminator
 * @param containerId the {@code containerId} component
 * @param label the {@code label} component
 * @param canonicalName the {@code canonicalName} component
 * @param status the {@code status} component
 * @param symbol the {@code symbol} component
 * @param compartments the {@code compartments} component
 * @param anchor the {@code anchor} component
 */
public record DiagramNode(String id, DiagramNodeKind kind, String containerId, String label,
                          String canonicalName, DiagramNodeStatus status,
                          Optional<SymbolRef> symbol, List<DiagramCompartment> compartments,
                          Optional<SourceAnchor> anchor) {
    /** Creates a validated instance. */
    public DiagramNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(canonicalName, "canonicalName");
        Objects.requireNonNull(status, "status");
        symbol = Objects.requireNonNull(symbol, "symbol");
        compartments = List.copyOf(Objects.requireNonNull(compartments, "compartments"));
        anchor = Objects.requireNonNull(anchor, "anchor");
    }
}
