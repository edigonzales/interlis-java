package guru.interlis.db;

import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.NumericType;

/** The SQL precision and scale of an ili2db fixed-scale decimal. */
record DbDecimal(int precision, int scale) {
    static boolean fixedScale(NumericType type) {
        var range = type.range();
        return range.minimum().filter(DbDecimal::plainDecimal).isPresent()
                && range.maximum().filter(DbDecimal::plainDecimal).isPresent()
                && range.minimum().orElseThrow().precision().fractionalDigits()
                == range.maximum().orElseThrow().precision().fractionalDigits();
    }

    static DbDecimal describe(NumericType type) {
        var min = type.range().minimum().orElseThrow();
        var max = type.range().maximum().orElseThrow();
        int precision = Math.max(digits(min), digits(max));
        return new DbDecimal(precision, min.precision().fractionalDigits());
    }

    private static boolean plainDecimal(DecimalLiteral value) {
        return value.precision().fractionalDigits() > 0 && value.precision().exponent() == 0;
    }

    private static int digits(DecimalLiteral value) {
        return value.exactValue().abs().toPlainString().length() - 1;
    }
}
