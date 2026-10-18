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
        String cleaned = lexeme.replace("+", "");
        BigDecimal value = new BigDecimal(cleaned);
        int dot = cleaned.indexOf('.');
        int fraction = dot < 0 ? 0 : cleaned.length() - dot - 1;
        int e = Math.max(cleaned.indexOf('e'), cleaned.indexOf('E'));
        int exponent = 0;
        if (e >= 0) {
            exponent = Integer.parseInt(cleaned.substring(e + 1));
            fraction = 0;
        }
        return new DecimalLiteral(lexeme, value,
                new DecimalPrecision(fraction, exponent, PrecisionOrigin.LEXICAL));
    }

    @Override
    public String toString() {
        return lexeme;
    }
}
