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
        new SubjectSelection(plan.snapshotId(), List.of(ref)).check(plan);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket",
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
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                <Item i:tid='first'><code>same</code></Item>
                <Item i:tid='second'><code>same</code></Item>
                """)));
        assertEquals(1, report.detectedViolations());
        assertEquals(ValidationCode.DATASET_UNIQUE_DUPLICATE, report.examples().getFirst().code());
        assertEquals("second", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
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
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", "")));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(ValidationCoverage.ConstraintStatus.UNSUPPORTED_EXPRESSION,
                report.coverage().constraints().getFirst().status());
    }
}
