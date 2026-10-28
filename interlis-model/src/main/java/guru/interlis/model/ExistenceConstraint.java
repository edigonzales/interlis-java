package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Existence of an attribute value under an alternative target set. Expression IDs
 * belong to the containing snapshot and retain types, ranges and error states.
 * Alternatives are immutable, null-free and preserve source order.
 *
 * @param name declared name
 * @param source snapshot-local source path expression, including type and diagnostic state
 * @param targets the {@code targets} component
 */
public record ExistenceConstraint(Optional<String> name, ExpressionId source,
                                  List<ExistenceTarget> targets)
        implements ConstraintDefinition {
    /** Creates a validated instance. */
    public ExistenceConstraint {
        name = Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }

    /**
     * Immutable value.
     *
     * @param viewable the {@code viewable} component
     * @param path snapshot-local target path expression
     */
    public record ExistenceTarget(SymbolId viewable, ExpressionId path) {
        /** Creates a validated instance. */
        public ExistenceTarget {
            Objects.requireNonNull(viewable, "viewable");
            Objects.requireNonNull(path, "path");
        }
    }
}
