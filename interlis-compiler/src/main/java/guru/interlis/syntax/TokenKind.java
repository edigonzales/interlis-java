package guru.interlis.syntax;

/** Token classification independent of ANTLR token numbers. */
public enum TokenKind {
    /** The {@code KEYWORD} constant. */
    KEYWORD,
    /** The {@code IDENTIFIER} constant. */
    IDENTIFIER,
    /** The {@code NUMBER} constant. */
    NUMBER,
    /** The {@code STRING} constant. */
    STRING,
    /** The {@code PUNCTUATION} constant. */
    PUNCTUATION,
    /** The {@code EOF} constant. */
    EOF,
    /** The {@code OTHER} constant. */
    OTHER
}
