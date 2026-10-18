package guru.interlis.model;

import java.util.Objects;

/**
 * Public snapshot-bound expression handle.
 *
 * @param snapshot snapshot identifier
 * @param expression the {@code expression} component
 */
public record ExpressionRef(SnapshotId snapshot, ExpressionId expression) {
    /** Creates a validated instance. */
    public ExpressionRef {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(expression, "expression");
    }
}
