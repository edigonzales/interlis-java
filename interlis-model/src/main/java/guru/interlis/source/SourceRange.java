package guru.interlis.source;

import java.util.Objects;

/**
 * Half-open UTF-16 range {@code [startOffset, endOffset)} in a specific source
 * revision. An empty range is legal and denotes an insertion point or a
 * missing token.
 *
 * @param source source identity
 * @param revision source revision the offsets belong to
 * @param startOffset inclusive UTF-16 start offset
 * @param endOffset exclusive UTF-16 end offset, not smaller than {@code startOffset}
 */
public record SourceRange(SourceId source, SourceRevision revision,
                          int startOffset, int endOffset) {
    /** Creates a validated instance. */
    public SourceRange {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(revision, "revision");
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("invalid source range");
        }
    }

    /**
     * Executes {@code length}.
     *
     * @return the result
     */
    public int length() {
        return endOffset - startOffset;
    }

    /**
     * Executes {@code isEmpty}.
     *
     * @return the result
     */
    public boolean isEmpty() {
        return startOffset == endOffset;
    }

    /**
     * Executes {@code contains}.
     *
     * @param offset the {@code offset} argument
     *
     * @return the result
     */
    public boolean contains(int offset) {
        return offset >= startOffset && offset < endOffset;
    }

    /**
     * Executes {@code withSource}.
     *
     * @param newSource the {@code newSource} argument
     * @param newRevision the {@code newRevision} argument
     *
     * @return the result
     */
    public SourceRange withSource(SourceId newSource, SourceRevision newRevision) {
        return new SourceRange(newSource, newRevision, startOffset, endOffset);
    }
}
