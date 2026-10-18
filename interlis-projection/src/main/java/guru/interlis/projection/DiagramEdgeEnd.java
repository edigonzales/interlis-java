package guru.interlis.projection;

import guru.interlis.model.Cardinality;
import guru.interlis.model.RoleAggregation;

import java.util.Objects;
import java.util.Optional;

/**
 * Exact semantic edge-end information.
 *
 * @param roleName the {@code roleName} component
 * @param cardinality cardinality
 * @param aggregation the {@code aggregation} component
 */
public record DiagramEdgeEnd(Optional<String> roleName, Optional<Cardinality> cardinality,
                             RoleAggregation aggregation) {
    /** Creates a validated instance. */
    public DiagramEdgeEnd {
        roleName = Objects.requireNonNull(roleName, "roleName");
        cardinality = Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(aggregation, "aggregation");
    }
}
