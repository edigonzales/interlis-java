package guru.interlis.cli;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.source.SourceText;

import java.util.Map;

/** Readable rendering derived from structured diagnostics. */
public final class ConsoleDiagnosticRenderer {
    /**
     * Renders structured diagnostics.
     *
     * @param diagnostic diagnostic to render
     * @param sources current source contents
     * @return readable multi-line text
     */
    public String render(Diagnostic diagnostic, Map<guru.interlis.source.SourceId, SourceText> sources) {
        StringBuilder out = new StringBuilder();
        diagnostic.primaryRange().ifPresent(range -> {
            SourceText source = sources.get(range.source());
            if (source != null) {
                var position = source.lineMap().positionAt(range.startOffset());
                out.append(source.id().value()).append(':')
                        .append(position.line() + 1).append(':')
                        .append(position.character() + 1).append(": ");
            } else {
                out.append(range.source().value()).append(": ");
            }
        });
        out.append(diagnostic.severity()).append(' ').append(diagnostic.code()).append(' ')
                .append(diagnostic.messageKey());
        if (!diagnostic.arguments().isEmpty()) {
            out.append(" (");
            for (int i = 0; i < diagnostic.arguments().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                DiagnosticArgument argument = diagnostic.arguments().get(i);
                out.append(argument.name()).append('=').append(value(argument));
            }
            out.append(')');
        }
        return out.toString();
    }

    private static String value(DiagnosticArgument argument) {
        return switch (argument) {
            case DiagnosticArgument.TextArgument a -> a.value();
            case DiagnosticArgument.IdentifierArgument a -> a.value();
            case DiagnosticArgument.TokenArgument a -> a.value();
            case DiagnosticArgument.IntegerArgument a -> a.value().toString();
            case DiagnosticArgument.DecimalArgument a -> a.value().toPlainString();
            case DiagnosticArgument.TypeNameArgument a -> a.value();
            case DiagnosticArgument.IdentifierListArgument a -> String.join(",", a.values());
        };
    }
}
