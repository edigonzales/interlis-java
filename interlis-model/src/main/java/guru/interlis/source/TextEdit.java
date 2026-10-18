package guru.interlis.source;

import java.util.Objects;

/**
 * Replacement of a half-open UTF-16 range in a documented base text.
 * All edits in one core request refer to the same source revision.
 *
 * @param range half-open range in the base revision
 * @param replacement exact replacement text, never {@code null}
 */
public record TextEdit(SourceRange range, String replacement) {
    /** Creates a validated instance. */
    public TextEdit {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(replacement, "replacement");
    }
}
