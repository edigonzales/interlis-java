package guru.interlis.compiler.query;

import guru.interlis.model.ReferenceId;
import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * One original reference occurrence.
 *
 * @param range source range
 * @param reference the {@code reference} component
 */
public record ReferenceOccurrence(SourceRange range, ReferenceId reference) {
    /** Creates a validated instance. */
    public ReferenceOccurrence {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(reference, "reference");
    }
}
