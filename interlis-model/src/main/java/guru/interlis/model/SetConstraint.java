package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Set/basket constraint with its later execution scope.
 *
 * @param name declared name
 * @param scope the {@code scope} component
 * @param where optional filter expression
 * @param condition condition expression
 */
public record SetConstraint(Optional<String> name, SetConstraintScope scope,
                            Optional<ExpressionId> where, ExpressionId condition)
        implements ConstraintDefinition {
    /** Creates a validated instance. */
    public SetConstraint {
        name = Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        where = Objects.requireNonNull(where, "where");
        Objects.requireNonNull(condition, "condition");
    }

    public enum SetConstraintScope {
        /** The {@code LOCAL} constant. */
        LOCAL,
        /** The {@code BASKET} constant. */
        BASKET,
        /** The {@code TRANSFER} constant. */
        TRANSFER
    }
}
