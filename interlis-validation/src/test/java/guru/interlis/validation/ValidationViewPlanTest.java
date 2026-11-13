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
}
