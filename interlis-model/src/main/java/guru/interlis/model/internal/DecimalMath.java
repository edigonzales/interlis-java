package guru.interlis.model.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/** Decimal operations shared by static expression checks and evaluation. */
public final class DecimalMath {
    private static final int PRECISION = 50;
    private static final MathContext NEAREST = new MathContext(PRECISION, RoundingMode.HALF_EVEN);
    private static final BigDecimal PI = new BigDecimal(
            "3.14159265358979323846264338327950288419716939937510", NEAREST);
    private static final BigDecimal LNBASE = new BigDecimal(
            "2.71828182845904523536028747135266249775724709369995", NEAREST);

    private DecimalMath() { }

    public static BigDecimal constant(String name) {
        return switch (name) {
            case "PI" -> PI;
            case "LNBASE" -> LNBASE;
            default -> throw new IllegalArgumentException("Unknown numerical constant " + name);
        };
    }

    public static Optional<BigDecimal> divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) return Optional.empty();
        try {
            return Optional.of(numerator.divide(denominator));
        } catch (ArithmeticException nonTerminating) {
            return Optional.of(numerator.divide(denominator, NEAREST));
        }
    }

    public static BigDecimal divideOutwards(BigDecimal numerator, BigDecimal denominator, boolean lower) {
        return numerator.divide(denominator,
                new MathContext(PRECISION, lower ? RoundingMode.FLOOR : RoundingMode.CEILING));
    }
}
