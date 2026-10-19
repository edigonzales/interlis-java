package guru.interlis.format;

import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * Non-fatal formatting notice; it is not a language diagnostic.
 *
 * @param code stable notice code
 * @param message safe user-facing message
 * @param range optional source range
 */
public record FormattingNotice(String code, String message, Optional<SourceRange> range) {
    /** Creates a validated instance. */
    public FormattingNotice {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        range = Objects.requireNonNull(range, "range");
    }
}
