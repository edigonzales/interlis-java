package guru.interlis.model;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * TEXT, MTEXT, NAME or URI with optional exact maximum length.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param textKind the {@code textKind} component
 * @param maxLength the {@code maxLength} component
 */
public record TextType(TypeId id, Origin origin, TextKind textKind,
                       OptionalInt maxLength) implements TypeDescriptor {
    /** Creates a validated instance. */
    public TextType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(textKind, "textKind");
        Objects.requireNonNull(maxLength, "maxLength");
        if (maxLength.isPresent() && maxLength.getAsInt() < 0) {
            throw new IllegalArgumentException("maxLength must be non-negative");
        }
    }

    @Override
    public TypeKind kind() {
        return TypeKind.TEXT;
    }
}
