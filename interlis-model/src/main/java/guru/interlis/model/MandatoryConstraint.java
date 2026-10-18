package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Boolean consistency condition.
 *
 * @param name declared name
 * @param condition condition expression
 */
public record MandatoryConstraint(Optional<String> name, ExpressionId condition)
        implements ConstraintDefinition {
    /** Creates a validated instance. */
    public MandatoryConstraint {
        name = Objects.requireNonNull(name, "name");
        Objects.requireNonNull(condition, "condition");
    }
}
