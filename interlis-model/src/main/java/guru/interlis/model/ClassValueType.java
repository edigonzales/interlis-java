package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * CLASS or STRUCTURE value type with optional restriction names.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param structureValue whether this is a STRUCTURE value
 * @param restrictions restriction names
 */
public record ClassValueType(TypeId id, Origin origin, boolean structureValue,
                             List<String> restrictions) implements TypeDescriptor {
    /** Creates a validated instance. */
    public ClassValueType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        restrictions = List.copyOf(Objects.requireNonNull(restrictions, "restrictions"));
    }

    @Override
    public TypeKind kind() {
        return TypeKind.CLASS_VALUE;
    }
}
