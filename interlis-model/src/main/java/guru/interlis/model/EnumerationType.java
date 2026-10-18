package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Enumeration tree plus ORDERED/CIRCULAR/FINAL metadata.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param values the {@code values} component
 * @param ordered the {@code ordered} component
 * @param circular the {@code circular} component
 * @param finalPart the {@code finalPart} component
 */
public record EnumerationType(TypeId id, Origin origin, List<EnumerationValue> values,
                              boolean ordered, boolean circular, boolean finalPart)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public EnumerationType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
    }

    @Override
    public TypeKind kind() {
        return TypeKind.ENUMERATION;
    }
}
