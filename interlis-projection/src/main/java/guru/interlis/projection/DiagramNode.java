package guru.interlis.projection;

import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import guru.interlis.model.Modifier;

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
 * @param modifiers immutable source-level declaration modifiers from the common graph
 */
public record DiagramNode(String id, DiagramNodeKind kind, String containerId, String label,
                          String canonicalName, DiagramNodeStatus status,
                          Optional<SymbolRef> symbol, List<DiagramCompartment> compartments,
                          Optional<SourceAnchor> anchor, Set<Modifier> modifiers) {
    /** Creates a node without source-level declaration modifiers. All collections are copied. */
    public DiagramNode(String id,DiagramNodeKind kind,String containerId,String label,String canonicalName,
                       DiagramNodeStatus status,Optional<SymbolRef> symbol,List<DiagramCompartment> compartments,Optional<SourceAnchor> anchor) {
        this(id,kind,containerId,label,canonicalName,status,symbol,compartments,anchor,Set.of());
    }
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
        modifiers=Set.copyOf(Objects.requireNonNull(modifiers,"modifiers"));
    }
}
