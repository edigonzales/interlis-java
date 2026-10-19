package guru.interlis.syntax;

/** Closed syntax-node classification. */
public enum SyntaxKind {
    /** The {@code DOCUMENT} constant. */
    DOCUMENT,
    /** The {@code MODEL} constant. */
    MODEL,
    /** The {@code TOPIC} constant. */
    TOPIC,
    /** The {@code IMPORT} constant. */
    IMPORT,
    /** The {@code CLASS} constant. */
    CLASS,
    /** The {@code STRUCTURE} constant. */
    STRUCTURE,
    /** The {@code DOMAIN} constant. */
    DOMAIN,
    /** The {@code UNIT} constant. */
    UNIT,
    /** The {@code FUNCTION} constant. */
    FUNCTION,
    /** The {@code EXTERNAL_CONSTRAINT} constant. */
    EXTERNAL_CONSTRAINT,
    /** The {@code VIEW} constant. */
    VIEW,
    /** The {@code ASSOCIATION} constant. */
    ASSOCIATION,
    /** The {@code ATTRIBUTE} constant. */
    ATTRIBUTE,
    /** The {@code ROLE} constant. */
    ROLE,
    /** The {@code CONSTRAINT} constant. */
    CONSTRAINT,
    /** The {@code UNSUPPORTED_DECLARATION} constant. */
    UNSUPPORTED_DECLARATION,
    /** The {@code TYPE_TEXT} constant. */
    TYPE_TEXT,
    /** The {@code TYPE_NUMERIC} constant. */
    TYPE_NUMERIC,
    /** The {@code TYPE_BOOLEAN} constant. */
    TYPE_BOOLEAN,
    /** The {@code TYPE_ENUMERATION} constant. */
    TYPE_ENUMERATION,
    /** The {@code TYPE_DOMAIN_REFERENCE} constant. */
    TYPE_DOMAIN_REFERENCE,
    /** The {@code TYPE_REFERENCE} constant. */
    TYPE_REFERENCE,
    /** The {@code TYPE_OID} constant. */
    TYPE_OID,

    /** The {@code TYPE_BLACKBOX} constant. */
    TYPE_BLACKBOX,

    /** The {@code TYPE_ALIGNMENT} constant. */
    TYPE_ALIGNMENT,

    /** The {@code TYPE_TEMPORAL} constant. */
    TYPE_TEMPORAL,

    /** The {@code TYPE_CLASS_VALUE} constant. */
    TYPE_CLASS_VALUE,

    /** The {@code TYPE_LINE} constant. */
    TYPE_LINE,

    /** The {@code TYPE_FORMATTED} constant. */
    TYPE_FORMATTED,

    /** The {@code TYPE_COLLECTION} constant. */
    TYPE_COLLECTION,
    /** The {@code TYPE_COORDINATE} constant. */
    TYPE_COORDINATE,
    /** The {@code TYPE_ENUM_TREE} constant. */
    TYPE_ENUM_TREE,
    /** The {@code TYPE_GENERIC} constant. */
    TYPE_GENERIC,
    /** The {@code TYPE_UNSUPPORTED} constant. */
    TYPE_UNSUPPORTED,
    /** The {@code EXPRESSION_LITERAL} constant. */
    EXPRESSION_LITERAL,
    /** The {@code EXPRESSION_PATH} constant. */
    EXPRESSION_PATH,
    /** The {@code EXPRESSION_BINARY} constant. */
    EXPRESSION_BINARY,
    /** The {@code EXPRESSION_GENERIC_CONSTANT} constant. */
    EXPRESSION_GENERIC_CONSTANT,
    /** The {@code EXPRESSION_CLASS_CONSTANT} constant. */
    EXPRESSION_CLASS_CONSTANT,
    /** The {@code EXPRESSION_ENUM_CONSTANT} constant. */
    EXPRESSION_ENUM_CONSTANT,
    /** The {@code EXPRESSION_BUILTIN} constant. */
    EXPRESSION_BUILTIN,
    /** The {@code EXPRESSION_FUNCTION_CALL} constant. */
    EXPRESSION_FUNCTION_CALL,
    /** The {@code EXPRESSION_UNARY} constant. */
    EXPRESSION_UNARY,
    /** The {@code EXPRESSION_DEFINED} constant. */
    EXPRESSION_DEFINED,
    /** The {@code EXPRESSION_CONSTANT} constant. */
    EXPRESSION_CONSTANT,
    /** The {@code EXPRESSION_UNDEFINED} constant. */
    EXPRESSION_UNDEFINED,
    /** The {@code EXPRESSION_PARENTHESIZED} constant. */
    EXPRESSION_PARENTHESIZED,
    /** The {@code EXPRESSION_UNSUPPORTED} constant. */
    EXPRESSION_UNSUPPORTED,
    /** The {@code NAME} constant. */
    NAME,
    /** The {@code ERROR} constant. */
    ERROR,
    /** The {@code RECOVERED} constant. */
    RECOVERED,
    /** The {@code SKIPPED} constant. */
    SKIPPED
}
