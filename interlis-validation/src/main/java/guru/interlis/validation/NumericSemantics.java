package guru.interlis.validation;

import guru.interlis.model.BinaryOperator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/** Exact rational arithmetic with outward bounds for irrational constants. */
final class NumericSemantics {
    private static final int MAX_BITS = 65_536;

    record Rational(BigInteger numerator, BigInteger denominator) implements Comparable<Rational> {
        Rational {
            checkSize(numerator);
            checkSize(denominator);
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            if (denominator.signum() == 0) {
                throw new ArithmeticException("Zero rational denominator");
            }
            var gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }

        static Rational decimal(BigDecimal decimal) {
            ScalarRules.requireNumericBudget(decimal);
            int scale = decimal.scale();
            return scale < 0
                    ? new Rational(decimal.unscaledValue().multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE)
                    : new Rational(decimal.unscaledValue(), BigInteger.TEN.pow(scale));
        }

        Rational add(Rational other) {
            return new Rational(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        Rational multiply(Rational other) {
            return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
        }

        Rational reciprocal() {
            return new Rational(denominator, numerator);
        }

        Rational negate() {
            return new Rational(numerator.negate(), denominator);
        }

        @Override
        public int compareTo(Rational other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
        }

        private static void checkSize(BigInteger integer) {
            if (integer.bitLength() > MAX_BITS) {
                throw new ValidationLimitException("Numeric expression exceeds the rational size limit");
            }
        }
    }

    record Number(Rational lower, Rational upper, String constant) {
        static Number exact(BigDecimal decimal) {
            var value = Rational.decimal(decimal);
            return new Number(value, value, "");
        }

        boolean exact() {
            return lower.equals(upper);
        }
    }

    private static final Rational ZERO = Rational.decimal(BigDecimal.ZERO);
    private static final Number PI = interval("PI", "3.14159265358979323846264338327950288419716939937510");
    private static final Number LNBASE = interval("LNBASE", "2.71828182845904523536028747135266249775724709369995");

    private NumericSemantics() { }

    static Number constant(String name) {
        return switch (name) {
            case "PI" -> PI;
            case "LNBASE" -> LNBASE;
            default -> throw new IllegalArgumentException("Unknown numerical constant " + name);
        };
    }

    private static Number interval(String name, String digits) {
        var lower = new BigDecimal(digits);
        return new Number(Rational.decimal(lower), Rational.decimal(lower.add(lower.ulp())), name);
    }

    static Number add(Number left, Number right) {
        return new Number(left.lower.add(right.lower), left.upper.add(right.upper), "");
    }

    static Number negate(Number number) {
        return new Number(number.upper.negate(), number.lower.negate(), "");
    }

    static Number subtract(Number left, Number right) {
        return sameConstant(left, right) ? Number.exact(BigDecimal.ZERO) : add(left, negate(right));
    }

    static Number multiply(Number left, Number right) {
        var first = left.lower.multiply(right.lower);
        var lower = first;
        var upper = first;
        for (var candidate : new Rational[] {left.lower.multiply(right.upper),
                left.upper.multiply(right.lower), left.upper.multiply(right.upper)}) {
            if (candidate.compareTo(lower) < 0) {
                lower = candidate;
            }
            if (candidate.compareTo(upper) > 0) {
                upper = candidate;
            }
        }
        return new Number(lower, upper, "");
    }

    static Optional<Number> divide(Number numerator, Number denominator) {
        if (denominator.exact() && denominator.lower.equals(ZERO)) {
            return Optional.empty();
        }
        if (denominator.lower.compareTo(ZERO) <= 0 && denominator.upper.compareTo(ZERO) >= 0) {
            throw precisionLimit();
        }
        if (sameConstant(numerator, denominator)) {
            return Optional.of(Number.exact(BigDecimal.ONE));
        }
        return Optional.of(multiply(numerator,
                new Number(denominator.upper.reciprocal(), denominator.lower.reciprocal(), "")));
    }

    static boolean compare(BinaryOperator operator, Number left, Number right) {
        if (sameConstant(left, right) || left.exact() && right.exact()) {
            return test(operator, left.lower.compareTo(right.lower));
        }
        if ((operator == BinaryOperator.EQ || operator == BinaryOperator.NEQ)
                && (!left.constant.isEmpty() && right.exact() || !right.constant.isEmpty() && left.exact()
                || !left.constant.isEmpty() && !right.constant.isEmpty())) {
            return operator == BinaryOperator.NEQ;
        }
        int minimum = left.lower.compareTo(right.upper);
        int maximum = left.upper.compareTo(right.lower);
        if (minimum > 0 || maximum < 0) {
            return test(operator, minimum > 0 ? minimum : maximum);
        }
        if (operator == BinaryOperator.LTE && maximum == 0 || operator == BinaryOperator.GTE && minimum == 0) {
            return true;
        }
        if (operator == BinaryOperator.LT && minimum == 0 || operator == BinaryOperator.GT && maximum == 0) {
            return false;
        }
        throw precisionLimit();
    }

    private static boolean sameConstant(Number left, Number right) {
        return !left.constant.isEmpty() && left.constant.equals(right.constant);
    }

    private static boolean test(BinaryOperator operator, int order) {
        return switch (operator) {
            case EQ -> order == 0;
            case NEQ -> order != 0;
            case LT -> order < 0;
            case LTE -> order <= 0;
            case GT -> order > 0;
            case GTE -> order >= 0;
            default -> throw new IllegalArgumentException("Expected numerical comparison");
        };
    }

    private static ValidationLimitException precisionLimit() {
        return new ValidationLimitException("Numerical comparison or operation exceeds the irrational precision budget");
    }
}
