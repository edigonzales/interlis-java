package guru.interlis.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceTextTest {
    @Test
    void crlfAndSupplementaryCharactersUseUtf16Offsets() {
        String text = "a😀b\r\nc\r\nd";
        SourceText source = new SourceText(new SourceId("memory:/u.ili"),
                new SourceRevision(4), text);
        assertEquals(3, source.lineMap().lineCount());
        SourcePosition emoji = source.lineMap().positionAt(1);
        assertEquals(0, emoji.line());
        assertEquals(1, emoji.character());
        SourcePosition afterEmoji = source.lineMap().positionAt(3);
        assertEquals(3, afterEmoji.character());
        assertEquals(6, source.lineMap().lineStart(1));
        assertEquals(9, source.lineMap().lineStart(2));
        assertEquals(4, source.lineMap().lineContentEnd(0));
        assertEquals(7, source.lineMap().lineContentEnd(1));
        assertEquals(10, source.lineMap().lineContentEnd(2));
        assertEquals(3, source.lineMap().offsetAt(new SourcePosition(0, 3)));
    }

    @Test
    void loneCrAndEmptyLinesUseUtf16HalfOpenRanges() {
        SourceText source = new SourceText(new SourceId("memory:/cr.ili"),
                new SourceRevision(1), "a\rb\r\rc");
        assertEquals(4, source.lineMap().lineCount());
        assertEquals(2, source.lineMap().lineStart(1));
        assertEquals(4, source.lineMap().lineStart(2));
        assertEquals(5, source.lineMap().lineStart(3));
        assertEquals(1, source.lineMap().lineContentEnd(0));
        assertEquals(3, source.lineMap().lineContentEnd(1));
        assertEquals(4, source.lineMap().lineContentEnd(2));
        assertEquals(6, source.lineMap().lineRange(3).endOffset());
        assertEquals("b", source.slice(source.range(2, 3)));
    }

    @Test
    void sliceRejectsForeignRevisionAndInvalidOffsets() {
        SourceText source = new SourceText(new SourceId("memory:/a.ili"),
                new SourceRevision(2), "abcdef");
        SourceRange current = source.range(1, 4);
        assertEquals("bcd", source.slice(current));
        SourceRange foreign = new SourceRange(source.id(), new SourceRevision(3), 1, 4);
        assertThrows(IllegalArgumentException.class, () -> source.slice(foreign));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceRange(source.id(), source.revision(), 4, 2));
        assertThrows(IllegalArgumentException.class, () -> source.range(0, 7));
    }

    @Test
    void contentFingerprintIsStableAndExact() {
        SourceText first = new SourceText(new SourceId("memory:/a.ili"),
                new SourceRevision(1), "Grüezi 😀\r\n");
        SourceText second = new SourceText(new SourceId("memory:/b.ili"),
                new SourceRevision(9), "Grüezi 😀\r\n");
        assertEquals(first.contentFingerprint(), second.contentFingerprint());
        assertNotEquals(first.contentFingerprint(), new SourceText(
                new SourceId("memory:/a.ili"), new SourceRevision(1), "Grüezi 😀\n")
                .contentFingerprint());
        assertEquals(64, first.contentFingerprint().value().length());
        assertTrue(first.contentFingerprint().algorithm().startsWith("SHA-256"));
    }
}
