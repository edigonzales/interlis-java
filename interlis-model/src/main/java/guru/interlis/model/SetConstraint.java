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

    /** Conceptual extent; execution may inspect only the available baskets without narrowing this rule. */
    public enum SetConstraintScope {
        /** All objects of the class, including objects beyond a single transfer. */
        GLOBAL,
        /** Objects of the class within the current basket (INTERLIS 2.4). */
        BASKET
    }
}
