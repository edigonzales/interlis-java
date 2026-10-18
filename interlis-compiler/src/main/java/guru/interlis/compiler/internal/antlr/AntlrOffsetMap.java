package guru.interlis.compiler.internal.antlr;

import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;
import org.antlr.v4.runtime.Token;

import java.util.Objects;

/** Converts ANTLR code-point indices to internal UTF-16 offsets. */
public final class AntlrOffsetMap {
    private final SourceText source;
    private final int[] codePointToUtf16;

    public AntlrOffsetMap(SourceText source) {
        this.source = Objects.requireNonNull(source, "source");
        String text = source.text();
        int codePoints = text.codePointCount(0, text.length());
        int[] map = new int[codePoints + 1];
        int cp = 0;
        int utf16 = 0;
        while (utf16 < text.length()) {
            map[cp++] = utf16;
            int current = text.codePointAt(utf16);
            utf16 += Character.charCount(current);
        }
        map[cp] = text.length();
        this.codePointToUtf16 = map;
    }

    public SourceText source() {
        return source;
    }

    public int codePointCount() {
        return codePointToUtf16.length - 1;
    }

    public int utf16Offset(int codePointBoundary) {
        if (codePointBoundary < 0 || codePointBoundary >= codePointToUtf16.length) {
            throw new IllegalArgumentException("code-point boundary outside source: " + codePointBoundary);
        }
        return codePointToUtf16[codePointBoundary];
    }

    public SourceRange tokenRange(Token token) {
        Objects.requireNonNull(token, "token");
        if (token.getType() == Token.EOF) {
            return eofRange();
        }
        int start = token.getStartIndex();
        int stopInclusive = token.getStopIndex();
        if (start < 0 || stopInclusive < start) {
            return eofRange();
        }
        return range(start, stopInclusive);
    }

    public SourceRange range(int startCodePointInclusive, int stopCodePointInclusive) {
        int start = utf16Offset(startCodePointInclusive);
        int end = utf16Offset(stopCodePointInclusive + 1);
        return source.range(start, end);
    }

    public SourceRange eofRange() {
        return source.range(source.length(), source.length());
    }

    /** Converts a one-based line and code-point column to a UTF-16 offset. */
    public int offsetAtLineColumn(int line, int column) {
        if (line < 1 || column < 0) {
            return 0;
        }
        try {
            int lineIndex = Math.min(line - 1, source.lineMap().lineCount() - 1);
            int start = source.lineMap().lineStart(lineIndex);
            int end = source.lineMap().lineContentEnd(lineIndex);
            int offset = start;
            int remaining = column;
            while (remaining > 0 && offset < end) {
                int cp = source.text().codePointAt(offset);
                offset += Character.charCount(cp);
                remaining--;
            }
            return Math.min(offset, source.length());
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
