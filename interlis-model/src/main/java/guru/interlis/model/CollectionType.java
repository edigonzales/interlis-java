package guru.interlis.model;

import java.util.Objects;

/**
 * BAG/LIST with exact cardinality and element type.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param collectionKind the {@code collectionKind} component
 * @param cardinality cardinality
 * @param elementType the {@code elementType} component
 */
public record CollectionType(TypeId id, Origin origin, CollectionKind collectionKind,
                             Cardinality cardinality, TypeId elementType)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public CollectionType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(collectionKind, "collectionKind");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(elementType, "elementType");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.COLLECTION;
    }
}
