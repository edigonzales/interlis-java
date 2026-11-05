package guru.interlis.gui.presentation;

import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;

import java.util.Objects;
import java.util.Optional;

/** Bounded display of an exact source revision with original and display UTF-16 offsets. */
public record SourcePreviewModel(SourceText source, int startOffset, int endOffset, int firstLine,
                                 String displayText, Optional<Highlight> highlight, boolean clipped) {
    private static final int CONTEXT = 6000;
    private static final int MAX_UNITS = 18_000;

    public record Highlight(int start, int end, SourceRange original, boolean clipped) {
    }

    public String originalText() {
        return source.text().substring(startOffset, endOffset);
    }

    /** An absent or mismatched source revision never produces a highlight. */
    public static Optional<SourcePreviewModel> from(SourceText source, Optional<SourceRange> selected) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(selected);
        if (selected.filter(range -> !range.source().equals(source.id()) || !range.revision().equals(source.revision())
                || range.endOffset() > source.length()).isPresent()) {
            return Optional.empty();
        }
        var lines = source.lineMap();
        int start = selected.map(range -> Math.max(0, range.startOffset() - CONTEXT)).orElse(0);
        int firstLine = lines.positionAt(start).line();
        int lineStart = lines.lineStart(firstLine);
        if (start - lineStart < CONTEXT) {
            start = lineStart;
        }
        int end = Math.min(source.length(), start + MAX_UNITS);
        if (end < source.length() && Character.isLowSurrogate(source.text().charAt(end))) {
            end++;
        }
        if (start > 0 && start < source.length() && Character.isLowSurrogate(source.text().charAt(start))) {
            start--;
        }
        if (start > 0 && start < source.length() && source.text().charAt(start) == '\n' && source.text().charAt(start - 1) == '\r') {
            start--;
        }
        if (end > 0 && end < source.length() && source.text().charAt(end) == '\n' && source.text().charAt(end - 1) == '\r') {
            end++;
        }
        String original = source.text().substring(start, end);
        var display = new StringBuilder(original.length());
        int[] offsets = new int[original.length() + 1];
        for (int raw = 0; raw < original.length(); raw++) {
            offsets[raw] = display.length();
            char ch = original.charAt(raw);
            if (ch == '\r') {
                if (raw + 1 < original.length() && original.charAt(raw + 1) == '\n') {
                    offsets[++raw] = display.length();
                }
                display.append('\n');
            } else {
                display.append(ch);
            }
        }
        offsets[original.length()] = display.length();
        Optional<Highlight> highlight = Optional.empty();
        if (selected.isPresent()) {
            SourceRange range = selected.orElseThrow();
            highlight = Optional.of(new Highlight(offsets[range.startOffset() - start],
                    offsets[Math.min(range.endOffset(), end) - start], range, range.endOffset() > end));
        }
        return Optional.of(new SourcePreviewModel(source, start, end, lines.positionAt(start).line() + 1,
                display.toString(), highlight, start != 0 || end != source.length()));
    }
}
