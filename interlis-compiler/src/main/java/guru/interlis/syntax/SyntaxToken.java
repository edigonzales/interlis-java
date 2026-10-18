package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * Own token value; inserted tokens never participate in reconstruction.
 *
 * @param id stable identifier
 * @param kind kind discriminator
 * @param range source range
 * @param channel the {@code channel} component
 * @param origin origin information
 * @param text the {@code text} component
 */
public record SyntaxToken(SyntaxNodeId id, TokenKind kind, SourceRange range, int channel,
                          TokenOrigin origin, String text) {
    /** Creates a validated instance. */
    public SyntaxToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(range, "range");
        if (channel < 0) {
            throw new IllegalArgumentException("channel must be non-negative");
        }
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(text, "text");
    }
}
