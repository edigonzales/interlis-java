package guru.interlis.model;

import java.util.Objects;

/**
 * HALIGNMENT / VALIGNMENT.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param alignment the {@code alignment} component
 */
public record AlignmentType(TypeId id, Origin origin, AlignmentKind alignment)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public AlignmentType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(alignment, "alignment");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.ALIGNMENT;
    }
}
