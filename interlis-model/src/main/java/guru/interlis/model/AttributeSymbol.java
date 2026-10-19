package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Attribute with declared type and override link when present.
 *
 * @param header symbol header
 * @param declaredType the {@code declaredType} component
 * @param mandatory mandatory marker
 * @param transientAttribute transient marker
 * @param continuous CONTINUOUS marker
 * @param subdivision SUBDIVISION marker
 * @param overrides the {@code overrides} component
 */
public record AttributeSymbol(SymbolHeader header, TypeId declaredType,
                              boolean mandatory, boolean transientAttribute,
                              boolean continuous, boolean subdivision,
                              Optional<SymbolId> overrides) implements Symbol {
    /** Creates a validated instance. */
    public AttributeSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(declaredType, "declaredType");
        overrides = Objects.requireNonNull(overrides, "overrides");
        if (header.kind() != SymbolKind.ATTRIBUTE) {
            throw new IllegalArgumentException("AttributeSymbol needs ATTRIBUTE header");
        }
    }
}
