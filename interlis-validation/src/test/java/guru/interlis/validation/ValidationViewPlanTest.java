package guru.interlis.validation;

import guru.interlis.model.QualifiedName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidationViewPlanTest {
    @Test
    void acceptsViewAsExplicitSubjectWithoutAddingATransferObject() {
        var plan = plan("""
                CLASS Item = amount: 0..10; END Item;
                VIEW Empty PROJECTION OF item~Item;
                  WHERE item->amount > 10;
                  =
                    MANDATORY CONSTRAINT #false;
                END Empty;
                """);
        var id = plan.symbols(QualifiedName.of("Checks", "Data", "Empty")).getFirst();
        var ref = plan.ref(id);
        assertTrue(plan.transferPlan().object(ref).isEmpty());
        assertNotNull(plan.views().get(id));
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket",
                "<Item i:tid='item'><amount>5</amount></Item>")));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertTrue(report.coverage().constraints().isEmpty());
    }
    @Test
    void reportsDuplicateKeysInVirtualRows() {
        var plan = plan("""
                CLASS Item = code: TEXT; END Item;
                VIEW Keys PROJECTION OF item~Item; =
                  code := item->code;
                  UNIQUE code;
                END Keys;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", """
                <Item i:tid='first'><code>same</code></Item>
                <Item i:tid='second'><code>same</code></Item>
                <Item i:tid='third'><code>same</code></Item>
                """)));
        assertEquals(1, report.detectedViolations());
        assertEquals(ValidationCode.DATASET_UNIQUE_DUPLICATE, report.examples().getFirst().code());
        assertEquals("first", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(List.of("p", "basket", "second", "3"), report.examples().getFirst().arguments());
        assertEquals(3, report.coverage().constraints().getFirst().occurrences());
    }

    @Test
    void recordsUnsupportedAreaInspectionEvenWithoutAnyDataRows() {
        var plan = plan("""
                DOMAIN Position = COORD 0.00..10.00, 0.00..10.00;
                CLASS Item =
                  area: AREA WITH (STRAIGHTS) VERTEX Position WITHOUT OVERLAPS > 0.01;
                END Item;
                VIEW Boundaries AREA INSPECTION OF edge~Item->area; =
                  MANDATORY CONSTRAINT #false;
                END Boundaries;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", "")));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(ValidationCoverage.ConstraintStatus.UNSUPPORTED_EXPRESSION,
                report.coverage().constraints().getFirst().status());
    }
    @Test
    void reportsADistinctRealWitnessForJoinedDuplicateRows() {
        var plan = plan("""
                CLASS LeftSide = code: TEXT; END LeftSide;
                CLASS RightSide = END RightSide;
                VIEW Keys JOIN OF l~LeftSide,r~RightSide; =
                  code := l->code;
                  UNIQUE code;
                END Keys;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", """
                <LeftSide i:tid='l1'><code>same</code></LeftSide>
                <LeftSide i:tid='l2'><code>same</code></LeftSide>
                <RightSide i:tid='r1'/>
                <RightSide i:tid='r2'/>
                """)));
        assertEquals(1, report.detectedViolations());
        var finding = report.examples().getFirst();
        assertEquals("l1", finding.data().tid().orElseThrow().value());
        assertEquals(List.of("p", "basket", "r2", "4"), finding.arguments());
        assertEquals(4, report.coverage().constraints().getFirst().occurrences());
    }
    @Test
    void distinguishesDuplicateInspectionRowsFromTheSameOwner() {
        var plan = plan("""
                STRUCTURE Part = code: TEXT; END Part;
                CLASS Item = parts: BAG OF Part; END Item;
                VIEW Keys INSPECTION OF part~Item->parts; =
                  code := part->code;
                  UNIQUE code;
                END Keys;
                """);
        var part = "<parts><Part><code>same</code></Part></parts>";
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket",
                "<Item i:tid='owner'>" + part.repeat(3) + "</Item>")));
        assertEquals(1, report.detectedViolations());
        var finding = report.examples().getFirst();
        assertEquals(ValidationCode.DATASET_UNIQUE_DUPLICATE, finding.code());
        assertEquals("owner", finding.data().tid().orElseThrow().value());
        assertEquals(List.of("p", "basket", "owner", "3", "1", "2"), finding.arguments());
        assertEquals(3, report.coverage().constraints().getFirst().occurrences());
    }
}
