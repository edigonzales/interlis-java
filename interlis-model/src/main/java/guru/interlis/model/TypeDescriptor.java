package guru.interlis.model;

/** Closed type descriptor hierarchy. */
public sealed interface TypeDescriptor permits TextType, BooleanType, AlignmentType,
        NumericType, EnumerationType, CoordinateType, TemporalType, BlackboxType, OidType,
        ReferenceType, DomainReferenceType, StructureValueType, CollectionType, FormattedType, GeometryType, ClassValueType, GenericType,
        DeferredTypeBinding, ErrorType, PendingType {

    /**
     * Returns the {@code id} value.
     *
     * @return the value
     */
    TypeId id();

    /**
     * Returns the {@code kind} value.
     *
     * @return the value
     */
    TypeKind kind();

    /**
     * Returns the {@code origin} value.
     *
     * @return the value
     */
    Origin origin();
}
