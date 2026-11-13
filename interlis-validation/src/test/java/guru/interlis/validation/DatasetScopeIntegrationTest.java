package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetScopeIntegrationTest {
    private static final String OID = "71c7dbf0-3dbc-4b5c-aa11-888cbfb13676";

    @Test
    void combinesReferenceWitnessesGlobalUniquenessAndDifferentDeliveryScopes() {
        var plan = plan("""
                CLASS Target = OID AS INTERLIS.UUIDOID; END Target;
                CLASS Catalogue = code: TEXT*10; END Catalogue;
                STRUCTURE Pointer = target: REFERENCE TO (EXTERNAL) Target; END Pointer;
                CLASS Item =
                  code: TEXT*10;
                  pointer: Pointer;
                  UNIQUE code;
                  EXISTENCE CONSTRAINT code REQUIRED IN Catalogue:code;
                  SET CONSTRAINT INTERLIS.objectCount(ALL) == 1;
                END Item;
                """);
        var source = primary("subject", basket("source-basket", """
                <Item i:tid='source'><code>alpha</code><pointer><Pointer>
                  <target i:ref='%s'/>
                </Pointer></pointer></Item>
                """.formatted(OID)));
        var duplicate = "<Item i:tid='duplicate'><code>alpha</code></Item>";
        var support = reference("support", basket("support-basket", duplicate
                + "<Target i:tid='" + OID + "'/>"
                + "<Catalogue i:tid='witness'><code>alpha</code></Catalogue>"));
        var proved = validate(plan, DatasetScope.SUPPLIED_DATASET, source, support);
        assertEquals(List.of(ValidationCode.DATASET_UNIQUE_DUPLICATE), codes(proved));
        assertEquals(List.of("support", "support-basket", "duplicate", "2"), proved.examples().getFirst().arguments());
        assertEquals("source", proved.examples().getFirst().data().tid().orElseThrow().value());
        assertTrue(proved.coverage().checked().stream()
                .anyMatch(rule -> rule.family() == ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY));
        assertEquals(0, proved.coverage().datasetRules().get(1).undecidedCandidates());
        assertEquals(1, proved.coverage().datasetRules().getLast().candidates());
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, proved.coverage().datasetRules().getLast().outcome());

        var incompleteSupport = reference("support", basket("support-basket", duplicate));
        var open = validate(plan, DatasetScope.PARTIAL_DATASET, source, incompleteSupport);
        assertEquals(java.util.Optional.of(DatasetScope.PARTIAL_DATASET), open.datasetScope());
        assertEquals(List.of(ValidationCode.DATASET_UNIQUE_DUPLICATE), codes(open));
        assertTrue(open.coverage().missingRuntimeContext().stream()
                .anyMatch(rule -> rule.family() == ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY));
        assertEquals(DatasetRuleCoverage.Outcome.UNDETERMINED, open.coverage().datasetRules().get(1).outcome());

        var closed = validate(plan, DatasetScope.SUPPLIED_DATASET, source, incompleteSupport);
        assertEquals(java.util.Optional.of(DatasetScope.SUPPLIED_DATASET), closed.datasetScope());
        assertEquals(List.of(ValidationCode.REFERENCE_TARGET_MISSING, ValidationCode.DATASET_UNIQUE_DUPLICATE,
                ValidationCode.EXISTENCE_TARGET_MISSING), codes(closed));
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, closed.coverage().datasetRules().getLast().outcome());
    }

    private static List<ValidationCode> codes(ValidationReport report) {
        return report.examples().stream().map(ValidationDiagnostic::code).toList();
    }
}
