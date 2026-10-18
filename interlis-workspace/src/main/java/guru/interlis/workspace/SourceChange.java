package guru.interlis.workspace;

import guru.interlis.source.TextEdit;

import java.util.List;
import java.util.Objects;

/** Complete replacement or atomic edits against one base revision. */
public sealed interface SourceChange permits SourceChange.Replace, SourceChange.ApplyEdits {
    /**
     * Replaces the complete source text.
     *
     * @param text exact new text
     */
    record Replace(String text) implements SourceChange {
        /** Creates a validated instance. */
        public Replace {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * Applies ordered edits to the declared base revision.
     *
     * @param edits edits in application order
     */
    record ApplyEdits(List<TextEdit> edits) implements SourceChange {
        /** Creates a validated instance. */
        public ApplyEdits {
            edits = List.copyOf(Objects.requireNonNull(edits, "edits"));
        }
    }
}
