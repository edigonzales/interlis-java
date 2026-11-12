package guru.interlis.validation;

import java.util.Objects;

/** Non-null host identity and role for one decoded transfer input. */
public record InputDescriptor(InputId id, InputRole role) {
    public InputDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }
}
