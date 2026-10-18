package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * One original name segment with its own optional binding.
 *
 * @param text the {@code text} component
 * @param range source range
 * @param target target symbol
 */
public record ReferenceSegment(String text, SourceRange range,
                               Optional<SymbolId> target) {
    /** Creates a validated instance. */
    public ReferenceSegment {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(range, "range");
        target = Objects.requireNonNull(target, "target");
    }
}
