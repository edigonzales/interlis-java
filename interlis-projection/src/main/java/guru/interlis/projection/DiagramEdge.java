package guru.interlis.projection;

import guru.interlis.model.SymbolRef;

import java.util.Objects;
import java.util.Optional;

/**
 * Directed semantic edge.
 *
 * @param id stable identifier
 * @param kind kind discriminator
 * @param source source identifier
 * @param target target symbol
 * @param origin origin information
 * @param sourceEnd the {@code sourceEnd} component
 * @param targetEnd the {@code targetEnd} component
 * @param label the {@code label} component
 * @param anchor the {@code anchor} component
 */
public record DiagramEdge(String id, EdgeKind kind, DiagramEndpoint source,
                          DiagramEndpoint target, Optional<SymbolRef> origin,
                          DiagramEdgeEnd sourceEnd, DiagramEdgeEnd targetEnd, String label,
                          Optional<SourceAnchor> anchor) {
    /** Creates a validated instance. */
    public DiagramEdge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        origin = Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sourceEnd, "sourceEnd");
        Objects.requireNonNull(targetEnd, "targetEnd");
        Objects.requireNonNull(label, "label");
        anchor = Objects.requireNonNull(anchor, "anchor");
    }

    public enum EdgeKind {
        /** The {@code INHERITANCE} constant. */
        INHERITANCE,
        /** The {@code ASSOCIATION_ROLE} constant. */
        ASSOCIATION_ROLE,
        /** The {@code REFERENCE} constant. */
        REFERENCE,
        /** The {@code STRUCTURE_COMPOSITION} constant. */
        STRUCTURE_COMPOSITION,
        /** The {@code DOMAIN_USE} constant. */
        DOMAIN_USE,
        /** The {@code VIEW_DERIVATION} constant. */
        VIEW_DERIVATION,
        /** The {@code MODEL_IMPORT} constant. */
        MODEL_IMPORT,
        /** The {@code TRANSLATION} constant. */
        TRANSLATION
    }
}
