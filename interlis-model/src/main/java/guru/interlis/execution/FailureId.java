package guru.interlis.execution;

import java.util.Objects;

/** Identity of one internal failure; not a user-facing diagnostic code. */
public record FailureId(String value) {
    /** Creates a validated instance. */
    public FailureId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("FailureId must not be blank");
        }
    }
}
