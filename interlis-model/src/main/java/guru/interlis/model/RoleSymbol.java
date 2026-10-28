package guru.interlis.model;

import java.util.Objects;
import java.util.List;

/**
 * Association role with exact cardinality and target.
 *
 * @param header symbol header
 * @param targets nonempty alternatives in declaration order
 * @param cardinality cardinality
 * @param aggregation the {@code aggregation} component
 * @param ordered the {@code ordered} component
 * @param external external-role marker
 * @param hiding the {@code hiding} component
 * @param derivation snapshot-local expression assigning the participant; empty without assignment
 */
public record RoleSymbol(SymbolHeader header, List<RoleTarget> targets, Cardinality cardinality,
                         RoleAggregation aggregation, boolean ordered, boolean external,
                         boolean hiding, java.util.Optional<ExpressionId> derivation) implements Symbol {
    /** Creates a validated instance. */
    public RoleSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(derivation);
        targets = List.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("role needs targets");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(aggregation, "aggregation");
        if (header.kind() != SymbolKind.ROLE) {
            throw new IllegalArgumentException("RoleSymbol needs ROLE header");
        }
    }

    /** Distinct allowed hierarchy roots across all alternatives, in declaration order. */
    public List<SymbolId> targetRoots() {
        return targets.stream().flatMap(t -> t.allowedRoots().stream()).distinct().toList();
    }
}
