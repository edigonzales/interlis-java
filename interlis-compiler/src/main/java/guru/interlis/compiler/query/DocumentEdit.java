package guru.interlis.compiler.query;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.TextEdit;

import java.util.List;
import java.util.Objects;

/**
 * Edits for one source revision.
 *
 * @param source source identifier
 * @param expected the {@code expected} component
 * @param edits the {@code edits} component
 */
public record DocumentEdit(SourceId source, SourceRevision expected,
                           List<TextEdit> edits) {
    /** Creates a validated instance. */
    public DocumentEdit {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expected, "expected");
        edits = List.copyOf(Objects.requireNonNull(edits, "edits"));
    }
}
