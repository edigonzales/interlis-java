package guru.interlis.gui.presentation;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SourcePreviewMappingTest {
    private final SourceId id = new SourceId("memory:/mapping.ili");
    private final SourceRevision revision = new SourceRevision(7);

    @Test
    void preservesTheOriginalAndMapsCrLfCrUmlautAndSurrogateOffsets() {
        var source = new SourceText(id, revision, "one\r\nä🦊z\rtwo\n");
        int start = source.text().indexOf("🦊");
        var original = source.range(start, start + 2);
        var preview = SourcePreviewModel.from(source, Optional.of(original)).orElseThrow();
        var highlight = preview.highlight().orElseThrow();
        assertEquals(source.text(), preview.originalText());
        assertEquals("one\nä🦊z\ntwo\n", preview.displayText());
        assertEquals(start - 1, highlight.start());
        assertEquals("🦊", preview.displayText().substring(highlight.start(), highlight.end()));
        assertEquals(original, highlight.original());
        assertEquals(1, preview.firstLine());
    }

    @Test
    void marksEmptyEofAndRejectsStaleOrForeignRanges() {
        var source = new SourceText(id, revision, "ä\r\n");
        var end = source.range(source.length(), source.length());
        var preview = SourcePreviewModel.from(source, Optional.of(end)).orElseThrow();
        var highlight = preview.highlight().orElseThrow();
        assertEquals(preview.displayText().length(), highlight.start());
        assertEquals(highlight.start(), highlight.end());
        assertTrue(SourcePreviewModel.from(source, Optional.empty()).orElseThrow().highlight().isEmpty());
        assertTrue(SourcePreviewModel.from(source, Optional.of(
                new SourceRange(id, new SourceRevision(8), 0, 1))).isEmpty());
        assertTrue(SourcePreviewModel.from(source, Optional.of(
                new SourceRange(new SourceId("memory:/other.ili"), revision, 0, 1))).isEmpty());
        assertTrue(SourcePreviewModel.from(source, Optional.of(new SourceRange(id, revision, 0, 4))).isEmpty());
    }

    @Test
    void aBoundedExcerptRetainsAbsoluteCoordinatesEvenWithinOneLongLine() {
        var source = new SourceText(id, revision, "head\r\n" + "ä".repeat(26_000) + "🦊" + "tail".repeat(8000));
        int start = source.text().indexOf("🦊");
        var preview = SourcePreviewModel.from(source, Optional.of(source.range(start, start + 2))).orElseThrow();
        assertTrue(preview.clipped());
        assertEquals(2, preview.firstLine());
        assertTrue(preview.originalText().length() < 19_000);
        var mark = preview.highlight().orElseThrow();
        assertEquals(start, mark.original().startOffset());
        assertEquals("🦊", preview.displayText().substring(mark.start(), mark.end()));
    }
}
