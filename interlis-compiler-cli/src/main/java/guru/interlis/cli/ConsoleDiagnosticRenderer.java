package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;

import java.util.Map;
import java.util.Objects;

/** Pure presentation from canonical data and explicitly supplied source revisions; never loads files. */
public final class ConsoleDiagnosticRenderer {
    /** English convenience overload, independent of the process locale. */
    public String render(Diagnostic diagnostic, Map<SourceId, SourceText> sources) {
        return render(diagnostic, sources, DiagnosticLanguage.EN);
    }

    /**
     * Renders primary/related locations and safe text. Sources remain owned by the
     * caller. Only matching revisions produce 1-based UTF-16 line/column positions;
     * missing/stale source text produces explicit revision/offset information.
     */
    public String render(Diagnostic diagnostic, Map<SourceId, SourceText> sources,
                         DiagnosticLanguage language) {
        return render(diagnostic, sources, language, false);
    }

    /** Explicit pure style projection; legacy overloads remain plain. */
    public String render(Diagnostic diagnostic, Map<SourceId, SourceText> sources,
                         DiagnosticLanguage language, boolean ansiEnabled) {
        return render(diagnostic, sources, language, RenderOptions.defaults(ansiEnabled));
    }

    /** Renders safe diagnostic fields; verbose options add a bounded frame, related locations and hints. */
    public String render(Diagnostic diagnostic, Map<SourceId, SourceText> sources,
                         DiagnosticLanguage language, RenderOptions options) {
        Objects.requireNonNull(options, "options");
        boolean ansiEnabled = options.ansiEnabled();
        var styler = new AnsiStyler();
        Objects.requireNonNull(diagnostic, "diagnostic"); Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(language, "language");
        var formatter = new DiagnosticMessageFormatter();
        var message = formatter.format(diagnostic, language);
        var out = new StringBuilder();
        out.append(severityPrefix(diagnostic.severity(), ansiEnabled));
        diagnostic.primaryRange().ifPresent(range -> out.append(styler.style(AnsiStyler.Role.LOCATION, location(range, sources), ansiEnabled)).append(": "));
        out.append(message.text()).append(" [")
                .append(styler.style(AnsiStyler.Role.CODE, diagnostic.code().value(), ansiEnabled)).append(']');
        if (!options.verbose()) return out.toString();
        new DiagnosticSnippetRenderer().renderPrimary(diagnostic, sources, options, styler)
                .ifPresent(frame -> out.append('\n').append(frame));
        for (var related : diagnostic.relatedLocations()) {
            out.append('\n').append("  ").append(language == DiagnosticLanguage.DE ? "Verwandte Stelle: " : "Related location: ")
                    .append(styler.style(AnsiStyler.Role.LOCATION, location(related.range(), sources), ansiEnabled)).append(": ")
                    .append(formatter.formatRelated(related, language));
        }
        message.hint().ifPresent(hint -> out.append('\n').append("  ")
                .append(styler.style(AnsiStyler.Role.HINT, language == DiagnosticLanguage.DE ? "Hinweis: " : "Hint: ", ansiEnabled)).append(hint));
        return out.toString();
    }

    static String renderLog(Severity severity, String message, boolean ansiEnabled) {
        return severityPrefix(severity, ansiEnabled) + DiagnosticMessageFormatter.safeText(message);
    }

    private static String severityPrefix(Severity severity, boolean ansiEnabled) {
        return new AnsiStyler().style(AnsiStyler.Role.valueOf(severity.name()), severity.name(), ansiEnabled) + " ";
    }

    static String delimitedLocation(String value) {
        return "<" + DiagnosticMessageFormatter.safeText(value).replace("<", "\\u003c").replace(">", "\\u003e") + ">";
    }

    private static String location(SourceRange range, Map<SourceId, SourceText> sources) {
        String id = delimitedLocation(range.source().value());
        var source = sources.get(range.source());
        if (source != null && source.id().equals(range.source()) && source.revision().equals(range.revision())
                && range.startOffset() <= source.length() && range.endOffset() <= source.length()) {
            var position = source.lineMap().positionAt(range.startOffset());
            return id + ":" + (position.line() + 1) + ":" + (position.character() + 1);
        }
        return id + "@" + range.revision().value() + " (UTF-16 "
                + range.startOffset() + ".." + range.endOffset() + ")";
    }

}
