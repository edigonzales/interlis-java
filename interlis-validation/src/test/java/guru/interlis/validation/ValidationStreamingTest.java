package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidationStreamingTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final int OBJECTS = 100_000;

    @Test
    void countsLargeStreamWithoutRetainingOwnersAndBoundsTheExamples() {
        var f = new Fixture();
        var examples = 3;
        try (var session = f.open(new ValidationOptions(examples, 128, 1_000))) {
            for (int i = 1; i <= OBJECTS; i++) {
                session.accept(f.object(i, i % 1_000 == 0), RUN);
            }
            var report = f.finish(session);
            assertEquals(ValidationReport.Result.FAILED_CHECKED_SCOPE, report.result());
            assertEquals(1, report.inspectedBaskets());
            assertEquals(OBJECTS, report.inspectedObjects());
            assertEquals(100, report.detectedViolations());
            assertEquals(examples, report.examples().size());
            assertEquals(List.of("1000", "2000", "3000"),
                    report.examples().stream().map(d -> d.data().tid().orElseThrow().value()).toList());
            assertTrue(report.examples().stream().allMatch(d -> d.code() == ValidationCode.LOCAL_UNIQUE_DUPLICATE));
            var count = report.coverage().constraints().getFirst();
            assertEquals(1, report.coverage().constraints().size());
            assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, count.status());
            assertEquals(OBJECTS, count.occurrences());
            assertTrue(report.coverage().notChecked().isEmpty());
            System.out.printf("Validation stream: objects=%d violations=%d examples=%d localChecks=%d maxHeapBytes=%d%n",
                    report.inspectedObjects(), report.detectedViolations(), report.examples().size(), count.occurrences(),
                    Runtime.getRuntime().maxMemory());
        }
        try (var session = f.open(ValidationOptions.defaults())) {
            session.accept(f.object(1, false), RUN);
            var report = f.finish(session);
            assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE, report.result());
            assertEquals(1, report.coverage().constraints().getFirst().occurrences());
        }
    }

    @Test
    void appliesDepthLimitToAValidNestedObjectAndDiscardsInterruptedRuns() {
        var f = new Fixture();
        try (var session = f.open(new ValidationOptions(3, 4, 1_000))) {
            session.accept(f.object(1, false), RUN);
            assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE, f.finish(session).result());
        }
        try (var session = f.open(new ValidationOptions(3, 3, 1_000))) {
            var limit = assertThrows(ValidationLimitException.class, () -> session.accept(f.object(1, false), RUN));
            assertEquals("Maximum value depth exceeded", limit.getMessage());
            assertThrows(ValidationStreamException.class, session::finish);
        }
        try (var session = f.open(ValidationOptions.defaults())) {
            session.accept(f.object(1, true), RUN);
            assertThrows(OperationCancelledException.class, () -> session.accept(f.object(2, false), () -> true));
            assertThrows(ValidationStreamException.class, session::finish);
        }
        try (var session = f.open(ValidationOptions.defaults())) {
            session.accept(f.object(1, true), RUN);
            var other = new TransferIdentity.BasketId("other", TransferIdentity.IdOrigin.SOURCE);
            assertThrows(ValidationStreamException.class, () -> session.accept(new TransferEvent.EndBasket(other), RUN));
            assertThrows(ValidationStreamException.class, session::finish);
        }
        try (var session = f.open(ValidationOptions.defaults())) {
            session.accept(f.object(1, false), RUN);
            var report = f.finish(session);
            assertEquals(1, report.inspectedObjects());
            assertEquals(0, report.detectedViolations());
        }
    }

    private static final class Fixture {
        private final ValidationPlan plan;
        private final SymbolRef topic;
        private final SymbolRef item;
        private final SymbolRef groups;
        private final SymbolRef group;
        private final SymbolRef details;
        private final SymbolRef detail;
        private final SymbolRef key;
        private final TransferIdentity.BasketId basket = new TransferIdentity.BasketId("b1", TransferIdentity.IdOrigin.SOURCE);

        Fixture() {
            var schema = compile("""
                    INTERLIS 2.4;
                    MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                      TOPIC Data =
                        STRUCTURE Detail = key : TEXT*8; END Detail;
                        STRUCTURE Group = details : BAG {0..*} OF Detail; END Group;
                        CLASS Item =
                          groups : LIST {0..*} OF Group;
                          UNIQUE (LOCAL) groups->details: key;
                        END Item;
                      END Data;
                    END Checks.
                    """);
            plan = ready(schema, transfer(schema));
            topic = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data")).getFirst();
            item = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Item")).getFirst();
            groups = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Item", "groups")).getFirst();
            group = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Group")).getFirst();
            details = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Group", "details")).getFirst();
            detail = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Detail")).getFirst();
            key = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Detail", "key")).getFirst();
        }

        private TransferValue.Repeated groups(int i, boolean duplicates) {
            var value = Integer.toString(i % 97);
            var a = new TransferValue.Structured(detail, Map.of(key, new TransferValue.Text(value)));
            var b = new TransferValue.Structured(detail, Map.of(key, new TransferValue.Text(duplicates ? value : "b" + value)));
            return new TransferValue.Repeated(CollectionKind.LIST, List.of(new TransferValue.Structured(group,
                    Map.of(details, new TransferValue.Repeated(CollectionKind.BAG, List.of(a, b))))));
        }

        TransferEvent.ObjectEvent object(int i, boolean duplicates) {
            var identity = new TransferIdentity(basket, Optional.empty(),
                    Optional.of(new TransferIdentity.TransferId(Integer.toString(i), TransferIdentity.IdOrigin.SOURCE)),
                    Optional.empty(), Optional.empty());
            var object = new TransferObject(identity, new TransferValue.Structured(item, Map.of(groups, groups(i, duplicates))));
            return new TransferEvent.ObjectEvent(object, Optional.empty(), TransferEvent.Consistency.COMPLETE);
        }

        ValidationSession open(ValidationOptions options) {
            var session = LocalValidator.session(plan, options);
            session.accept(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of()), RUN);
            session.accept(new TransferEvent.StartBasket(basket, topic, List.of(), TransferEvent.BasketKind.FULL,
                    TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of()), RUN);
            return session;
        }

        ValidationReport finish(ValidationSession session) {
            session.accept(new TransferEvent.EndBasket(basket), RUN);
            session.accept(new TransferEvent.EndTransfer(), RUN);
            return session.finish();
        }
    }
}
