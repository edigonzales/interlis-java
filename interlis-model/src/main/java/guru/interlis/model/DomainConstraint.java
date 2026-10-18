package guru.interlis.model;

import java.util.Objects;

/**
 * Domain constraint expression.
 *
 * @param condition condition expression
 */
public record DomainConstraint(ExpressionId condition) implements ConstraintDefinition {
    /** Creates a validated instance. */
    public DomainConstraint {
        Objects.requireNonNull(condition, "condition");
    }
}
