package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AggregationViewTest {
    @Test
    void groupsByExactKeysAndCountsAMultiObjectGroup() {
        var plan = plan("""
                CLASS Item = key: 0.0..10.0; END Item;
                VIEW Grouped AGGREGATION OF item~Item EQUAL (item->key); =
                  size := INTERLIS.elementCount(AGGREGATES);
                  MANDATORY CONSTRAINT size >= 2;
                END Grouped;
                """);
        var data = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            data.append("<Item i:tid='g").append(i).append("'><key>1.0</key></Item>");
        }
        data.append("<Item i:tid='only'><key>2.0</key></Item>");
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", data.toString())));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals("only", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
        var empty = validate(plan, List.of("Item"), primary("p", basket("basket", "")));
        assertTrue(empty.coverage().constraints().isEmpty());
    }
}
