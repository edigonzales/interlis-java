package guru.interlis.validation;

import guru.interlis.model.BinaryOperator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class NumericEvaluationTest {
    @Test
    void evaluatesArithmeticAndConstantsInBoundConstraints() {
        var plan = plan("""
                CLASS Item =
                  amount: -10.0..10.0;
                  divisor: 0..3;
                  MANDATORY CONSTRAINT Exact: ((amount + 0.2) - 0.3) == 0;
                  MANDATORY CONSTRAINT Rational: ((1/3)*3) == 1;
                  MANDATORY CONSTRAINT Negation: (0 - amount) < 0;
                  MANDATORY CONSTRAINT Zero: (1/divisor) == UNDEFINED;
                  MANDATORY CONSTRAINT Constants: (PI > 3.14159265358979323846) AND
                      (LNBASE < 2.71828182845904523537) AND ((PI/PI) == 1);
                END Item;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("b", """
                <Item i:tid="item"><amount>0.1</amount><divisor>0</divisor></Item>
                """)));
        assertEquals(0, report.detectedViolations(), report.examples().toString());
        assertTrue(report.coverage().notChecked().isEmpty());
        assertEquals(5, report.coverage().constraints().size());
        assertTrue(report.coverage().constraints().stream()
                .allMatch(rule -> rule.status() == ValidationCoverage.ConstraintStatus.CHECKED));
    }

    @Test
    void preservesRationalValuesAndFailsAtTheTechnicalSizeLimit() {
        var third = NumericSemantics.divide(number("1"), number("3")).orElseThrow();
        assertEquals(BigInteger.ONE, third.lower().numerator());
        assertEquals(BigInteger.valueOf(3), third.lower().denominator());
        assertEquals(third.lower(), third.upper());
        assertTrue(NumericSemantics.compare(BinaryOperator.GT, third, number("0.3333333333333333333333333333333333")));
        assertTrue(NumericSemantics.compare(BinaryOperator.LT, third, number("0.3333333333333333333333333333333334")));
        assertEquals(number("0"), NumericSemantics.negate(number("-0.000")));
        assertTrue(NumericSemantics.divide(number("1"), number("-0")).isEmpty());
        assertThrows(ValidationLimitException.class, () -> NumericSemantics.multiply(number("1e10000"), number("1e10000")));
    }

    private static NumericSemantics.Number number(String decimal) {
        return NumericSemantics.Number.exact(new BigDecimal(decimal));
    }
}
