package guru.interlis.source;

import java.util.Objects;

/**
 * Immutable source text with its exact Java string, identity and revision.
 *
 * <p>Text is neither normalized nor re-encoded. The contract fingerprint uses
 * SHA-256 over the UTF-16 code units in big-endian order without a BOM, so
 * malformed surrogate sequences and platform differences remain stable.</p>
 */
public final class SourceText {
    private final SourceId id;
    private final SourceRevision revision;
    private final String text;
    private final LineMap lineMap;
    private final ContentFingerprint contentFingerprint;

    /**
     * Creates an immutable source text and its line map.
     *
     * @param id source identity
     * @param revision source revision
     * @param text exact Java string, not normalized
     */
    public SourceText(SourceId id, SourceRevision revision, String text) {
        this.id = Objects.requireNonNull(id, "id");
        this.revision = Objects.requireNonNull(revision, "revision");
        this.text = Objects.requireNonNull(text, "text");
        this.lineMap = LineMap.of(id, revision, text);
        this.contentFingerprint = computeFingerprint(text);
    }

    /** Returns the opaque source identity. */
    public SourceId id() {
        return id;
    }

    /** Returns the source revision. */
    public SourceRevision revision() {
        return revision;
    }

    /** Returns the exact source string. */
    public String text() {
        return text;
    }

    /** Returns the UTF-16 length of the source string. */
    public int length() {
        return text.length();
    }

    /** Returns the immutable line index for this text. */
    public LineMap lineMap() {
        return lineMap;
    }

    /**
     * Builds a range in this source revision.
     *
     * @throws IllegalArgumentException if the range is outside this text
     */
    public SourceRange range(int startOffset, int endOffset) {
        if (startOffset < 0 || endOffset < startOffset || endOffset > text.length()) {
            throw new IllegalArgumentException("range outside source revision");
        }
        return new SourceRange(id, revision, startOffset, endOffset);
    }

    /**
     * Returns the exact substring of a range in this source revision.
     *
     * @throws IllegalArgumentException for a foreign revision or invalid range
     */
    public String slice(SourceRange range) {
        Objects.requireNonNull(range, "range");
        if (!id.equals(range.source()) || !revision.equals(range.revision())) {
            throw new IllegalArgumentException("range belongs to another source revision");
        }
        if (range.startOffset() < 0 || range.endOffset() > text.length()) {
            throw new IllegalArgumentException("range outside source revision");
        }
        return text.substring(range.startOffset(), range.endOffset());
    }

    /** Returns the SHA-256 fingerprint over UTF-16BE code units. */
    public ContentFingerprint contentFingerprint() {
        return contentFingerprint;
    }

    private static ContentFingerprint computeFingerprint(String text) {
        return ContentFingerprint.sha256(Hex.encode(Sha256.digestUtf16(text)));
    }

    /** Compares identity, revision and exact text. */
    @Override
    public boolean equals(Object other) {
        return other instanceof SourceText s
                && id.equals(s.id) && revision.equals(s.revision) && text.equals(s.text);
    }

    /** Returns a hash consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(id, revision, text);
    }

    /** Returns a diagnostic representation without source content. */
    @Override
    public String toString() {
        return "SourceText[" + id.value() + "@" + revision.value() + "]";
    }
}
