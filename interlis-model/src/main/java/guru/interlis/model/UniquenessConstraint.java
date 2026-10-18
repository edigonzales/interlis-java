package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Global or local uniqueness condition.
 *
 * @param name declared name
 * @param basket whether the constraint is basket-wide
 * @param where optional filter expression
 * @param keys key fields
 * @param localKeys local key groups
 */
public record UniquenessConstraint(Optional<String> name, boolean basket,
                                   Optional<ExpressionId> where,
                                   List<ObjectPath> keys,
                                   List<LocalUniqueness> localKeys)
        implements ConstraintDefinition {
    /** Creates a validated instance. */
    public UniquenessConstraint {
        name = Objects.requireNonNull(name, "name");
        where = Objects.requireNonNull(where, "where");
        keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
        localKeys = List.copyOf(Objects.requireNonNull(localKeys, "localKeys"));
    }

    /**
     * Immutable value.
     *
     * @param name declared name
     * @param prefix path prefix
     * @param fields field paths
     */
    public record LocalUniqueness(String name, List<String> prefix, List<String> fields) {
        /** Creates a validated instance. */
        public LocalUniqueness {
            Objects.requireNonNull(name, "name");
            prefix = List.copyOf(Objects.requireNonNull(prefix, "prefix"));
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        }
    }
}
