package guru.interlis.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Percentage plausibility condition.
 *
 * @param name declared name
 * @param percentage the {@code percentage} component
 * @param direction the {@code direction} component
 * @param condition condition expression
 */
public record PlausibilityConstraint(Optional<String> name, BigDecimal percentage,
                                     BinaryOperator direction, ExpressionId condition)
        implements ConstraintDefinition {
    /** Creates a validated instance. */
    public PlausibilityConstraint {
        name = Objects.requireNonNull(name, "name");
        Objects.requireNonNull(percentage, "percentage");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(condition, "condition");
    }
}
