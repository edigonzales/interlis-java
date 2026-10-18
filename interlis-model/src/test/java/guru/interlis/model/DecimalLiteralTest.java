package guru.interlis.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecimalLiteralTest {
    @Test
    void largeIntegerAndScaleRemainExact() {
        DecimalLiteral literal = DecimalLiteral.parse("9007199254740993.0010");
        assertEquals("9007199254740993.0010", literal.exactValue().toPlainString());
        assertEquals(new BigDecimal("9007199254740993.0010"), literal.exactValue());
        assertEquals("9007199254740993.0010", literal.lexeme());
        assertEquals(4, literal.precision().fractionalDigits());
        assertEquals(PrecisionOrigin.LEXICAL, literal.precision().origin());
    }

    @Test
    void exponentAndSignArePreservedAsLexicalMetadata() {
        DecimalLiteral literal = DecimalLiteral.parse("1.2e3");
        assertEquals("1.2E+3", literal.exactValue().toString());
        assertEquals(0, literal.precision().fractionalDigits());
        assertEquals(3, literal.precision().exponent());
        assertEquals("+1.50", DecimalLiteral.parse("+1.50").lexeme());
        assertEquals(2, DecimalLiteral.parse("+1.50").precision().fractionalDigits());
    }

    @Test
    void invalidLiteralIsNotSilentlyAccepted() {
        assertThrows(NumberFormatException.class, () -> DecimalLiteral.parse("1.2.3"));
        assertThrows(NumberFormatException.class, () -> DecimalLiteral.parse(""));
    }
}
