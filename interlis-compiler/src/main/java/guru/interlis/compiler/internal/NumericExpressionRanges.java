package guru.interlis.compiler.internal;

import guru.interlis.model.BinaryOperator;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DecimalPrecision;
import guru.interlis.model.NumericDirection;
import guru.interlis.model.NumericRange;
import guru.interlis.model.PrecisionOrigin;
import guru.interlis.model.SymbolId;
import guru.interlis.model.internal.DecimalMath;

import java.math.BigDecimal;
import java.util.Optional;

/** Bounds derived using the same decimal arithmetic as expression evaluation. */
final class NumericExpressionRanges {
    private NumericExpressionRanges() { }

    static boolean compatibleAdditiveUnits(BinaryOperator operator, NumericRange left, NumericRange right) {
        return operator != BinaryOperator.PLUS && operator != BinaryOperator.MINUS
                || left.unit().equals(right.unit());
    }

    record Estimate(NumericRange range, boolean rounded) { }

    static Estimate infer(BinaryOperator operator, NumericRange left, NumericRange right, SemanticWork work) {
        return infer(operator, left, right, false, work);
    }

    static Estimate infer(BinaryOperator operator, NumericRange left, NumericRange right,
                          boolean sameOperand, SemanticWork work) {
        work.checkpoint();
        Optional<SymbolId> unit = left.unit();
        if (operator == BinaryOperator.MUL) {
            if (left.unit().isPresent() && right.unit().isPresent()) return new Estimate(NumericRange.unbounded(), false);
            if (unit.isEmpty()) unit = right.unit();
        } else if (operator == BinaryOperator.DIV && right.unit().isPresent()) {
            if (!left.unit().equals(right.unit())) return new Estimate(NumericRange.unbounded(), false);
            unit = Optional.empty();
        }

        if (left.minimum().isEmpty() || left.maximum().isEmpty()
                || right.minimum().isEmpty() || right.maximum().isEmpty()) {
            return new Estimate(range(null, null, unit), false);
        }
        var a = new BigDecimal[] { checked(left.minimum().orElseThrow().exactValue()),
                checked(left.maximum().orElseThrow().exactValue()) };
        var b = new BigDecimal[] { checked(right.minimum().orElseThrow().exactValue()),
                checked(right.maximum().orElseThrow().exactValue()) };
        boolean divide = operator == BinaryOperator.DIV;
        if (divide && b[0].signum() <= 0 && b[1].signum() >= 0) return new Estimate(range(null, null, unit), false);

        // A fixed terminating reciprocal is exact for every finite decimal operand.
        boolean exact = !divide || (b[0].compareTo(b[1]) == 0
                && (a[0].compareTo(a[1]) == 0 || terminatingReciprocal(b[0])));
        boolean rounded = false;
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < b.length; j++) {
                if (sameOperand && i != j) continue;
                var l = a[i];
                var r = b[j];
                work.checkpoint();
                var lower = checked(exact ? operate(operator, l, r)
                        : DecimalMath.divideOutwards(l, r, true));
                var upper = exact ? lower : checked(DecimalMath.divideOutwards(l, r, false));
                rounded |= lower.compareTo(upper) != 0;
                minimum = minimum == null ? lower : minimum.min(lower);
                maximum = maximum == null ? upper : maximum.max(upper);
            }
        }
        if (sameOperand && operator == BinaryOperator.MUL && a[0].signum() <= 0 && a[1].signum() >= 0) {
            minimum = BigDecimal.ZERO;
        }
        return new Estimate(range(minimum, maximum, unit), rounded);
    }

    private static boolean terminatingReciprocal(BigDecimal divisor) {
        try {
            BigDecimal.ONE.divide(divisor);
            return true;
        } catch (ArithmeticException nonTerminating) {
            return false;
        }
    }

    private static BigDecimal operate(BinaryOperator operator, BigDecimal left, BigDecimal right) {
        return switch (operator) {
            case PLUS -> left.add(right);
            case MINUS -> left.subtract(right);
            case MUL -> left.multiply(right);
            case DIV -> DecimalMath.divide(left, right).orElseThrow();
            default -> throw new IllegalArgumentException("not a numeric operator");
        };
    }

    static BigDecimal checked(BigDecimal value) {
        if (value.unscaledValue().bitLength() > 16_384 || Math.abs((long) value.scale()) > 10_000) {
            throw new AnalysisBudgetExceededException("numericMagnitudeOrScale", 16_384);
        }
        return value;
    }

    private static NumericRange range(BigDecimal lower, BigDecimal upper, Optional<SymbolId> unit) {
        return new NumericRange(Optional.ofNullable(lower).map(NumericExpressionRanges::boundary),
                Optional.ofNullable(upper).map(NumericExpressionRanges::boundary),
                false, NumericDirection.NONE, unit, Optional.empty());
    }

    static NumericRange singleton(BigDecimal value) {
        return range(value, value, Optional.empty());
    }

    static DecimalLiteral boundary(BigDecimal value) {
        var representation = DecimalLiteral.parse(value.stripTrailingZeros().toString());
        var precision = representation.precision();
        return new DecimalLiteral(representation.lexeme(), representation.exactValue(),
                new DecimalPrecision(precision.fractionalDigits(), precision.exponent(), PrecisionOrigin.COMPUTED));
    }
}
