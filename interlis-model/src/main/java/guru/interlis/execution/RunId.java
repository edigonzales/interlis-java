package guru.interlis.execution;

import java.util.Objects;

/** Opaque run identity, unique within a factory namespace. */
public record RunId(String value) {
    /** Creates a validated instance. */
    public RunId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RunId must not be blank");
        }
    }
}
