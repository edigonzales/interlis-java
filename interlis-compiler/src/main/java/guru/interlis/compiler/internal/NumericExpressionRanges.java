package guru.interlis.compiler.internal;

import guru.interlis.model.BinaryOperator;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DecimalPrecision;
import guru.interlis.model.NumericDirection;
import guru.interlis.model.NumericRange;
import guru.interlis.model.PrecisionOrigin;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalLong;

/** A bounded exact subset of arithmetic over nominal model values. */
final class NumericExpressionRanges {
    private static final long GRID = 1024;
    private static final long LIMIT = 2048 * GRID;
    private static final BigDecimal GRID_DECIMAL = BigDecimal.valueOf(GRID);

    private NumericExpressionRanges() { }

    static NumericRange infer(BinaryOperator operator, NumericRange left, NumericRange right, SemanticWork work) {
        work.checkpoint();
        boolean additive = operator == BinaryOperator.PLUS || operator == BinaryOperator.MINUS;
        if (additive ? !left.unit().equals(right.unit()) : left.unit().isPresent() || right.unit().isPresent()) {
            return NumericRange.unbounded();
        }
        var a = interval(left);
        var b = interval(right);
        if (a.isEmpty() || b.isEmpty()) return NumericRange.unbounded();
        Interval x = a.orElseThrow();
        Interval y = b.orElseThrow();
        if (!(x.singleton() && y.singleton()) && !(x.integral() && y.integral())) {
            return NumericRange.unbounded();
        }
        if (operator == BinaryOperator.DIV && (!x.singleton() || !y.singleton() || y.minimum() == 0)) {
            return NumericRange.unbounded();
        }
        long minimum = Long.MAX_VALUE;
        long maximum = Long.MIN_VALUE;
        for (long l : new long[]{x.minimum(), x.maximum()}) {
            for (long r : new long[]{y.minimum(), y.maximum()}) {
                work.checkpoint();
                var result = operation(operator, l, r);
                if (result.isEmpty() || !exact(result.getAsLong())) return NumericRange.unbounded();
                minimum = Math.min(minimum, result.getAsLong());
                maximum = Math.max(maximum, result.getAsLong());
            }
        }
        return new NumericRange(Optional.of(boundary(minimum)), Optional.of(boundary(maximum)),
                false, NumericDirection.NONE, left.unit(), Optional.empty());
    }

    private static Optional<Interval> interval(NumericRange range) {
        if (range.minimum().isEmpty() || range.maximum().isEmpty()) return Optional.empty();
        DecimalLiteral minimum = range.minimum().orElseThrow();
        DecimalLiteral maximum = range.maximum().orElseThrow();
        OptionalLong low = gridValue(minimum.exactValue());
        OptionalLong high = gridValue(maximum.exactValue());
        if (low.isEmpty() || high.isEmpty()) return Optional.empty();
        long a = low.getAsLong();
        long b = high.getAsLong();
        boolean integral = a % GRID == 0 && b % GRID == 0
                && (a == b || integerQuantum(minimum) && integerQuantum(maximum));
        return Optional.of(new Interval(a, b, integral));
    }

    private static boolean integerQuantum(DecimalLiteral boundary) {
        var precision = boundary.precision();
        return precision.origin() != PrecisionOrigin.COMPUTED && precision.exponent() >= precision.fractionalDigits();
    }

    private static OptionalLong gridValue(BigDecimal value) {
        // Bound the mantissa and scale before arithmetic or normalization.
        if (value.precision() > 32 || value.scale() < -12 || value.scale() > 20
                || value.abs().compareTo(BigDecimal.valueOf(2048)) > 0) return OptionalLong.empty();
        var scaled = value.multiply(GRID_DECIMAL).stripTrailingZeros();
        if (scaled.scale() > 0) return OptionalLong.empty();
        long grid = scaled.longValueExact();
        return exact(grid) ? OptionalLong.of(grid) : OptionalLong.empty();
    }

    private static boolean exact(long grid) {
        long magnitude = Math.abs(grid);
        // Every admitted nonzero value is normal even in IEEE binary16. Its
        // significand needs at most 11 bits. For integer intervals all interior
        // values and all results between the corner extrema are exact up to 2048.
        // Seven significant decimal digits also fit IEEE decimal32.
        return magnitude <= LIMIT && (magnitude == 0
                || Long.SIZE - Long.numberOfLeadingZeros(magnitude) - Long.numberOfTrailingZeros(magnitude) <= 11
                && decimalValue(grid).precision() <= 7);
    }

    private static OptionalLong operation(BinaryOperator operator, long left, long right) {
        // The grid bound makes products fit in long. Quotients must be exact
        // multiples of the grid step, with division restricted to singletons.
        return switch (operator) {
            case PLUS -> OptionalLong.of(left + right);
            case MINUS -> OptionalLong.of(left - right);
            case MUL -> exactQuotient(left * right, GRID);
            case DIV -> exactQuotient(left * GRID, right);
            default -> throw new IllegalArgumentException("not a numeric operator: " + operator);
        };
    }

    private static OptionalLong exactQuotient(long numerator, long denominator) {
        return numerator % denominator == 0 ? OptionalLong.of(numerator / denominator) : OptionalLong.empty();
    }

    private static BigDecimal decimalValue(long grid) {
        return BigDecimal.valueOf(grid).divide(GRID_DECIMAL).stripTrailingZeros();
    }

    private static DecimalLiteral boundary(long grid) {
        BigDecimal value = decimalValue(grid);
        var representation = DecimalLiteral.parse(value.toString());
        var precision = representation.precision();
        return new DecimalLiteral(representation.lexeme(), value,
                new DecimalPrecision(precision.fractionalDigits(), precision.exponent(), PrecisionOrigin.COMPUTED));
    }

    private record Interval(long minimum, long maximum, boolean integral) {
        boolean singleton() { return minimum == maximum; }
    }
}
