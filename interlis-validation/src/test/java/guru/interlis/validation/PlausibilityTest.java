package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlausibilityTest {
    private static final String MODEL = """
            CLASS Base =
              active: BOOLEAN;
              CONSTRAINT >= 66.66666666666666666666666666666666% active;
              CONSTRAINT >= 66.66666666666666666666666666666667% active;
              CONSTRAINT <= 66.66666666666666666666666666666666% active;
            END Base;
            CLASS Child EXTENDS Base = END Child;
            """;
    private static final String OBJECTS = """
            <Base i:tid='true'><active>true</active></Base>
            <Child i:tid='false'><active>false</active></Child>
            <Child i:tid='undefined'/>
            """;

    @Test
    void comparesExactFractionsAndIncludesUndefinedAndSpecializationsInTheDenominator() {
        var plan = plan(MODEL);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("pop", OBJECTS)));
        assertEquals(2, report.detectedViolations(), report.examples().toString());
        assertTrue(report.examples().stream().allMatch(d -> d.code() == ValidationCode.PLAUSIBILITY_CONSTRAINT_FALSE));
        assertTrue(report.examples().stream().allMatch(d -> d.arguments().subList(0, 2).equals(List.of("2", "3"))));
        assertEquals(List.of("GTE", "LTE"), report.examples().stream().map(d -> d.arguments().get(2)).toList());
        var scopes = report.coverage().datasetRules();
        assertEquals(3, scopes.size());
        assertTrue(scopes.stream().allMatch(scope -> scope.candidates() == 3 && scope.evaluations() == 1));
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, scopes.getFirst().outcome());
    }

    @Test
    void acceptsAnExactPercentageBoundaryWithoutInventingAValueForTheEmptyPopulation() {
        var plan = plan("""
                CLASS Item =
                  active: BOOLEAN;
                  CONSTRAINT >= 50.00% active;
                  CONSTRAINT <= 50.00% active;
                END Item;
                """);
        var half = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("p", """
                <Item i:tid='one'><active>true</active></Item>
                <Item i:tid='two'><active>false</active></Item>
                """)));
        assertEquals(0, half.detectedViolations());
        assertTrue(half.coverage().datasetRules().stream().allMatch(rule ->
                rule.outcome() == DatasetRuleCoverage.Outcome.SATISFIED && rule.candidates() == 2));

        var empty = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", ""));
        assertEquals(0, empty.detectedViolations());
        assertTrue(empty.coverage().datasetRules().stream().allMatch(rule ->
                rule.outcome() == DatasetRuleCoverage.Outcome.NOT_APPLICABLE
                        && rule.candidates() == 0 && rule.evaluations() == 0));
    }

    @Test
    void appliesTheSameUndefinedDenominatorContractToXtf23() {
        var plan = plan("2.3", """
                CLASS Item =
                  active: BOOLEAN;
                  CONSTRAINT >= 75% active;
                  CONSTRAINT <= 50% active;
                END Item;
                """);
        var input = primary("p", """
                <Checks.Data BID="data">
                  <Checks.Data.Item TID="true"><active>true</active></Checks.Data.Item>
                  <Checks.Data.Item TID="undefined"/>
                </Checks.Data>
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, input);
        assertEquals(1, report.detectedViolations());
        assertEquals(List.of("2", "2", "LTE", "50"), report.examples().getFirst().arguments());
        assertEquals(2, report.coverage().datasetRules().getFirst().candidates());
    }

    @Test
    void leavesAnOpenPopulationUndecidedEvenWhenTheAvailableRatioViolatesTheRule() {
        var plan = plan(MODEL);
        var report = validate(plan, DatasetScope.PARTIAL_DATASET, primary("p", basket("pop", OBJECTS)));
        assertEquals(0, report.detectedViolations());
        assertTrue(report.coverage().datasetRules().stream().allMatch(scope ->
                scope.outcome() == DatasetRuleCoverage.Outcome.UNDETERMINED
                        && scope.candidates() == 3 && scope.evaluations() == 0));
    }
}
