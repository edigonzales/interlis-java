package guru.interlis.model;

/** Closed type-family classification. */
public enum TypeKind {
    /** The {@code CLASS_VALUE} constant. */
    CLASS_VALUE,
    /** The {@code GEOMETRY} constant. */
    GEOMETRY,
    /** The {@code FORMATTED} constant. */
    FORMATTED,
    /** The {@code TEXT} constant. */
    TEXT,
    /** The {@code BOOLEAN} constant. */
    BOOLEAN,
    /** The {@code ALIGNMENT} constant. */
    ALIGNMENT,
    /** The {@code NUMERIC} constant. */
    NUMERIC,
    /** The {@code ENUMERATION} constant. */
    ENUMERATION,
    /** The {@code COORDINATE} constant. */
    COORDINATE,
    /** The {@code TEMPORAL} constant. */
    TEMPORAL,
    /** The {@code BLACKBOX} constant. */
    BLACKBOX,
    /** The {@code OID} constant. */
    OID,
    /** The {@code REFERENCE} constant. */
    REFERENCE,
    /** The {@code DOMAIN_REFERENCE} constant. */
    DOMAIN_REFERENCE,
    /** The {@code STRUCTURE_VALUE} constant. */
    STRUCTURE_VALUE,
    /** The {@code COLLECTION} constant. */
    COLLECTION,
    /** The {@code GENERIC} constant. */
    GENERIC,
    /** The {@code DEFERRED} constant. */
    DEFERRED,
    /** The {@code ERROR} constant. */
    ERROR,
    /** The {@code PENDING} constant. */
    PENDING
}
