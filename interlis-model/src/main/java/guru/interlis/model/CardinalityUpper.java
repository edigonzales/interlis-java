package guru.interlis.model;

import java.math.BigInteger;
import java.util.Objects;

/** Explicit finite or unbounded cardinality upper limit. */
public sealed interface CardinalityUpper permits CardinalityUpper.Finite, CardinalityUpper.Unbounded {
    /**
     * Finite upper bound.
     *
     * @param value non-negative upper bound
     */
    record Finite(BigInteger value) implements CardinalityUpper {
        /** Creates a validated instance. */
        public Finite {
            Objects.requireNonNull(value, "value");
            if (value.signum() < 0) {
                throw new IllegalArgumentException("upper bound must be non-negative");
            }
        }
    }

    /** Explicitly unbounded upper limit. */
    record Unbounded() implements CardinalityUpper {
    }

    /** Returns the unbounded upper limit. */
    static CardinalityUpper unbounded() {
        return new Unbounded();
    }

    /**
     * Returns a finite upper limit.
     *
     * @param value non-negative upper bound
     * @return finite upper limit
     */
    static CardinalityUpper finite(long value) {
        return new Finite(BigInteger.valueOf(value));
    }
}
