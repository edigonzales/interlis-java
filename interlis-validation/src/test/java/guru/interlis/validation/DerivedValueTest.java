package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DerivedValueTest {
    @Test
    void leavesUndefinedDerivationsOpenForIncompleteBaskets() {
        var plan = plan("""
                CLASS Item =
                  source: 0..10;
                  peer: REFERENCE TO (EXTERNAL) Item;
                  localMirror: MANDATORY 0..10 := source;
                  deferredMirror: MANDATORY 0..10 := peer->source;
                END Item;
                """);
        var input = basket("basket", """
                <Item i:tid="a"><peer i:ref="b"/><localMirror>3</localMirror><deferredMirror>3</deferredMirror></Item>
                <Item i:tid="b"><peer i:ref="a"/></Item>
                """).replace("i:bid=", "i:consistency=\"INCOMPLETE\" i:bid=");
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", input));
        assertEquals(0, report.detectedViolations(), report.examples().toString());
        assertEquals(4, report.coverage().incompleteContext().stream()
                .filter(count -> count.family() == ValidationCoverage.RuleFamily.VALUE_RANGE)
                .mapToLong(ValidationCoverage.RuleCount::occurrences).sum());
    }

    @Test
    void checksSuppliedAndDerivedValuesForDistinctViolations() {
        var plan = plan("""
                CLASS Item =
                  source: 0..10;
                  label: TEXT*10;
                  mirror: MANDATORY 0..10 := source;
                  text: MANDATORY TEXT*10 := label;
                  optional: 0..10 := source;
                END Item;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", """
                <Item i:tid="a"><source>3</source><label>a</label><mirror>11</mirror><text>a</text></Item>
                <Item i:tid="b"><source>3</source><label>b</label><mirror>4</mirror><text>b</text></Item>
                <Item i:tid="c"><label></label><mirror>4</mirror><text>c</text></Item>
                """)));
        assertEquals(5, report.detectedViolations(), report.examples().toString());
        assertEquals(List.of(ValidationCode.VALUE_OUT_OF_RANGE, ValidationCode.DERIVED_VALUE_MISMATCH,
                        ValidationCode.DERIVED_VALUE_MISMATCH, ValidationCode.REQUIRED_VALUE_UNDEFINED,
                        ValidationCode.REQUIRED_VALUE_UNDEFINED),
                report.examples().stream().map(ValidationDiagnostic::code).toList());
    }

    @Test
    void defersComputedPresenceUntilReferenceTargetIsAvailable() {
        var plan = plan("""
                CLASS Item =
                  source: 0..10;
                  peer: REFERENCE TO (EXTERNAL) Item;
                  mirror: MANDATORY 0..10 := peer->source;
                END Item;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", """
                <Item i:tid="a"><peer i:ref="b"/><mirror>3</mirror></Item>
                <Item i:tid="b"><peer i:ref="a"/></Item>
                """)));
        assertEquals(2, report.detectedViolations(), report.examples().toString());
        assertEquals(List.of(ValidationCode.REQUIRED_VALUE_MISSING, ValidationCode.REQUIRED_VALUE_UNDEFINED),
                report.examples().stream().map(ValidationDiagnostic::code).toList());
        assertEquals(List.of("b", "a"), report.examples().stream()
                .map(diagnostic -> diagnostic.data().tid().orElseThrow().value()).toList());
    }

    @Test
    void requiresDeliveredValueEvenWhenItCanBeDerived() {
        var plan = plan("""
                CLASS Item =
                  source: 0..10;
                  required: MANDATORY 0..10;
                  mirror: MANDATORY 0..10 := source;
                END Item;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", """
                <Item i:tid="a"><source>3</source></Item>
                """)));
        assertEquals(2, report.detectedViolations(), report.examples().toString());
        assertTrue(report.examples().stream()
                .allMatch(diagnostic -> diagnostic.code() == ValidationCode.REQUIRED_VALUE_MISSING));
    }

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
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", """
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
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class, ValidationPlanner.plan(ValidationPlannerTest.transfer(schema), ValidationProfile.DATASET)).plan();
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p",
                basket("first", "<Node i:tid='a'><amount>5</amount><peer i:ref='b'/><mirror>7</mirror></Node>")
                        + basket("second", "<Node i:tid='b'><amount>7</amount><peer i:ref='a'/><mirror>5</mirror></Node>")));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(2, report.coverage().missingRuntimeContext().stream()
                .filter(count -> count.family() == ValidationCoverage.RuleFamily.VALUE_RANGE)
                .mapToLong(ValidationCoverage.RuleCount::occurrences).sum());
        assertEquals(ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT,
                report.coverage().constraints().getFirst().status());
    }
}
