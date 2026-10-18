package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Association role with exact cardinality and target.
 *
 * @param header symbol header
 * @param target target symbol
 * @param cardinality cardinality
 * @param aggregation the {@code aggregation} component
 * @param ordered the {@code ordered} component
 * @param external external-role marker
 * @param hiding the {@code hiding} component
 */
public record RoleSymbol(SymbolHeader header, SymbolId target, Cardinality cardinality,
                         RoleAggregation aggregation, boolean ordered, boolean external,
                         boolean hiding) implements Symbol {
    /** Creates a validated instance. */
    public RoleSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(aggregation, "aggregation");
        if (header.kind() != SymbolKind.ROLE) {
            throw new IllegalArgumentException("RoleSymbol needs ROLE header");
        }
    }

    /**
     * Returns the {@code targetOptional} value.
     *
     * @return the value
     */
    public Optional<SymbolId> targetOptional() {
        return Optional.of(target);
    }
}
