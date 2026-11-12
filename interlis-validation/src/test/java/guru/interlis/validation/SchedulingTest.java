package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingTest {
    @Test
    void countsLocalAndDeferredApplicationsOnceInConcurrentSessions() throws Exception {
        var plan = plan("""
                CLASS Item =
                  amount: 0..100;
                  MANDATORY CONSTRAINT amount > 0;
                  SET CONSTRAINT INTERLIS.objectCount(ALL) == 1;
                END Item;
                """);
        assertEquals(1, plan.object(type(plan, "Item")).constraints().size());
        assertEquals(1, plan.deferredRules().size());
        var single = primary("one", basket("basket", "<Item i:tid='a'><amount>1</amount></Item>"));
        var two = primary("two", basket("basket", """
                <Item i:tid='a'><amount>0</amount></Item>
                <Item i:tid='b'><amount>1</amount></Item>
                """));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> validate(plan, List.of("Item"), single));
            var second = workers.submit(() -> validate(plan, List.of("Item"), two));
            var good = first.get(10, TimeUnit.SECONDS);
            var bad = second.get(10, TimeUnit.SECONDS);
            assertEquals(0, good.detectedViolations());
            assertEquals(2, bad.detectedViolations());
            assertEquals(1, good.coverage().constraints().getFirst().occurrences());
            assertEquals(2, bad.coverage().constraints().stream()
                    .mapToLong(ValidationCoverage.ConstraintCount::occurrences).sum());
            assertEquals(1, good.coverage().datasetRules().getFirst().evaluations());
            assertEquals(1, bad.coverage().datasetRules().getFirst().evaluations());
        }
    }
}
