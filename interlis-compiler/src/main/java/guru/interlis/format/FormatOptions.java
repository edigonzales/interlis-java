package guru.interlis.format;

import java.util.Objects;

/**
 * Formatting preferences; no IO or environment state is part of this value.
 *
 * @param indentWidth spaces per indentation level
 * @param lineEnding requested line ending policy
 * @param finalNewline whether the formatted text ends with a line ending
 * @param maxLineWidth soft limit in Unicode code points, including indentation (40 to 240)
 */
public record FormatOptions(int indentWidth, LineEnding lineEnding, boolean finalNewline,
                            int maxLineWidth) {
    /** Supported line-ending policies. */
    public enum LineEnding {
        /** Preserve the original line ending per line. */
        PRESERVE,
        /** Use LF outside value-bearing multiline literals and metadata. */
        LF,
        /** Use CRLF outside value-bearing multiline literals and metadata. */
        CRLF
    }

    /** Default soft line width in Unicode code points. */
    public static final int DEFAULT_MAX_LINE_WIDTH = 120;

    /** Uses the default soft line width with the supplied indentation and line ending policy. */
    public FormatOptions(int indentWidth, LineEnding lineEnding, boolean finalNewline) {
        this(indentWidth, lineEnding, finalNewline, DEFAULT_MAX_LINE_WIDTH);
    }

    /** Creates a validated instance. */
    public FormatOptions {
        if (indentWidth < 0 || indentWidth > 16) {
            throw new IllegalArgumentException("indentWidth must be between 0 and 16");
        }
        if (maxLineWidth < 40 || maxLineWidth > 240) {
            throw new IllegalArgumentException("maxLineWidth must be between 40 and 240");
        }
        Objects.requireNonNull(lineEnding, "lineEnding");
    }

    /**
     * Returns the default options.
     *
     * @return two spaces, preserved line endings, final newline, soft width of 120 code points
     */
    public static FormatOptions defaults() {
        return new FormatOptions(2, LineEnding.PRESERVE, true);
    }
}
