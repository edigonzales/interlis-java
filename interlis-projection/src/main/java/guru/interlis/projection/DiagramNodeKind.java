package guru.interlis.projection;

/** Renderer-independent node category. */
public enum DiagramNodeKind {
    GRAPHIC, DRAWING_RULE,
    /** The {@code CLASS} constant. */
    CLASS,
    /** The {@code STRUCTURE} constant. */
    STRUCTURE,
    /** The {@code ASSOCIATION} constant. */
    ASSOCIATION,
    /** The {@code VIEW} constant. */
    VIEW,
    /** The {@code DOMAIN} constant. */
    DOMAIN,
    /** Declared curve form referring to a segment structure. */
    LINE_FORM,
    METADATA_BASKET, METAOBJECT,
    /** The {@code EXTERNAL_REFERENCE} constant. */
    EXTERNAL_REFERENCE
}
