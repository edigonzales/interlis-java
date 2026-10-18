package guru.interlis.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Exact cardinality; unbounded is an explicit variant, never -1.
 *
 * @param lower inclusive lower bound
 * @param upper upper bound
 */
public record Cardinality(BigInteger lower, CardinalityUpper upper) {
    /** Creates a validated instance. */
    public Cardinality {
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
        if (lower.signum() < 0) {
            throw new IllegalArgumentException("lower bound must be non-negative");
        }
        if (upper instanceof CardinalityUpper.Finite finite && finite.value().compareTo(lower) < 0) {
            throw new IllegalArgumentException("upper bound must not be below lower bound");
        }
    }

    /**
     * Executes {@code of}.
     *
     * @param lower the {@code lower} argument
     * @param upper the {@code upper} argument
     *
     * @return the result
     */
    public static Cardinality of(long lower, long upper) {
        return new Cardinality(BigInteger.valueOf(lower), CardinalityUpper.finite(upper));
    }

    /**
     * Executes {@code exactly}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public static Cardinality exactly(long value) {
        return of(value, value);
    }

    /**
     * Executes {@code unboundedFrom}.
     *
     * @param lower the {@code lower} argument
     *
     * @return the result
     */
    public static Cardinality unboundedFrom(long lower) {
        return new Cardinality(BigInteger.valueOf(lower), CardinalityUpper.unbounded());
    }
}
