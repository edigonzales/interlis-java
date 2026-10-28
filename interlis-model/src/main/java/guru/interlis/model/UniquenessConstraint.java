package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Global or local uniqueness condition. All expression IDs belong to the containing snapshot.
 * Lists are immutable, null-free and preserve source order; expressions retain types and ranges.
 *
 * @param name declared name
 * @param basket whether the constraint is basket-wide
 * @param where optional filter expression
 * @param keys global key expressions; empty for LOCAL
 * @param localKeys local prefix and field expressions; empty for global uniqueness
 */
public record UniquenessConstraint(Optional<String> name, boolean basket,
                                   Optional<ExpressionId> where,
                                   List<ExpressionId> keys,
                                   List<LocalUniqueness> localKeys)
        implements ConstraintDefinition {
    /** Creates a validated instance. */
    public UniquenessConstraint {
        name = Objects.requireNonNull(name, "name");
        where = Objects.requireNonNull(where, "where");
        keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
        localKeys = List.copyOf(Objects.requireNonNull(localKeys, "localKeys"));
    }

    /** LOCAL prefix and field expressions, all snapshot-local and in source order.
     * Fields bind in the structure selected by the prefix, independently of its multiplicity. */
    public record LocalUniqueness(ExpressionId prefix, List<ExpressionId> fields) {
        public LocalUniqueness {
            Objects.requireNonNull(prefix, "prefix");
            fields = List.copyOf(fields);
        }
    }
}
