package guru.interlis.projection;

import guru.interlis.model.SymbolRef;

import java.util.Objects;
import java.util.Optional;

/**
 * Model or topic container.
 *
 * @param id stable identifier
 * @param kind kind discriminator
 * @param parentId the {@code parentId} component
 * @param label the {@code label} component
 * @param canonicalName the {@code canonicalName} component
 * @param symbol the {@code symbol} component
 * @param anchor the {@code anchor} component
 */
public record DiagramContainer(String id, ContainerKind kind, Optional<String> parentId,
                               String label, String canonicalName,
                               Optional<SymbolRef> symbol, Optional<SourceAnchor> anchor) {
    /** Creates a validated instance. */
    public DiagramContainer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        parentId = Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(canonicalName, "canonicalName");
        symbol = Objects.requireNonNull(symbol, "symbol");
        anchor = Objects.requireNonNull(anchor, "anchor");
    }

    public enum ContainerKind {
        /** The {@code MODEL} constant. */
        MODEL,
        /** The {@code TOPIC} constant. */
        TOPIC
    }
}
