package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferReader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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

    @Test
    void keepsNestedReferenceChecksAndSuppliedScopeDistinctFromPartialDelivery() {
        var plan = plan("""
                CLASS Target = OID AS INTERLIS.UUIDOID; amount: 0..10; END Target;
                STRUCTURE Link =
                  peer: REFERENCE TO (EXTERNAL) Target;
                  MANDATORY CONSTRAINT TargetAmount: DEFINED(peer->amount) AND peer->amount >= 5;
                END Link;
                CLASS Item = link: Link; END Item;
                """);
        var source = primary("primary", basket("data", """
                <Item i:tid="item"><link><Link><peer i:ref="%s"/></Link></link></Item>
                """.formatted(OID)));
        var valid = reference("support", basket("reference", "<Target i:tid='" + OID + "'><amount>7</amount></Target>"));
        var invalid = reference("support", basket("reference", "<Target i:tid='" + OID + "'/>"));
        for (var scope : DatasetScope.values()) {
            var checked = validate(plan, scope, source, valid);
            assertTrue(checked.examples().isEmpty(), checked.examples().toString());
            assertEquals(ValidationCoverage.ConstraintStatus.CHECKED,
                    checked.coverage().constraints().getFirst().status());
            var falseConstraint = validate(plan, scope, source, invalid);
            assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE), codes(falseConstraint));
            var data = falseConstraint.examples().getFirst().data();
            assertEquals("item", data.tid().orElseThrow().value());
            assertEquals(1, data.path().size());
            var slot = assertInstanceOf(DataLocation.Slot.class, data.path().getFirst());
            assertEquals("link", plan.transferPlan().symbol(slot.symbol()).known().orElseThrow().name().value());
        }
        var partial = validate(plan, DatasetScope.PARTIAL_DATASET, source);
        assertTrue(partial.examples().isEmpty(), partial.examples().toString());
        assertEquals(ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT,
                partial.coverage().constraints().getFirst().status());
        var supplied = validate(plan, DatasetScope.SUPPLIED_DATASET, source);
        assertEquals(List.of(ValidationCode.REFERENCE_TARGET_MISSING), codes(supplied));
    }

    @Test
    void keepsMissingTargetsOpenInAnIncompleteReferenceBasket() {
        var plan = plan("""
                CLASS Target = OID AS INTERLIS.UUIDOID; amount: 0..10; END Target;
                STRUCTURE Link =
                  peer: REFERENCE TO (EXTERNAL) Target;
                  MANDATORY CONSTRAINT TargetAmount: peer->amount >= 5;
                END Link;
                CLASS Item = link: Link; END Item;
                """);
        var source = primary("primary", basket("data", """
                <Item i:tid="item"><link><Link><peer i:ref="%s"/></Link></link></Item>
                """.formatted(OID)));
        var incomplete = """
                <Data i:bid="reference" i:consistency="INCOMPLETE">%s</Data>
                """;
        var missing = validate(plan, DatasetScope.SUPPLIED_DATASET, source,
                reference("support", incomplete.formatted("")));
        assertEquals(0, missing.detectedViolations(), missing.examples().toString());
        assertTrue(missing.coverage().missingRuntimeContext().stream()
                .anyMatch(rule -> rule.family() == ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY));
        var pending = missing.coverage().constraints().getFirst();
        assertEquals(ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT, pending.status());
        assertEquals(1, pending.occurrences());

        var known = validate(plan, DatasetScope.SUPPLIED_DATASET, source,
                reference("support", incomplete.formatted("""
                        <Target i:tid="%s"><amount>3</amount></Target>
                        """.formatted(OID))));
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE), codes(known));
        var checked = known.coverage().constraints().getFirst();
        assertEquals(pending.constraint(), checked.constraint());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, checked.status());
        assertEquals(1, checked.occurrences());

        var complete = validate(plan, DatasetScope.SUPPLIED_DATASET, source,
                reference("support", basket("reference", "")));
        assertEquals(List.of(ValidationCode.REFERENCE_TARGET_MISSING), codes(complete));
    }

    @Test
    void keepsMissingTargetsOpenWhenAReferenceObjectIsInconsistent() {
        var plan = plan("""
                CLASS Target = OID AS INTERLIS.UUIDOID; amount: 0..10; END Target;
                STRUCTURE Link =
                  peer: REFERENCE TO (EXTERNAL) Target;
                  MANDATORY CONSTRAINT TargetAmount: peer->amount >= 5;
                END Link;
                CLASS Item = link: Link; END Item;
                """);
        var source = basket("data", """
                <Item i:tid="item"><link><Link><peer i:ref="%s"/></Link></link></Item>
                """.formatted(OID));
        var unrelated = "4ec9c3b6-8ef7-4c96-a809-091cebd492b0";
        var closed = validateWithReferenceObject(plan, source, unrelated, TransferEvent.Consistency.COMPLETE);
        assertEquals(List.of(ValidationCode.REFERENCE_TARGET_MISSING), codes(closed));

        var open = validateWithReferenceObject(plan, source, unrelated, TransferEvent.Consistency.INCONSISTENT);
        assertEquals(0, open.detectedViolations(), open.examples().toString());
        assertTrue(open.coverage().missingRuntimeContext().stream()
                .anyMatch(rule -> rule.family() == ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY));
        var pending = open.coverage().constraints().getFirst();
        assertEquals(ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT, pending.status());
        assertEquals(1, pending.occurrences());

        var known = validateWithReferenceObject(plan, source, OID, TransferEvent.Consistency.INCONSISTENT);
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE), codes(known));
        var checked = known.coverage().constraints().getFirst();
        assertEquals(pending.constraint(), checked.constraint());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, checked.status());
        assertEquals(1, checked.occurrences());
    }

    private static ValidationReport validateWithReferenceObject(ValidationPlan plan, String source, String tid,
                                                               TransferEvent.Consistency consistency) {
        var token = CancellationToken.none();
        var support = basket("reference", "<Target i:tid='" + tid + "'><amount>3</amount></Target>");
        try (var storage = new InMemoryValidationStorage(1_000, 1_000_000);
             var run = DatasetValidator.session(plan, DatasetOptions.defaults(), storage);
             var primary = reader(plan, source);
             var decoded = reader(plan, support);
             var reference = new TransferReader(plan.transferPlan()) {
                 @Override
                 protected Optional<TransferEvent> readEvent(CancellationToken cancellation) {
                     // XTF 2.4 carries consistency on baskets. Set the object status at the neutral boundary.
                     return decoded.readNext(cancellation).map(event -> event instanceof TransferEvent.ObjectEvent object
                             ? new TransferEvent.ObjectEvent(object.object(), object.operation(), consistency) : event);
                 }

                 @Override
                 protected void release() { }
             }) {
            run.readInput(new InputDescriptor(new InputId("primary"), InputRole.PRIMARY), primary, token);
            run.readInput(new InputDescriptor(new InputId("support"), InputRole.REFERENCE), reference, token);
            return run.finish(token);
        }
    }

    private static List<ValidationCode> codes(ValidationReport report) {
        return report.examples().stream().map(ValidationDiagnostic::code).toList();
    }
}
