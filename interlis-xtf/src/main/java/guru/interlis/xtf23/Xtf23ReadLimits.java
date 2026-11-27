package guru.interlis.xtf23;

/**
 * Configurable resource limits for reading XTF 2.3.
 *
 * @param maxObjectEvents maximum StAX events per object, including its start and end and nested values
 */
public record Xtf23ReadLimits(long maxObjectEvents) {
    /** @throws IllegalArgumentException if the event limit is nonpositive */
    public Xtf23ReadLimits {
        if (maxObjectEvents <= 0) {
            throw new IllegalArgumentException("XTF 2.3 object event limit must be positive");
        }
    }

    /** Returns the standard limits for reading a transfer. */
    public static Xtf23ReadLimits defaults() {
        return new Xtf23ReadLimits(1_000_000);
    }
}
