package guru.interlis.compiler.query;

import guru.interlis.model.NamePrefix;
import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * Completion request at an explicit cursor.
 *
 * @param cursor the {@code cursor} component
 * @param prefix path prefix
 * @param limit the {@code limit} component
 */
public record CompletionQuery(SourceRange cursor, NamePrefix prefix, int limit) {
    /** Creates a validated instance. */
    public CompletionQuery {
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(prefix, "prefix");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
