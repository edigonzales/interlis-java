package guru.interlis.diagnostics;

import guru.interlis.source.Hex;
import guru.interlis.source.Sha256;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** Deterministic public diagnostic identity. */
public final class DiagnosticIds {
    private DiagnosticIds() {
    }

    public static DiagnosticId create(DiagnosticCode code, Phase phase,
                                      Optional<guru.interlis.source.SourceRange> range,
                                      String messageKey, List<DiagnosticArgument> arguments) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, code.value());
        append(canonical, phase.name());
        if (range.isPresent()) {
            var r = range.get();
            append(canonical, r.source().value());
            append(canonical, Long.toString(r.revision().value()));
            append(canonical, Integer.toString(r.startOffset()));
            append(canonical, Integer.toString(r.endOffset()));
        } else {
            append(canonical, "<global>");
        }
        append(canonical, messageKey);
        for (DiagnosticArgument argument : arguments) {
            append(canonical, argument.name());
            append(canonical, argument.getClass().getSimpleName());
            append(canonical, stringValue(argument));
        }
        byte[] digest = Sha256.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
        return new DiagnosticId("diag-" + Hex.encode(digest, 12));
    }

    private static String stringValue(DiagnosticArgument argument) {
        return switch (argument) {
            case DiagnosticArgument.TextArgument a -> a.value();
            case DiagnosticArgument.IdentifierArgument a -> a.value();
            case DiagnosticArgument.TokenArgument a -> a.value();
            case DiagnosticArgument.IntegerArgument a -> a.value().toString();
            case DiagnosticArgument.DecimalArgument a -> a.value().toPlainString();
            case DiagnosticArgument.TypeNameArgument a -> a.value();
            case DiagnosticArgument.IdentifierListArgument a -> String.join("\u0000", a.values());
        };
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append(';');
    }
}
