package guru.interlis.validation;

import java.util.Objects;

/** Host-assigned identifier of one transfer input within a dataset run. */
public record InputId(String value) {
    public InputId {
        if (Objects.requireNonNull(value, "value").isBlank()) {
            throw new IllegalArgumentException("Input id must not be blank");
        }
    }
}
