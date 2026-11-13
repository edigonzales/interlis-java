package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AggregationViewTest {
    @Test
    void retainsTheComputedBaseOfAnAggregation() {
        var plan = plan("""
                CLASS Item = key: 0..10; END Item;
                VIEW Keys PROJECTION OF item~Item; =
                  computed := item->key + 1;
                END Keys;
                VIEW Grouped AGGREGATION OF keys~Keys EQUAL (keys->computed); =
                  MANDATORY CONSTRAINT keys->computed == 2 AND INTERLIS.elementCount(AGGREGATES) == 2;
                END Grouped;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", """
                <Item i:tid='first'><key>1</key></Item>
                <Item i:tid='second'><key>1</key></Item>
                <Item i:tid='wrong'><key>2</key></Item>
                """)));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals("wrong", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
    }

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
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", data.toString())));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals("only", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
        var empty = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", "")));
        assertTrue(empty.coverage().constraints().isEmpty());
    }
}
