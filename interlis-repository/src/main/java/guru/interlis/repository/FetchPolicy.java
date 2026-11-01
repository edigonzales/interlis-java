package guru.interlis.repository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable host IO limits, independent of compiler semantics.
 * @param offline whether only verified local content may be used
 * @param maxBytes positive maximum response size in bytes
 * @param connectTimeout positive limit for establishing a new HTTP connection
 * @param readTimeout positive inactivity limit from sending until headers, then between body data
 * @param requestTimeout optional positive limit for one complete HTTP exchange, including its body;
 *                       redirects and cache retries receive fresh budgets
 */
public record FetchPolicy(boolean offline, int maxBytes, Duration connectTimeout,
                          Duration readTimeout, Optional<Duration> requestTimeout) {
    /** Rejects nulls, nonpositive limits and durations not representable in nanoseconds. */
    public FetchPolicy {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        validate(connectTimeout, "connectTimeout");
        validate(readTimeout, "readTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout").ifPresent(d -> validate(d, "requestTimeout"));
    }

    private static void validate(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) throw new IllegalArgumentException(name + " must be positive");
        try { value.toNanos(); }
        catch (ArithmeticException tooLarge) { throw new IllegalArgumentException(name + " exceeds nanosecond range", tooLarge); }
    }

    /** Online, 16 MiB, 15 seconds connect, 40 seconds inactivity and no total exchange limit. */
    public static FetchPolicy defaults() {
        return new FetchPolicy(false, 16 * 1024 * 1024, Duration.ofSeconds(15),
                Duration.ofSeconds(40), Optional.empty());
    }
}
