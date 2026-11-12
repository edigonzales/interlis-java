package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtentCoverageTest {
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
        var report = validate(plan, List.of(), primary("p", basket("data", SUBJECT)));
        assertEquals(0, report.detectedViolations());
        var rules = report.coverage().datasetRules();
        assertEquals(List.of(DatasetRuleCoverage.Completeness.OPEN, DatasetRuleCoverage.Completeness.OPEN),
                rules.stream().map(DatasetRuleCoverage::completeness).toList());
        assertEquals(List.of(DatasetRuleCoverage.Outcome.NO_VIOLATION_IN_AVAILABLE_SCOPE,
                        DatasetRuleCoverage.Outcome.UNDETERMINED),
                rules.stream().map(DatasetRuleCoverage::outcome).toList());
    }

    @Test
    void reportsPositiveWitnessInOpenExtent() {
        var plan = plan(MODEL);
        var report = validate(plan, List.of(),
                primary("p", basket("data", SUBJECT)),
                reference("r", basket("support", "<Target i:tid='t'><code>key</code></Target>")));
        assertEquals(0, report.detectedViolations());
        var existence = report.coverage().datasetRules().getLast();
        assertEquals(DatasetRuleCoverage.Completeness.OPEN, existence.completeness());
        assertEquals(1, existence.evaluations());
        assertEquals(0, existence.undecidedCandidates());
    }

    @Test
    void keepsNegativeClaimUnknownWithoutCompleteAttributeValues() {
        var plan = plan(MODEL);
        var selector = new ExtentSelector(type(plan, "Target"), Set.of(new InputId("p")), Optional.empty());
        var membership = new ExtentDeclaration(selector, ExtentAspect.OBJECT_MEMBERSHIP, "inventory");
        var values = new ExtentDeclaration(selector, ExtentAspect.ATTRIBUTE_VALUES, "attribute values");
        var data = primary("p", basket("data", SUBJECT));
        for (var claim : List.of(membership, values)) {
            var incomplete = validateWithClaims(plan, List.of(claim), data);
            assertEquals(0, incomplete.detectedViolations());
            var existence = incomplete.coverage().datasetRules().getLast();
            assertEquals(DatasetRuleCoverage.Outcome.UNDETERMINED, existence.outcome());
            assertEquals(1, existence.undecidedCandidates());
        }
        var closed = validateWithClaims(plan, List.of(membership, values), data);
        assertEquals(1, closed.detectedViolations());
        assertEquals(ValidationCode.EXISTENCE_TARGET_MISSING, closed.examples().getFirst().code());
    }
}
