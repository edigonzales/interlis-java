package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * FORMAT type with a format name and optional lexical bounds.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param formatName format identifier
 * @param minimum optional lexical minimum
 * @param maximum optional lexical maximum
 */
public record FormattedType(TypeId id, Origin origin, String formatName,
                            Optional<String> minimum, Optional<String> maximum)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public FormattedType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(formatName, "formatName");
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.FORMATTED;
    }
}
