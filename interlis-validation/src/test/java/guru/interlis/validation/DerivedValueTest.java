package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DerivedValueTest {
    @Test
    void readsLastDefinedOverrideAndDoesNotRequireTransientTransferValue() {
        var plan = plan("""
                CLASS Base (ABSTRACT) =
                  first: 0..10;
                  second: 0..10;
                  chosen (TRANSIENT): 0..10 := first;
                END Base;
                CLASS Item EXTENDS Base =
                  chosen (EXTENDED, TRANSIENT): 0..10 := second;
                  expected: 0..10;
                  MANDATORY CONSTRAINT chosen == expected;
                END Item;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                <Item i:tid="a"><first>1</first><second>2</second><expected>2</expected></Item>
                <Item i:tid="b"><first>1</first><expected>1</expected></Item>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, report.coverage().constraints().getFirst().status());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
    }
    @Test
    void exposesDeferredMissingParameterAndActiveCyclesInCoverage() {
        var schema = ValidationPlannerTest.compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  PARAMETER factor: 1..10;
                  TOPIC Data =
                    CLASS Node =
                      amount: 0..100;
                      peer: REFERENCE TO (EXTERNAL) Node;
                      mirror: MANDATORY 0..1000 := peer->amount * PARAMETER factor;
                      first (TRANSIENT): 0..100 := second;
                      second (TRANSIENT): 0..100 := first;
                      MANDATORY CONSTRAINT first == amount;
                    END Node;
                  END Data;
                END Checks.
                """);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class, ValidationPlanner.plan(schema,
                ValidationPlannerTest.transfer(schema), ValidationProfile.DATASET)).plan();
        var report = validate(plan, List.of("Node"), primary("p",
                basket("first", "<Node i:tid='a'><amount>5</amount><peer i:ref='b'/></Node>")
                        + basket("second", "<Node i:tid='b'><amount>7</amount><peer i:ref='a'/></Node>")));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(2, report.coverage().missingRuntimeContext().stream()
                .filter(count -> count.family() == ValidationCoverage.RuleFamily.VALUE_RANGE)
                .mapToLong(ValidationCoverage.RuleCount::occurrences).sum());
        assertEquals(ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT,
                report.coverage().constraints().getFirst().status());
    }
}
