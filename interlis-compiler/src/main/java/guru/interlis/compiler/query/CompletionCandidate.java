package guru.interlis.compiler.query;

import java.util.Objects;

/**
 * One deterministic completion candidate.
 *
 * @param label the {@code label} component
 * @param insertText the {@code insertText} component
 * @param kind kind discriminator
 * @param detail the {@code detail} component
 */
public record CompletionCandidate(String label, String insertText, CompletionKind kind,
                                  String detail) {
    /** Creates a validated instance. */
    public CompletionCandidate {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(insertText, "insertText");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
    }
}
