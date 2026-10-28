package guru.interlis.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Exact numeric literal with its original lexeme and precision metadata.
 *
 * @param lexeme the {@code lexeme} component
 * @param exactValue the {@code exactValue} component
 * @param precision the {@code precision} component
 */
public record DecimalLiteral(String lexeme, BigDecimal exactValue, DecimalPrecision precision) {
    /** Creates a validated instance. */
    public DecimalLiteral {
        Objects.requireNonNull(lexeme, "lexeme");
        Objects.requireNonNull(exactValue, "exactValue");
        Objects.requireNonNull(precision, "precision");
    }

    /**
     * Executes {@code parse}.
     *
     * @param lexeme the {@code lexeme} argument
     *
     * @return the result
     */
    public static DecimalLiteral parse(String lexeme) {
        Objects.requireNonNull(lexeme, "lexeme");
        BigDecimal value = new BigDecimal(lexeme);
        int e = Math.max(lexeme.indexOf('e'), lexeme.indexOf('E'));
        String mantissa = e < 0 ? lexeme : lexeme.substring(0, e);
        int dot = mantissa.indexOf('.');
        int fraction = dot < 0 ? 0 : mantissa.length() - dot - 1;
        int exponent = e < 0 ? 0 : Integer.parseInt(lexeme.substring(e + 1));
        return new DecimalLiteral(lexeme, value,
                new DecimalPrecision(fraction, exponent, PrecisionOrigin.LEXICAL));
    }

    @Override
    public String toString() {
        return lexeme;
    }
}
