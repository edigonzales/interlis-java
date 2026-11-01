package guru.interlis.cli;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticMessageFormatter;
import guru.interlis.source.*;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded pure source projection. Never loads a source or guesses a revision. */
final class DiagnosticSnippetRenderer {
    /**
     * Returns no frame for absent, stale or invalid source ranges. Only bounded
     * line windows are allocated. Tabs use four-column stops; controls are escaped
     * before styling. Printable Unicode is retained, but uncertain display widths
     * before/within the marked span suppress the caret explicitly, retaining the
     * canonical UTF-16 position. No terminal-specific width guesses are made.
     */
    Optional<String> renderPrimary(Diagnostic diagnostic, Map<SourceId, SourceText> sources,
                                   RenderOptions options, AnsiStyler styler) {
        Objects.requireNonNull(diagnostic); Objects.requireNonNull(sources);
        Objects.requireNonNull(options); Objects.requireNonNull(styler);
        if (diagnostic.primaryRange().isEmpty()) return Optional.empty();
        var range = diagnostic.primaryRange().orElseThrow();
        var source = sources.get(range.source());
        if (source == null || !source.id().equals(range.source())
                || !source.revision().equals(range.revision()) || range.endOffset() > source.length())
            return Optional.empty();
        var map = source.lineMap();
        int primary = map.positionAt(range.startOffset()).line();
        int first = Math.max(0, primary - options.contextLinesBefore());
        int last = Math.min(map.lineCount() - 1, primary + options.contextLinesAfter());
        int digits = Integer.toString(last + 1).length();
        var out = new StringBuilder();
        var role = AnsiStyler.Role.valueOf(diagnostic.severity().name());
        for (int line = first; line <= last; line++) {
            if (!out.isEmpty()) out.append('\n');
            String number = " ".repeat(digits - Integer.toString(line + 1).length()) + (line + 1);
            String gutter = "  " + number + " | ";
            int budget = options.maxVisibleColumns() - gutter.length();
            int start = map.lineStart(line), end = map.lineContentEnd(line);
            int focus = line == primary ? Math.min(range.startOffset(), end) : start;
            // A code unit may expand to six printable escape columns. Retain a
            // bounded prefix even for hostile runs of controls before the span.
            int window = Math.max(start, focus - budget / 12);
            if (window > start && window < end && Character.isLowSurrogate(source.text().charAt(window))
                    && Character.isHighSurrogate(source.text().charAt(window - 1))) window--;
            var rendered = window(source.text(), start, end, window, budget, range);
            out.append("  ").append(styler.style(AnsiStyler.Role.CODE, number, options.ansiEnabled()))
                    .append(" | ").append(rendered.text());
            if (line == primary) {
                String markerGutter = " ".repeat(digits + 3) + "| ";
                out.append('\n').append(markerGutter);
                if (rendered.markerStart() >= 0 && !rendered.uncertain() && range.startOffset() <= end) {
                    int length = Math.max(1, rendered.markerEnd() - rendered.markerStart());
                    out.append(" ".repeat(rendered.markerStart())).append(styler.style(role,
                            "^" + "~".repeat(length - 1), options.ansiEnabled()));
                } else {
                    String fallback = "[UTF-16 " + (range.startOffset() - start + 1) + "; marker omitted]";
                    out.append(fallback, 0, Math.min(fallback.length(), budget));
                }
                if (range.endOffset() > end) {
                    String continuation = "[span continues]";
                    out.append('\n').append(markerGutter).append(continuation, 0, Math.min(continuation.length(), budget));
                }
            }
        }
        return Optional.of(out.toString());
    }

    private record Window(String text, int markerStart, int markerEnd, boolean uncertain) { }

    private static Window window(String text, int lineStart, int lineEnd, int windowStart,
                                 int budget, SourceRange range) {
        var out = new StringBuilder(budget);
        boolean uncertain = false;
        int column = 0, shown = 0, markerStart = -1, markerEnd = -1;
        if (windowStart > lineStart) { out.append("..."); shown = 3; }
        int offset = lineStart;
        while (offset < lineEnd) {
            int cp = text.codePointAt(offset), count = Character.charCount(cp);
            String raw = new String(Character.toChars(cp));
            String safe = cp == '\t' ? " ".repeat(4 - column % 4)
                    : cp >= 0xd800 && cp <= 0xdfff ? String.format(java.util.Locale.ROOT, "\\u%04x", cp)
                    : DiagnosticMessageFormatter.safeText(raw);
            // Retain printable Unicode; bound its maximum width conservatively.
            boolean ambiguous = cp > 126 && safe.equals(raw);
            int width = ambiguous ? 2 : safe.length();
            if (offset >= windowStart) {
                // Reserve room for ellipsis or an EOL insertion marker.
                if (shown + width > budget - 3) break;
                if (offset == range.startOffset()) markerStart = shown;
                if (offset < range.endOffset()) markerEnd = shown + width;
                out.append(safe); shown += width;
            }
            if (offset < range.endOffset() || offset < range.startOffset()) uncertain |= ambiguous;
            if ((range.startOffset() > offset && range.startOffset() < offset + count)
                    || (range.endOffset() > offset && range.endOffset() < offset + count)) uncertain = true;
            column = (column + width) % 4;
            offset += count;
        }
        if (offset < lineEnd) out.append("...");
        if (range.startOffset() == lineEnd && offset == lineEnd) { markerStart = shown; markerEnd = shown; }
        if (range.isEmpty() && markerStart >= 0) markerEnd = markerStart;
        if (markerEnd < markerStart) markerEnd = markerStart;
        return new Window(out.toString(), markerStart, markerEnd, uncertain);
    }
}
