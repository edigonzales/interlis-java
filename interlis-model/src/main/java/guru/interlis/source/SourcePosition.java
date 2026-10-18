package guru.interlis.source;

/**
 * Zero-based line and UTF-16 character position.
 *
 * @param line zero-based line index
 * @param character zero-based UTF-16 code-unit column
 */
public record SourcePosition(int line, int character) {
    /** Creates a validated instance. */
    public SourcePosition {
        if (line < 0 || character < 0) {
            throw new IllegalArgumentException("line and character must be non-negative");
        }
    }
}
