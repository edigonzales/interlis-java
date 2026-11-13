package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetScopeTest {
    private static final String MODEL = """
            CLASS Target = code: TEXT*10; END Target;
            CLASS Subject =
              code: TEXT*10;
              UNIQUE code;
              EXISTENCE CONSTRAINT code REQUIRED IN Target:code;
            END Subject;
            """;
    private static final String SUBJECT = "<Subject i:tid='s'><code>key</code></Subject>";

    @Test
    void doesNotInferGlobalCompletenessFromFullTransfer() {
        var plan = plan(MODEL);
        var report = validate(plan, DatasetScope.PARTIAL_DATASET, primary("p", basket("data", SUBJECT)));
        assertEquals(0, report.detectedViolations());
        var rules = report.coverage().datasetRules();
        assertEquals(List.of(DatasetRuleCoverage.Outcome.NO_VIOLATION_IN_AVAILABLE_SCOPE,
                        DatasetRuleCoverage.Outcome.UNDETERMINED),
                rules.stream().map(DatasetRuleCoverage::outcome).toList());
    }

    @Test
    void reportsPositiveWitnessInOpenExtent() {
        var plan = plan(MODEL);
        var report = validate(plan, DatasetScope.PARTIAL_DATASET,
                primary("p", basket("data", SUBJECT)),
                reference("r", basket("support", "<Target i:tid='t'><code>key</code></Target>")));
        assertEquals(0, report.detectedViolations());
        var existence = report.coverage().datasetRules().getLast();
        assertEquals(1, existence.evaluations());
        assertEquals(0, existence.undecidedCandidates());
    }

    @Test
    void leavesNegativeExistenceOpenInPartialDelivery() {
        var plan = plan(MODEL);
        var data = primary("p", basket("data", SUBJECT));
        var partial = validate(plan, DatasetScope.PARTIAL_DATASET, data);
        assertEquals(0, partial.detectedViolations());
        assertEquals(1, partial.coverage().datasetRules().getLast().undecidedCandidates());
        var closed = validate(plan, DatasetScope.SUPPLIED_DATASET, data);
        assertEquals(1, closed.detectedViolations());
        assertEquals(ValidationCode.EXISTENCE_TARGET_MISSING, closed.examples().getFirst().code());
    }
}
