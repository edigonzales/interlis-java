package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Existence of a referenced object under an alternative target set.
 *
 * @param name declared name
 * @param source source identifier
 * @param targets the {@code targets} component
 */
public record ExistenceConstraint(Optional<String> name, ObjectPath source,
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
     * @param path name path
     */
    public record ExistenceTarget(SymbolId viewable, ObjectPath path) {
        /** Creates a validated instance. */
        public ExistenceTarget {
            Objects.requireNonNull(viewable, "viewable");
            Objects.requireNonNull(path, "path");
        }
    }
}
