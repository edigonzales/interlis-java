package guru.interlis.model;

/** Closed type descriptor hierarchy. */
public sealed interface TypeDescriptor permits EnumArgumentType, MetaObjectType, TextType, BooleanType, AlignmentType,
        NumericType, EnumerationType, CoordinateType, BlackboxType, OidType,
        ReferenceType, DomainReferenceType, StructureValueType, CollectionType, FormattedType, GeometryType, ClassValueType, AttributePathType, GenericType,
        ObjectArgumentType, DeferredTypeBinding, ErrorType, PendingType {

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
