package guru.interlis.repository;

import java.time.Duration;
import java.util.Objects;

/**
 * IO policy for host loaders, never a language decision.
 *
 * @param offline the {@code offline} component
 * @param maxBytes the {@code maxBytes} component
 * @param timeout the {@code timeout} component
 */
public record FetchPolicy(boolean offline, int maxBytes, Duration timeout) {
    /** Creates a validated instance. */
    public FetchPolicy {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    /**
     * Returns the {@code defaults} value.
     *
     * @return the value
     */
    public static FetchPolicy defaults() {
        return new FetchPolicy(false, 16 * 1024 * 1024, Duration.ofSeconds(20));
    }
}
