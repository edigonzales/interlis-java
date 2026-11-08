package guru.interlis.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Exact decimal value with a spelling and its representation metadata.
 *
 * @param lexeme original source spelling for {@link PrecisionOrigin#LEXICAL};
 *               deterministic generated spelling of the exact value for
 *               {@link PrecisionOrigin#COMPUTED}
 * @param exactValue exact, non-null decimal value
 * @param precision non-null metadata describing the spelling and its origin.
 *                  A computed spelling alone establishes no quantization grid.
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
