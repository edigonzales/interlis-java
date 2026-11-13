package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AggregateRuntimeTest {
    @Test
    void resolvesReferenceFilterAndRetainsAnUnknownCandidate() {
        var plan = plan("""
                CLASS Target = active: BOOLEAN; END Target;
                CLASS Item =
                  peer: REFERENCE TO (EXTERNAL) Target;
                  SET CONSTRAINT WHERE peer->active:
                    INTERLIS.objectCount(ALL) == 1 AND INTERLIS.objectCount(ALL) == 1;
                END Item;
                """);
        var first = basket("first", "<Item i:tid='item'><peer i:ref='target'/></Item>");
        var witnesses = basket("second", "<Target i:tid='target'><active>true</active></Target>");
        var complete = validate(plan, List.of("Item"), primary("p", first + witnesses));
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, complete.coverage().datasetRules().getFirst().outcome());

        var partial = validate(plan, List.of("Item"), primary("p", first));
        assertEquals(0, partial.detectedViolations());
        var coverage = partial.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.Outcome.UNDETERMINED, coverage.outcome());
        assertEquals(1, coverage.undecidedCandidates());
        assertEquals(0, coverage.evaluations());
    }
}
