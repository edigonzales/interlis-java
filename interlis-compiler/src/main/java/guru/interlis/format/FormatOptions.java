package guru.interlis.format;

import java.util.Objects;

/**
 * Formatting preferences; no IO or environment state is part of this value.
 *
 * @param indentWidth spaces per indentation level
 * @param lineEnding requested line ending policy
 * @param finalNewline whether the formatted text ends with a line ending
 */
public record FormatOptions(int indentWidth, LineEnding lineEnding, boolean finalNewline) {
    /** Supported line-ending policies. */
    public enum LineEnding {
        /** Preserve the original line ending per line. */
        PRESERVE,
        /** Use LF. */
        LF,
        /** Use CRLF. */
        CRLF
    }

    /** Creates a validated instance. */
    public FormatOptions {
        if (indentWidth < 0 || indentWidth > 16) {
            throw new IllegalArgumentException("indentWidth must be between 0 and 16");
        }
        Objects.requireNonNull(lineEnding, "lineEnding");
    }

    /**
     * Returns the default options.
     *
     * @return two spaces, preserved line endings, final newline
     */
    public static FormatOptions defaults() {
        return new FormatOptions(2, LineEnding.PRESERVE, true);
    }
}
