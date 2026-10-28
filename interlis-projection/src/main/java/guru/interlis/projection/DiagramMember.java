package guru.interlis.projection;

import guru.interlis.model.SymbolId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ordered attribute/role/constraint member.
 *
 * @param id stable identifier
 * @param kind kind discriminator
 * @param label the {@code label} component
 * @param typeLabel the {@code typeLabel} component
 * @param cardinality cardinality
 * @param declaredSymbol declared symbol
 * @param effectiveOwner effective owner
 * @param inherited the {@code inherited} component
 * @param overridden whether an inherited member is overridden
 * @param anchor the {@code anchor} component
 */
public record DiagramMember(String id, MemberKind kind, String label,
                            Optional<String> typeLabel, Optional<String> cardinality,
                            SymbolId declaredSymbol, SymbolId effectiveOwner,
                            boolean inherited, boolean overridden,
                            Optional<SourceAnchor> anchor) {
    /** Creates a validated instance. */
    public DiagramMember {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        typeLabel = Objects.requireNonNull(typeLabel, "typeLabel");
        cardinality = Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(declaredSymbol, "declaredSymbol");
        Objects.requireNonNull(effectiveOwner, "effectiveOwner");
        anchor = Objects.requireNonNull(anchor, "anchor");
    }

    public enum MemberKind {
        PARAMETER,
        /** The {@code ATTRIBUTE} constant. */
        ATTRIBUTE,
        /** The {@code ROLE} constant. */
        ROLE,
        /** The {@code CONSTRAINT} constant. */
        CONSTRAINT
    }
}
