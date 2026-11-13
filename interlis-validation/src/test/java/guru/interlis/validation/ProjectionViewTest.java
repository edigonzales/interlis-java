package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionViewTest {
    @Test
    void validatesSelectedVirtualRowsAndReportsRealOrigin() {
        var plan = plan("""
                CLASS Item = amount: 0..10; END Item;
                VIEW Selected PROJECTION OF item~Item;
                  WHERE item->amount > 0;
                  =
                    doubled := item->amount + item->amount;
                    MANDATORY CONSTRAINT doubled > 2;
                END Selected;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                <Item i:tid='filtered'><amount>0</amount></Item>
                <Item i:tid='wrong'><amount>1</amount></Item>
                <Item i:tid='right'><amount>2</amount></Item>
                """)));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals("wrong", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
    }
}
