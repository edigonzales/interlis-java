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
        Objects.requireNonNull(diagnostic, "diagnostic"); Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(language, "language");
        var formatter = new DiagnosticMessageFormatter();
        var message = formatter.format(diagnostic, language);
        var out = new StringBuilder();
        diagnostic.primaryRange().ifPresent(range -> out.append(location(range, sources)).append(": "));
        out.append(severity(diagnostic.severity(), language)).append(" [")
                .append(diagnostic.code().value()).append("] ").append(message.text());
        for (var related : diagnostic.relatedLocations()) {
            out.append('\n').append("  ").append(language == DiagnosticLanguage.DE ? "Verwandte Stelle: " : "Related location: ")
                    .append(location(related.range(), sources)).append(": ")
                    .append(formatter.formatRelated(related, language));
        }
        message.hint().ifPresent(hint -> out.append('\n').append("  ")
                .append(language == DiagnosticLanguage.DE ? "Hinweis: " : "Hint: ").append(hint));
        return out.toString();
    }

    private static String location(SourceRange range, Map<SourceId, SourceText> sources) {
        String id = DiagnosticMessageFormatter.safeText(range.source().value());
        var source = sources.get(range.source());
        if (source != null && source.id().equals(range.source()) && source.revision().equals(range.revision())
                && range.startOffset() <= source.length() && range.endOffset() <= source.length()) {
            var position = source.lineMap().positionAt(range.startOffset());
            return id + ":" + (position.line() + 1) + ":" + (position.character() + 1);
        }
        return id + "@" + range.revision().value() + " (UTF-16 "
                + range.startOffset() + ".." + range.endOffset() + ")";
    }

    private static String severity(Severity severity, DiagnosticLanguage language) {
        if (language == DiagnosticLanguage.EN) return severity.name();
        return switch (severity) {
            case ERROR -> "Fehler"; case WARNING -> "Warnung"; case INFO -> "Information"; case HINT -> "Hinweis";
        };
    }
}
