package guru.interlis.model;

/** Closed symbol categories used by the P1 model. */
public enum SymbolKind {
    GRAPHIC, DRAWING_RULE,
    /** The {@code MODEL} constant. */
    MODEL,
    /** The {@code TOPIC} constant. */
    TOPIC,
    /** The {@code CLASS} constant. */
    CLASS,
    /** The {@code STRUCTURE} constant. */
    STRUCTURE,
    /** The {@code ASSOCIATION} constant. */
    ASSOCIATION,
    /** The {@code ATTRIBUTE} constant. */
    ATTRIBUTE,
    /** The {@code ROLE} constant. */
    ROLE,
    /** The {@code DOMAIN} constant. */
    DOMAIN,
    /** The {@code CONSTRAINT} constant. */
    CONSTRAINT,
    /** The {@code ENUM_ELEMENT} constant. */
    ENUM_ELEMENT,
    /** The {@code UNIT} constant. */
    UNIT,
    /** The {@code FUNCTION} constant. */
    FUNCTION,
    /** Model-level runtime parameter, in a separate namespace. */
    PARAMETER,
    /** Generic coordinate context, scoped to a model. */
    CONTEXT,
    /** External metadata basket and named object declarations. */
    METADATA_BASKET, METAOBJECT,
    /** Bound curve form and segment structure. */
    LINE_FORM,
    /** The {@code VIEW} constant. */
    VIEW,
    /** Locally named view formation basis. */
    VIEW_BASE,
    /** The {@code UNSUPPORTED} constant. */
    UNSUPPORTED
}
