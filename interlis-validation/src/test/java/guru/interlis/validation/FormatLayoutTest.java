package guru.interlis.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FormatLayoutTest {
    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void validatesNestedWidthComponentBoundsAndInheritedPrefix(String version) {
        var schema = ValidationPlannerTest.compile("""
                INTERLIS %s;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  STRUCTURE Digits = n: 0..99; END Digits;
                  STRUCTURE Derived EXTENDS Digits =
                    n (EXTENDED): 10..20;
                    extra: 0..9;
                  END Derived;
                  DOMAIN Base = FORMAT BASED ON Digits (n/2) "00".."99";
                    Extension EXTENDS Base = FORMAT BASED ON Derived (INHERITANCE "-" extra) "10-2".."20-7";
                  STRUCTURE Outer = code: Derived; fraction: 0.00..9.99; END Outer;
                  DOMAIN Nested = FORMAT BASED ON Outer (code/Extension "#" fraction/1);
                  TOPIC Data =
                    CLASS Item =
                      value: Nested;
                      MANDATORY CONSTRAINT value >= "10-2#1.00";
                    END Item;
                  END Data;
                END Checks.
                """.formatted(version));
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(ValidationPlannerTest.transfer(schema), ValidationProfile.DATASET)).plan();
        var good = validate(plan, List.of("Item"), primary("p", basket(version, "pass", "10-2#1.00")));
        assertTrue(good.examples().isEmpty(), good.examples().toString());
        assertTrue(good.coverage().unsupportedValues().isEmpty());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, good.coverage().constraints().getFirst().status());
        for (var bad : List.of("9-3#1.00", "10-1#1.00", "20-8#1.00", "11-3#1.0", "11-3#10.00", "11/3#1.00",
                "010-3#1.00", "+10-3#1.00", "11-3#01.00")) {
            var report = validate(plan, List.of("Item"), primary("p", basket(version, "fail", bad)));
            assertEquals(List.of(ValidationCode.INVALID_FORMATTED_VALUE),
                    report.examples().stream().map(ValidationDiagnostic::code).toList(), bad);
        }
    }

    @Test
    void rejectsTooManySignificantDigitsEvenWhenTheComponentRangeAllowsThem() {
        var plan = plan("""
                STRUCTURE Digits = n: 0..999; END Digits;
                DOMAIN Two = FORMAT BASED ON Digits (n/2);
                CLASS Item = value: Two; END Item;
                """);
        var good = validate(plan, List.of("Item"), primary("p", basket("2.4", "pass", "12")));
        assertTrue(good.examples().isEmpty(), good.examples().toString());
        var bad = validate(plan, List.of("Item"), primary("p", basket("2.4", "fail", "123")));
        assertEquals(List.of(ValidationCode.INVALID_FORMATTED_VALUE),
                bad.examples().stream().map(ValidationDiagnostic::code).toList());
    }

    private static String basket(String version, String id, String value) {
        return version.equals("2.3") ? """
                <Checks.Data BID="%s"><Checks.Data.Item TID="item"><value>%s</value></Checks.Data.Item></Checks.Data>
                """.formatted(id, value) : DatasetRuleTestSupport.basket(id,
                "<Item i:tid='item'><value>%s</value></Item>".formatted(value));
    }
}
