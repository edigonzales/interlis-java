package guru.interlis.diagnostics;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/** Additional source location that explains a diagnostic. */
public record RelatedLocation(SourceRange range, String messageKey,
                              List<DiagnosticArgument> arguments) {
    /** Creates a validated instance. */
    public RelatedLocation {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }

    /**
     * Executes {@code of}.
     *
     * @param range the {@code range} argument
     * @param messageKey the {@code messageKey} argument
     *
     * @return the result
     */
    public static RelatedLocation of(SourceRange range, String messageKey) {
        return new RelatedLocation(range, messageKey, List.of());
    }
}
