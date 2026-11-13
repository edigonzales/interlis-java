package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ObjectSetRuntimeTest {
    @Test
    void countsTheSameFilteredPopulationAndAnExplicitRestriction() {
        var plan = plan("""
                CLASS Base (ABSTRACT) =
                  active: BOOLEAN;
                  SET CONSTRAINT (BASKET) WHERE active:
                    (INTERLIS.objectCount(ALL) == 2) AND
                    (INTERLIS.objectCount(ALL) == INTERLIS.objectCount(ALL)) AND
                    (INTERLIS.objectCount(ALL(Base)) == 3);
                END Base;
                CLASS Left EXTENDS Base = END Left;
                CLASS Right EXTENDS Base = END Right;
                """);
        var report = validate(plan, List.of(), primary("p", basket("basket", """
                <Left i:tid='left'><active>true</active></Left>
                <Left i:tid='inactive'><active>false</active></Left>
                <Right i:tid='right'><active>true</active></Right>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        var coverage = report.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, coverage.outcome());
        assertEquals(2, coverage.candidates());
        assertEquals(1, coverage.evaluations());
    }

    @Test
    void supportsEmptyExplicitClassPopulation() {
        var plan = plan("""
                CLASS Other = END Other;
                CLASS Item =
                  SET CONSTRAINT (BASKET) INTERLIS.objectCount(ALL(Other)) == 0;
                END Item;
                """);
        var report = validate(plan, List.of(), primary("p", basket("basket", "<Item i:tid='item'/>")));
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, report.coverage().datasetRules().getFirst().outcome());
        assertTrue(report.examples().isEmpty());
    }
}
