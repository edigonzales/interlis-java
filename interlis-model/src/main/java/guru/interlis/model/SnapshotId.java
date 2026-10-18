package guru.interlis.model;

import java.util.Objects;

/**
 * Identity of one published analysis snapshot.
 *
 * @param value wrapped value
 */
public record SnapshotId(String value) {
    /** Creates a validated instance. */
    public SnapshotId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SnapshotId must not be blank");
        }
    }
}
