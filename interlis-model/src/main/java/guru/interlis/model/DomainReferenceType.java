package guru.interlis.model;

import java.util.Objects;

/**
 * Alias use of a named domain; declared and effective types stay distinct.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param targetDomain the {@code targetDomain} component
 */
public record DomainReferenceType(TypeId id, Origin origin, SymbolId targetDomain)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public DomainReferenceType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(targetDomain, "targetDomain");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.DOMAIN_REFERENCE;
    }
}
