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
 * @param overrides immediate inherited attribute, including an ALL OF member
 * @param derivations immutable local alternatives in source order; follow {@code overrides}
 *                    from base to derived attribute before these alternatives. The last
 *                    defined alternative wins. IDs belong to the same semantic graph.
 */
public record AttributeSymbol(SymbolHeader header, TypeId declaredType,
                              boolean mandatory, boolean transientAttribute,
                              boolean continuous, boolean subdivision,
                              Optional<SymbolId> overrides, java.util.List<ExpressionId> derivations) implements Symbol {
    /** Creates a validated instance. */
    public AttributeSymbol {
        Objects.requireNonNull(header, "header");
        derivations = java.util.List.copyOf(derivations);
        Objects.requireNonNull(declaredType, "declaredType");
        overrides = Objects.requireNonNull(overrides, "overrides");
        if (header.kind() != SymbolKind.ATTRIBUTE) {
            throw new IllegalArgumentException("AttributeSymbol needs ATTRIBUTE header");
        }
    }
}
