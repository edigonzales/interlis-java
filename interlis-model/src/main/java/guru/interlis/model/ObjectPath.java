package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Ordered semantic object/attribute path.
 *
 * @param steps the {@code steps} component
 */
public record ObjectPath(List<PathStep> steps) {
    /** Creates a validated instance. */
    public ObjectPath {
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("object path must not be empty");
        }
    }
}
