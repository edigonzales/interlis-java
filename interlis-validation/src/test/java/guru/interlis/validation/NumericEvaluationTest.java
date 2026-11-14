package guru.interlis.validation;

import guru.interlis.model.BinaryOperator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
                  MANDATORY CONSTRAINT RoundedDivision: ((1/3)*3) != 1;
                  MANDATORY CONSTRAINT Negation: (0 - amount) < 0;
                  MANDATORY CONSTRAINT Zero: (1/divisor) == UNDEFINED;
                  MANDATORY CONSTRAINT Constants: (PI > 3.14159265358979323846) AND
                      (LNBASE < 2.71828182845904523537) AND ((PI/PI) == 1) AND
                      ((PI * 1) == PI) AND ((LNBASE + 0) == LNBASE) AND
                      ((PI / 1) == PI) AND ((LNBASE - 0) == LNBASE);
                END Item;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("b", """
                <Item i:tid="item"><amount>0.1</amount><divisor>0</divisor></Item>
                """)));
        assertEquals(0, report.detectedViolations(), report.examples().toString());
        assertTrue(report.coverage().notChecked().isEmpty());
        assertEquals(5, report.coverage().constraints().size());
        assertTrue(report.coverage().constraints().stream()
                .allMatch(rule -> rule.status() == ValidationCoverage.ConstraintStatus.CHECKED));
    }

    @Test
    void comparesComputedConstantsInBoundConstraints() {
        var plan = plan("""
                CLASS Item =
                  MANDATORY CONSTRAINT SameProduct: (PI * 2) == (PI * 2);
                  MANDATORY CONSTRAINT SameSum: (PI * 2) == (PI + PI);
                  MANDATORY CONSTRAINT SameLnBase: (LNBASE * 2) == (LNBASE + LNBASE);
                  MANDATORY CONSTRAINT Different: (PI * 2) != (PI + PI + 1);
                END Item;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET,
                primary("p", basket("b", "<Item i:tid=\"item\"/>")));
        assertEquals(0, report.detectedViolations(), report.examples().toString());
        assertTrue(report.coverage().notChecked().isEmpty());
        assertEquals(4, report.coverage().constraints().size());
        assertTrue(report.coverage().constraints().stream()
                .allMatch(rule -> rule.status() == ValidationCoverage.ConstraintStatus.CHECKED));
    }

    @Test
    void comparesComputedConstantsDirectly() {
        var two = number("2");
        for (var name : List.of("PI", "LNBASE")) {
            var constant = NumericSemantics.constant(name);
            var product = NumericSemantics.multiply(constant, two);
            var sum = NumericSemantics.add(constant, constant);
            assertTrue(NumericSemantics.compare(BinaryOperator.EQ, product, product));
            assertTrue(NumericSemantics.compare(BinaryOperator.EQ, product, sum));
            assertFalse(NumericSemantics.compare(BinaryOperator.EQ, product, NumericSemantics.add(sum, number("1"))));
        }
    }

    @Test
    void preservesDeliveredDecimalsAndBoundsComputedValues() {
        var third = NumericSemantics.divide(number("1"), number("3")).orElseThrow();
        assertEquals(number("0." + "3".repeat(50)), third);
        assertTrue(NumericSemantics.compare(BinaryOperator.LT, number("9007199254740992"),
                number("9007199254740993")));
        assertFalse(NumericSemantics.compare(BinaryOperator.EQ, NumericSemantics.constant("PI"),
                number("3.141592653589793")));
        assertTrue(NumericSemantics.compare(BinaryOperator.EQ, number("0"),
                NumericSemantics.negate(number("-0.000"))));
        assertTrue(NumericSemantics.divide(number("1"), number("-0")).isEmpty());
        assertThrows(ValidationLimitException.class, () -> NumericSemantics.multiply(number("1e10000"), number("1e10000")));
    }

    private static NumericSemantics.Number number(String decimal) {
        return new NumericSemantics.Number(new BigDecimal(decimal));
    }
}
