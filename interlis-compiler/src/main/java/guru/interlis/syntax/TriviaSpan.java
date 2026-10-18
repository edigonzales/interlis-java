package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * One trivia span in original source order.
 *
 * @param kind kind discriminator
 * @param range source range
 * @param text the {@code text} component
 */
public record TriviaSpan(TriviaKind kind, SourceRange range, String text) {
    /** Creates a validated instance. */
    public TriviaSpan {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(text, "text");
    }
}
