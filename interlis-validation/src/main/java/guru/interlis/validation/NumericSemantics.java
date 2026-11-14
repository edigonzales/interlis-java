package guru.interlis.validation;

import guru.interlis.model.BinaryOperator;
import guru.interlis.transfer.TransferValue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/** Decimal arithmetic for bound expressions and unit conversions. */
final class NumericSemantics {
    private static final MathContext PRECISION = new MathContext(50, RoundingMode.HALF_EVEN);
    private static final Number PI = new Number(new BigDecimal(
            "3.14159265358979323846264338327950288419716939937510", PRECISION));
    private static final Number LNBASE = new Number(new BigDecimal(
            "2.71828182845904523536028747135266249775724709369995", PRECISION));

    record Number(BigDecimal decimal) {
        Number {
            ScalarRules.requireNumericBudget(decimal);
        }

        TransferValue.NumberValue asValue() {
            return new TransferValue.NumberValue(decimal.toString(), decimal);
        }
    }

    private NumericSemantics() { }

    static Number constant(String name) {
        return switch (name) {
            case "PI" -> PI;
            case "LNBASE" -> LNBASE;
            default -> throw new IllegalArgumentException("Unknown numerical constant " + name);
        };
    }

    static Number add(Number left, Number right) {
        return new Number(left.decimal().add(right.decimal()));
    }

    static Number negate(Number number) {
        return new Number(number.decimal().negate());
    }

    static Number subtract(Number left, Number right) {
        return new Number(left.decimal().subtract(right.decimal()));
    }

    static Number multiply(Number left, Number right) {
        return new Number(left.decimal().multiply(right.decimal()));
    }

    static Optional<Number> divide(Number numerator, Number denominator) {
        var divisor = denominator.decimal();
        if (divisor.signum() == 0) {
            return Optional.empty();
        }
        var dividend = numerator.decimal();
        BigDecimal quotient;
        try {
            quotient = dividend.divide(divisor);
        } catch (ArithmeticException nonTerminating) {
            // Operand scale and coefficient bounds keep the exact attempt within BigDecimal's scale range.
            quotient = dividend.divide(divisor, PRECISION);
        }
        return Optional.of(new Number(quotient));
    }

    static boolean compare(BinaryOperator operator, Number left, Number right) {
        int order = left.decimal().compareTo(right.decimal());
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

    static boolean matchesRounded(Number computed, BigDecimal supplied, int scale) {
        var decimal = computed.decimal();
        var lower = decimal.setScale(scale, RoundingMode.FLOOR);
        var upper = decimal.setScale(scale, RoundingMode.CEILING);
        return lower.compareTo(supplied) == 0 || upper.compareTo(supplied) == 0;
    }
}
