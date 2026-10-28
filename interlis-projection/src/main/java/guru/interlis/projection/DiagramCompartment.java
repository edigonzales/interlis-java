package guru.interlis.projection;

import java.util.List;
import java.util.Objects;

/**
 * Ordered member compartment.
 *
 * @param id stable identifier
 * @param kind kind discriminator
 * @param members member identifiers
 */
public record DiagramCompartment(String id, CompartmentKind kind,
                                 List<DiagramMember> members) {
    /** Creates a validated instance. */
    public DiagramCompartment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
    }

    public enum CompartmentKind {
        PARAMETERS,
        /** The {@code ATTRIBUTES} constant. */
        ATTRIBUTES,
        /** The {@code ROLES} constant. */
        ROLES,
        /** The {@code CONSTRAINTS} constant. */
        CONSTRAINTS,
        /** The {@code ENUM_VALUES} constant. */
        ENUM_VALUES,
        /** The {@code DETAILS} constant. */
        DETAILS
    }
}
