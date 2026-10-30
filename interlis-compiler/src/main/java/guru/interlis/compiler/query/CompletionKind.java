package guru.interlis.compiler.query;

/** Completion provenance category. */
public enum CompletionKind {
    /** Bound semantic categories remain distinct from syntax keywords. */
    FUNCTION, UNIT, VIEW, VIEW_BASE, CONTEXT, ENUM_ELEMENT,
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
    /** The {@code DOMAIN} constant. */
    DOMAIN,
    /** The {@code ATTRIBUTE} constant. */
    ATTRIBUTE,
    /** The {@code ROLE} constant. */
    ROLE,
    /** Runtime parameter, separate from ordinary member names. */
    PARAMETER,
    LINE_FORM,
    METADATA_BASKET, METAOBJECT,
    /** The {@code CONSTRAINT} constant. */
    CONSTRAINT,
    /** The {@code KEYWORD} constant. */
    KEYWORD
}
