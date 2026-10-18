package guru.interlis.source;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable line index for exactly one source revision.
 *
 * <p>CRLF is one line separator with two UTF-16 code units; LF and CR are
 * supported as well. A trailing line separator creates a final empty line.
 * Offsets inside a separator belong to the preceding line; {@link #offsetAt}
 * deliberately rejects such positions.</p>
 */
public final class LineMap {
    private final SourceId source;
    private final SourceRevision revision;
    private final String text;
    private final int[] lineStarts;

    private LineMap(SourceId source, SourceRevision revision, String text) {
        this.source = source;
        this.revision = revision;
        this.text = text;
        int[] starts = new int[Math.max(1, text.length() / 16 + 2)];
        int count = 0;
        starts[count++] = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (count == starts.length) {
                    starts = Arrays.copyOf(starts, count * 2);
                }
                starts[count++] = i + 1;
            } else if (c == '\n') {
                if (count == starts.length) {
                    starts = Arrays.copyOf(starts, count * 2);
                }
                starts[count++] = i + 1;
            }
        }
        this.lineStarts = Arrays.copyOf(starts, count);
    }

    /** Builds an immutable line index for one exact source revision. */
    public static LineMap of(SourceId source, SourceRevision revision, String text) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(text, "text");
        return new LineMap(source, revision, text);
    }

    /** Returns the source identity. */
    public SourceId source() {
        return source;
    }

    /** Returns the source revision. */
    public SourceRevision revision() {
        return revision;
    }

    /** Returns the number of lines, including a trailing empty line. */
    public int lineCount() {
        return lineStarts.length;
    }

    /** Maps a UTF-16 offset inside a line to its zero-based position. */
    public SourcePosition positionAt(int offset) {
        if (offset < 0 || offset > text.length()) {
            throw new IllegalArgumentException("offset outside source: " + offset);
        }
        int idx = Arrays.binarySearch(lineStarts, offset);
        if (idx < 0) {
            idx = -idx - 2;
        }
        if (idx < 0) {
            idx = 0;
        }
        return new SourcePosition(idx, offset - lineStarts[idx]);
    }

    /** Maps a line/column position to a UTF-16 offset; separator positions are rejected. */
    public int offsetAt(SourcePosition position) {
        Objects.requireNonNull(position, "position");
        if (position.line() >= lineStarts.length) {
            throw new IllegalArgumentException("line outside source: " + position.line());
        }
        int start = lineStarts[position.line()];
        int end = lineContentEnd(position.line());
        int offset = start + position.character();
        if (offset > end) {
            throw new IllegalArgumentException("character outside line content: " + position);
        }
        return offset;
    }

    /** Returns the inclusive UTF-16 offset of a line start. */
    public int lineStart(int line) {
        if (line < 0 || line >= lineStarts.length) {
            throw new IllegalArgumentException("line outside source: " + line);
        }
        return lineStarts[line];
    }

    /** Returns the exclusive content end offset before the line separator. */
    public int lineContentEnd(int line) {
        if (line < 0 || line >= lineStarts.length) {
            throw new IllegalArgumentException("line outside source: " + line);
        }
        int nextStart = line + 1 < lineStarts.length ? lineStarts[line + 1] : text.length();
        int end = nextStart;
        if (end > lineStarts[line] && end <= text.length()) {
            char last = text.charAt(end - 1);
            if (last == '\n') {
                end--;
                if (end > lineStarts[line] && text.charAt(end - 1) == '\r') {
                    end--;
                }
            } else if (last == '\r') {
                end--;
            }
        }
        return end;
    }

    /** Returns the full line range including its separator. */
    public SourceRange lineRange(int line) {
        return new SourceRange(source, revision, lineStart(line), lineContentEnd(line));
    }
}
