package guru.interlis.model;

import java.util.Objects;

/**
 * Stored lexical precision metadata. It is not a substitute for INTERLIS
 * numeric rules; numeric semantics must derive precision from the domain
 * context as well.
 *
 * @param fractionalDigits fractional digit count
 * @param exponent decimal exponent
 * @param origin origin information
 */
public record DecimalPrecision(int fractionalDigits, int exponent, PrecisionOrigin origin) {
    /** Creates a validated instance. */
    public DecimalPrecision {
        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractionalDigits must be non-negative");
        }
        Objects.requireNonNull(origin, "origin");
    }
}
