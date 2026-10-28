package guru.interlis.model;

import java.util.Objects;
import java.util.List;
import guru.interlis.source.SourceRange;

/**
 * Source documentation text, empty when none was attached.
 *
 * @param text interpreted text
 * @param ranges original comment ranges, in source order; immutable and null-free
 */
public record Documentation(String text,List<SourceRange> ranges) {
    /** Creates a validated instance. */
    public Documentation {
        Objects.requireNonNull(text, "text");
        ranges=List.copyOf(ranges);
    }

    /**
     * Returns the {@code empty} value.
     *
     * @return the value
     */
    public static Documentation empty() {
        return new Documentation("",List.of());
    }

    /**
     * Returns the {@code isEmpty} value.
     *
     * @return the value
     */
    public boolean isEmpty() {
        return text.isEmpty();
    }
}
