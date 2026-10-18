package guru.interlis.model;

import java.util.Objects;

/**
 * Source documentation text, empty when none was attached.
 *
 * @param text the {@code text} component
 */
public record Documentation(String text) {
    /** Creates a validated instance. */
    public Documentation {
        Objects.requireNonNull(text, "text");
    }

    /**
     * Returns the {@code empty} value.
     *
     * @return the value
     */
    public static Documentation empty() {
        return new Documentation("");
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
