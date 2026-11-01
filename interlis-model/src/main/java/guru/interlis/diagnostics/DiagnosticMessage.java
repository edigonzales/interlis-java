package guru.interlis.diagnostics;

import java.util.Objects;
import java.util.Optional;

/** Immutable, null-free presentation, separate from canonical diagnostic identity and arguments. */
public record DiagnosticMessage(String text, Optional<String> hint) {
    /** Both fields are required; an absent hint is Optional.empty(), not null. */
    public DiagnosticMessage {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hint, "hint");
    }
}
