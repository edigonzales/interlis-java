package guru.interlis.validation.jts.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Closed decimal interval with outward rounding for operations involving algebraic roots. */
record Interval(BigDecimal low, BigDecimal high, int digits) {
    static final int INITIAL_DIGITS = 34;
    static final int MAX_DIGITS = 544;
    static final int OVERLAPS_ZERO = 2;

    static Interval exact(BigDecimal value, int digits) {
        return new Interval(value, value, digits);
    }

    static Interval rounded(BigDecimal low, BigDecimal high, int digits) {
        return new Interval(low.round(new MathContext(digits, RoundingMode.FLOOR)),
                high.round(new MathContext(digits, RoundingMode.CEILING)), digits);
    }

    static Interval ratio(BigDecimal numerator, BigDecimal denominator, int digits) {
        return new Interval(numerator.divide(denominator, new MathContext(digits, RoundingMode.FLOOR)),
                numerator.divide(denominator, new MathContext(digits, RoundingMode.CEILING)), digits);
    }

    Interval add(Interval other) {
        return rounded(low.add(other.low), high.add(other.high), digits);
    }

    Interval negate() {
        return new Interval(high.negate(), low.negate(), digits);
    }

    Interval subtract(Interval other) {
        return add(other.negate());
    }

    Interval multiply(Interval other) {
        var a = low.multiply(other.low);
        var b = low.multiply(other.high);
        var c = high.multiply(other.low);
        var d = high.multiply(other.high);
        return rounded(a.min(b).min(c).min(d), a.max(b).max(c).max(d), digits);
    }

    Interval divide(Interval divisor) {
        if (divisor.low.signum() <= 0 && divisor.high.signum() >= 0) {
            throw new ArithmeticException("Divisor interval contains zero");
        }
        var lower = BigDecimal.ONE.divide(divisor.high, new MathContext(digits, RoundingMode.FLOOR));
        var upper = BigDecimal.ONE.divide(divisor.low, new MathContext(digits, RoundingMode.CEILING));
        return multiply(new Interval(lower, upper, digits));
    }

    Interval square() {
        return abs().multiply(abs());
    }

    Interval sqrt() {
        return new Interval(low.max(BigDecimal.ZERO).sqrt(new MathContext(digits, RoundingMode.FLOOR)),
                high.sqrt(new MathContext(digits, RoundingMode.CEILING)), digits);
    }

    Interval abs() {
        if (low.signum() >= 0) {
            return this;
        }
        if (high.signum() <= 0) {
            return negate();
        }
        return new Interval(BigDecimal.ZERO, high.max(low.negate()), digits);
    }

    int sign() {
        if (low.signum() == high.signum()) {
            return low.signum();
        }
        return OVERLAPS_ZERO;
    }

    BigDecimal middle() {
        return low.add(high).multiply(new BigDecimal("0.5"));
    }
}
