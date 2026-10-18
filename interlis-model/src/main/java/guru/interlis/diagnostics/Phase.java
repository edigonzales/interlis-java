package guru.interlis.diagnostics;

/** Stable diagnostic phase categories shared by compiler, workspace and adapters. */
public enum Phase {
    /** The {@code SOURCE_RESOLUTION} constant. */
    SOURCE_RESOLUTION,
    /** The {@code LEXING} constant. */
    LEXING,
    /** The {@code PARSING} constant. */
    PARSING,
    /** The {@code DECLARATION_INDEXING} constant. */
    DECLARATION_INDEXING,
    /** The {@code BINDING} constant. */
    BINDING,
    /** The {@code TYPE_CHECKING} constant. */
    TYPE_CHECKING,
    /** The {@code SEMANTIC_CHECKING} constant. */
    SEMANTIC_CHECKING,
    /** The {@code PROJECTION} constant. */
    PROJECTION,
    /** The {@code TRANSFER} constant. */
    TRANSFER,
    /** The {@code VALIDATION} constant. */
    VALIDATION,
    /** The {@code DB_MAPPING} constant. */
    DB_MAPPING,
    /** The {@code DB_EXECUTION} constant. */
    DB_EXECUTION,
    /** The {@code INFRASTRUCTURE} constant. */
    INFRASTRUCTURE
}
