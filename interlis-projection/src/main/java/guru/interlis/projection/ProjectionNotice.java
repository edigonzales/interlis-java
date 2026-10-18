package guru.interlis.projection;

import guru.interlis.diagnostics.Severity;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Consumer-side finding; never changes language validity.
 *
 * @param code the {@code code} component
 * @param severity the {@code severity} component
 * @param messageKey the {@code messageKey} component
 * @param arguments the {@code arguments} component
 * @param symbol the {@code symbol} component
 * @param range source range
 */
public record ProjectionNotice(ProjectionNoticeCode code, Severity severity,
                               String messageKey, List<String> arguments,
                               Optional<SymbolRef> symbol, Optional<SourceRange> range) {
    /** Creates a validated instance. */
    public ProjectionNotice {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        symbol = Objects.requireNonNull(symbol, "symbol");
        range = Objects.requireNonNull(range, "range");
    }
}
