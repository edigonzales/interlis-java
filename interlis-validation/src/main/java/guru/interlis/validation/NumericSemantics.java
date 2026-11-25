package guru.interlis.validation;

import guru.interlis.model.BinaryOperator;
import guru.interlis.model.internal.DecimalMath;
import guru.interlis.transfer.TransferValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** Decimal arithmetic for bound expressions and unit conversions. */
final class NumericSemantics {
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
        return new Number(DecimalMath.constant(name));
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
        return DecimalMath.divide(numerator.decimal(), denominator.decimal()).map(Number::new);
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
