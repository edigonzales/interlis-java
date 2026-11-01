package guru.interlis.cli;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.DiagnosticMessageFormatter;

import java.util.List;

/** Small deterministic JSON diagnostic array; exact numbers stay strings. */
public final class JsonDiagnosticWriter {
    /**
     * Executes {@code write}.
     *
     * @param diagnostics the {@code diagnostics} argument
     *
     * @return the result
     */
    public String write(List<Diagnostic> diagnostics) {
        return write(diagnostics, DiagnosticLanguage.EN);
    }

    /** Adds presentation only; canonical fields/arguments/numbers retain their original encoding. */
    public String write(List<Diagnostic> diagnostics, DiagnosticLanguage language) {
        java.util.Objects.requireNonNull(language, "language");
        var formatter = new DiagnosticMessageFormatter();
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < diagnostics.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            Diagnostic diagnostic = diagnostics.get(i);
            out.append('{');
            field(out, "id", diagnostic.id().value()).append(',');
            field(out, "code", diagnostic.code().value()).append(',');
            field(out, "severity", diagnostic.severity().name()).append(',');
            field(out, "phase", diagnostic.phase().name()).append(',');
            field(out, "messageKey", diagnostic.messageKey());
            var message = formatter.format(diagnostic, language);
            out.append(','); field(out, "message", message.text());
            message.hint().ifPresent(hint -> { out.append(','); field(out, "hint", hint); });
            diagnostic.primaryRange().ifPresent(range -> {
                out.append(",\"range\":{");
                field(out, "source", range.source().value()).append(',');
                field(out, "revision", Long.toString(range.revision().value())).append(',');
                out.append("\"startOffset\":").append(range.startOffset()).append(',');
                out.append("\"endOffset\":").append(range.endOffset()).append('}');
            });
            out.append(",\"arguments\":[");
            for (int j = 0; j < diagnostic.arguments().size(); j++) {
                if (j > 0) {
                    out.append(',');
                }
                out.append('{');
                DiagnosticArgument argument = diagnostic.arguments().get(j);
                field(out, "name", argument.name()).append(',');
                field(out, "kind", kind(argument)).append(',');
                field(out, "value", value(argument));
                out.append('}');
            }
            out.append(']');
            diagnostic.causeId().ifPresent(id -> { out.append(','); field(out, "causeId", id.value()); });
            if (!diagnostic.dependsOn().isEmpty()) {
                out.append(",\"dependsOn\":[");
                for (int j = 0; j < diagnostic.dependsOn().size(); j++) {
                    if (j > 0) out.append(',');
                    out.append('"').append(escape(diagnostic.dependsOn().get(j).value())).append('"');
                }
                out.append(']');
            }
            if (!diagnostic.relatedLocations().isEmpty()) {
                out.append(",\"relatedLocations\":[");
                for (int j = 0; j < diagnostic.relatedLocations().size(); j++) {
                    if (j > 0) out.append(',');
                    var related = diagnostic.relatedLocations().get(j);
                    var range = related.range();
                    out.append("{\"range\":{");
                    field(out, "source", range.source().value()).append(',');
                    field(out, "revision", Long.toString(range.revision().value())).append(',');
                    out.append("\"startOffset\":").append(range.startOffset()).append(',');
                    out.append("\"endOffset\":").append(range.endOffset()).append("},");
                    field(out, "messageKey", related.messageKey()).append(',');
                    field(out, "message", formatter.formatRelated(related, language));
                    out.append(",\"arguments\":[");
                    for (int k = 0; k < related.arguments().size(); k++) {
                        if (k > 0) out.append(',');
                        var argument = related.arguments().get(k);
                        out.append('{'); field(out, "name", argument.name()).append(',');
                        field(out, "kind", kind(argument)).append(','); field(out, "value", value(argument)).append('}');
                    }
                    out.append("]}");
                }
                out.append(']');
            }
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String kind(DiagnosticArgument argument) {
        return switch (argument) {
            case DiagnosticArgument.TextArgument ignored -> "TEXT";
            case DiagnosticArgument.IdentifierArgument ignored -> "IDENTIFIER";
            case DiagnosticArgument.TokenArgument ignored -> "TOKEN";
            case DiagnosticArgument.IntegerArgument ignored -> "INTEGER";
            case DiagnosticArgument.DecimalArgument ignored -> "DECIMAL";
            case DiagnosticArgument.TypeNameArgument ignored -> "TYPE_NAME";
            case DiagnosticArgument.IdentifierListArgument ignored -> "IDENTIFIER_LIST";
        };
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

    private static StringBuilder field(StringBuilder out, String name, String value) {
        return out.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u00").append(Character.forDigit(character >>> 4, 16))
                                .append(Character.forDigit(character & 15, 16));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
