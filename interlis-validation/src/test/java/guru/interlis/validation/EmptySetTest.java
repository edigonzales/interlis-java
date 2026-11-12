package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class EmptySetTest {
    @Test
    void evaluatesAnExplicitlyClosedEmptyClassAndQualifiesAnOpenOne() {
        var plan = plan("""
                CLASS Item =
                  SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
                END Item;
                """);
        var headerOnly = primary("p", "");
        var membership = new ExtentDeclaration(new ExtentSelector(type(plan, "Item"),
                Set.of(new InputId("p")), Optional.empty()), ExtentAspect.OBJECT_MEMBERSHIP, "empty inventory");
        var closed = validateWithClaims(plan, List.of(membership), headerOnly);
        assertEquals(1, closed.detectedViolations());
        var violation = closed.examples().getFirst();
        assertEquals(ValidationCode.SET_CONSTRAINT_FALSE, violation.code());
        assertEquals(List.of("0"), violation.arguments());
        assertTrue(violation.data().basket().isEmpty());
        assertEquals(type(plan, "Item"), violation.data().objectType().orElseThrow());
        var coverage = closed.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.Completeness.DECLARED_COMPLETE, coverage.completeness());
        assertEquals(0, coverage.candidates());
        assertEquals(1, coverage.evaluations());

        var open = validate(plan, List.of(), headerOnly);
        assertEquals(0, open.detectedViolations());
        assertEquals(DatasetRuleCoverage.Outcome.UNDETERMINED, open.coverage().datasetRules().getFirst().outcome());
        assertEquals(0, open.coverage().datasetRules().getFirst().candidates());
        assertEquals(1, open.coverage().datasetRules().getFirst().scopes());
    }

    @Test
    void evaluatesAnActualEmptyBasketAndDoesNotInventOneForHeaderOnly() {
        var plan = plan("""
                CLASS Item =
                  SET CONSTRAINT (BASKET) INTERLIS.objectCount(ALL) > 0;
                END Item;
                """);
        var missing = validate(plan, List.of(), primary("p", ""));
        assertEquals(0, missing.detectedViolations());
        assertEquals(0, missing.coverage().datasetRules().getFirst().scopes());
        assertEquals(DatasetRuleCoverage.Outcome.NOT_APPLICABLE, missing.coverage().datasetRules().getFirst().outcome());

        var present = validate(plan, List.of(), primary("p", basket("empty", "")));
        assertEquals(1, present.detectedViolations());
        assertEquals("empty", present.examples().getFirst().data().basket().orElseThrow().value());
        var coverage = present.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.ScopeKind.BASKET, coverage.scopeKind());
        assertEquals(DatasetRuleCoverage.Completeness.CLOSED_BY_TRANSFER, coverage.completeness());
        assertEquals(1, coverage.scopes());
        assertEquals(0, coverage.candidates());
        assertEquals(1, coverage.evaluations());
    }

    @Test
    void countsOnlyTheFilteredBoundClassIncludingItsSubclasses() {
        var plan = plan("""
                CLASS Base (ABSTRACT) =
                  active: BOOLEAN;
                  SET CONSTRAINT (BASKET) WHERE active:
                    INTERLIS.objectCount(ALL) > 0 AND INTERLIS.objectCount(ALL) <= 1;
                END Base;
                CLASS Child EXTENDS Base = END Child;
                CLASS Other = END Other;
                """);
        var data = "<Child i:tid='skip'><active>false</active></Child><Other i:tid='other'/>";
        var emptyAfterFilter = validate(plan, List.of(), primary("p", basket("filtered", data)));
        assertEquals(List.of("0"), emptyAfterFilter.examples().getFirst().arguments());
        assertEquals(0, emptyAfterFilter.coverage().datasetRules().getFirst().candidates());
        assertEquals(1, emptyAfterFilter.coverage().datasetRules().getFirst().evaluations());

        var positive = validate(plan, List.of(), primary("p", basket("filtered",
                data + "<Child i:tid='pass'><active>true</active></Child>")));
        assertEquals(0, positive.detectedViolations(), positive.examples().toString());
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, positive.coverage().datasetRules().getFirst().outcome());
        assertEquals(1, positive.coverage().datasetRules().getFirst().candidates());
        assertEquals(1, positive.coverage().datasetRules().getFirst().evaluations());
    }

    @Test
    void filtersOutReferenceOnlyPopulations() {
        var plan = plan("""
                CLASS Item =
                  SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
                END Item;
                """);
        var report = validate(plan, List.of("Item"), primary("p", ""),
                reference("ref", basket("witnesses", "<Item i:tid='r'/>")));
        assertEquals(1, report.detectedViolations());
        assertEquals(List.of("0"), report.examples().getFirst().arguments());
    }
    @Test
    void leavesOtherSetFunctionsExplicitlyUnsupported() {
        var plan = plan("""
                DOMAIN Point = COORD 0.000..100.000, 0.000..100.000;
                CLASS Item =
                  area: SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.010;
                  SET CONSTRAINT INTERLIS.areAreas(ALL, UNDEFINED, >>area);
                END Item;
                """);
        var report = validate(plan, List.of("Item"), primary("p", ""));
        assertEquals(0, report.detectedViolations());
        assertEquals(DatasetRuleCoverage.Outcome.UNSUPPORTED, report.coverage().datasetRules().getFirst().outcome());
        assertEquals(0, report.coverage().datasetRules().getFirst().evaluations());
    }

}
