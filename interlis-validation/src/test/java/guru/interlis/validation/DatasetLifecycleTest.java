package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.QualifiedName;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetLifecycleTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
              TOPIC Data =
                CLASS Item = code : MANDATORY TEXT*3; UNIQUE code; END Item;
              END Data;
            END Checks.
            """;

    @Test
    void preservesSeparateInputsAndValidatesOnlyPrimarySubjects() {
        var f = new Fixture();
        for (boolean referenceFirst : List.of(true, false)) {
            try (var store = new InMemoryValidationStorage(100, 100_000);
                 var run = DatasetValidator.session(f.plan, DatasetOptions.defaults(f.plan), store);
                 var a = f.reader(true, false, null);
                 var b = f.reader(true, true, null)) {
                if (referenceFirst) {
                    run.readInput(input("reference", InputRole.REFERENCE), b, RUN);
                }
                run.readInput(input("primary", InputRole.PRIMARY), a, RUN);
                if (!referenceFirst) {
                    run.readInput(input("reference", InputRole.REFERENCE), b, RUN);
                }
                var report = run.finish(RUN);
                assertEquals(1, report.inspectedObjects());
                assertEquals(0, report.detectedViolations());
                assertEquals(DatasetRuleCoverage.Outcome.UNDETERMINED,
                        report.coverage().datasetRules().getFirst().outcome());
                assertFalse(a.closed);
                assertFalse(b.closed);
                try (var cursor = store.table(StorageArea.OBJECTS).scan(new byte[0], Optional.empty())) {
                    var codec = new StoredValueCodec(f.plan.snapshotId(), 100_000);
                    var first = codec.decodeObject(cursor.next().orElseThrow().value());
                    var second = codec.decodeObject(cursor.next().orElseThrow().value());
                    assertNotEquals(first.input(), second.input());
                    assertEquals(first.event().object().identity().tid(), second.event().object().identity().tid());
                    assertTrue(cursor.next().isEmpty());
                }
                assertThrows(ValidationStreamException.class, () -> run.finish(RUN));
            }
        }
    }

    @Test
    void acceptsEmptyPrimaryButRejectsFailureBeforeSecondInputEof() {
        var f = new Fixture();
        try (var store = new InMemoryValidationStorage(100, 100_000);
             var run = DatasetValidator.session(f.plan, DatasetOptions.defaults(f.plan), store);
             var empty = f.reader(false, false, null)) {
            run.readInput(input("empty", InputRole.PRIMARY), empty, RUN);
            assertEquals(0, run.finish(RUN).inspectedObjects());
        }
        var failure = new IllegalStateException("source failed after EndTransfer");
        try (var store = new InMemoryValidationStorage(100, 100_000);
             var run = DatasetValidator.session(f.plan, DatasetOptions.defaults(f.plan), store);
             var first = f.reader(true, false, null);
             var second = f.reader(false, false, failure)) {
            run.readInput(input("first", InputRole.PRIMARY), first, RUN);
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> run.readInput(input("second", InputRole.REFERENCE), second, RUN)));
            assertThrows(ValidationStreamException.class, () -> run.finish(RUN));
            assertFalse(second.closed);
            assertThrows(IllegalArgumentException.class,
                    () -> DatasetValidator.session(f.plan, DatasetOptions.defaults(f.plan), store));
        }
    }

    @Test
    void rejectsForeignEmptyHeadersAndCancellationAtSeal() {
        var f = new Fixture();
        var foreign = new Fixture(MODEL.replace("TEXT*3", "TEXT*4"));
        try (var store = new InMemoryValidationStorage(100, 100_000);
             var run = DatasetValidator.session(f.plan, DatasetOptions.defaults(f.plan), store);
             var empty = foreign.reader(false, false, null)) {
            assertThrows(ValidationStreamException.class,
                    () -> run.readInput(input("foreign", InputRole.PRIMARY), empty, RUN));
            assertThrows(ValidationStreamException.class, () -> run.finish(RUN));
        }
        try (var store = new InMemoryValidationStorage(100, 100_000);
             var run = DatasetValidator.session(f.plan, DatasetOptions.defaults(f.plan), store);
             var empty = f.reader(false, false, null)) {
            run.readInput(input("empty", InputRole.PRIMARY), empty, RUN);
            assertThrows(OperationCancelledException.class, () -> run.finish(() -> true));
            assertThrows(ValidationStreamException.class, () -> run.finish(RUN));
        }
    }

    @Test
    void finishRejectsOverBudgetClaimsAndBasketsOutsideTheInput() {
        var f = new Fixture();
        var topic = SubjectSelection.fromRoots(f.plan).roots().getFirst();
        var id = new InputId("primary");
        var bid = new TransferIdentity.BasketId("b", TransferIdentity.IdOrigin.SOURCE);
        var claim = new ExtentDeclaration(new ExtentSelector(topic, java.util.Set.of(id), Optional.of(bid)),
                ExtentAspect.OBJECT_MEMBERSHIP, "inventory");
        var subjects = SubjectSelection.fromRoots(f.plan);
        var overBudget = new DatasetOptions(ValidationOptions.defaults(), subjects,
                new ExtentDeclarations(List.of(claim, claim)), new DatasetLimits(1, 1, 1, 4096, 1));
        try (var store = new InMemoryValidationStorage(100, 100_000);
             var run = DatasetValidator.session(f.plan, overBudget, store);
             var reader = f.reader(true, false, null)) {
            run.readInput(new InputDescriptor(id, InputRole.PRIMARY), reader, RUN);
            assertThrows(ValidationLimitException.class, () -> run.finish(RUN));
            assertThrows(ValidationStreamException.class, () -> run.finish(RUN));
        }
        var missingBasket = new DatasetOptions(ValidationOptions.defaults(), subjects,
                new ExtentDeclarations(List.of(claim)), DatasetLimits.defaults());
        try (var store = new InMemoryValidationStorage(100, 100_000);
             var run = DatasetValidator.session(f.plan, missingBasket, store);
             var reader = f.reader(false, false, null)) {
            run.readInput(new InputDescriptor(id, InputRole.PRIMARY), reader, RUN);
            assertThrows(ValidationStreamException.class, () -> run.finish(RUN));
        }
    }

    private static InputDescriptor input(String id, InputRole role) {
        return new InputDescriptor(new InputId(id), role);
    }

    private static final class Fixture {
        private final guru.interlis.model.CompiledSchema schema;
        private final TransferPlan transfer;
        private final ValidationPlan plan;

        Fixture() {
            this(MODEL);
        }

        Fixture(String model) {
            schema = compile(model);
            transfer = transfer(schema);
            plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                    ValidationPlanner.plan(schema, transfer, ValidationProfile.DATASET)).plan();
        }

        Reader reader(boolean object, boolean invalid, RuntimeException failure) {
            var model = schema.rootModels().getFirst();
            var q = schema.queries();
            var topic = q.findQualifiedName(QualifiedName.of("Checks", "Data")).getFirst();
            var item = q.findQualifiedName(QualifiedName.of("Checks", "Data", "Item")).getFirst();
            var code = q.findQualifiedName(QualifiedName.of("Checks", "Data", "Item", "code")).getFirst();
            var events = new ArrayList<TransferEvent>();
            events.add(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of(
                    new TransferEvent.ModelDeclaration(model, LanguageVersion.ILI24, Optional.empty(), Optional.empty()))));
            if (object) {
                var bid = new TransferIdentity.BasketId("b", TransferIdentity.IdOrigin.SOURCE);
                var tid = new TransferIdentity.TransferId("same", TransferIdentity.IdOrigin.SOURCE);
                events.add(new TransferEvent.StartBasket(bid, topic, List.of(), TransferEvent.BasketKind.FULL,
                        TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of()));
                var id = new TransferIdentity(bid, Optional.empty(), Optional.of(tid), Optional.empty(), Optional.empty());
                var value = new TransferValue.Structured(item, Map.of(code, new TransferValue.Text(invalid ? "long" : "ok")));
                events.add(new TransferEvent.ObjectEvent(new TransferObject(id, value), Optional.empty(),
                        TransferEvent.Consistency.COMPLETE));
                events.add(new TransferEvent.EndBasket(bid));
            }
            events.add(new TransferEvent.EndTransfer());
            return new Reader(transfer, events, failure);
        }
    }

    private static final class Reader extends TransferReader {
        private final java.util.Iterator<TransferEvent> events;
        private final RuntimeException failure;
        private boolean closed;

        Reader(TransferPlan plan, List<TransferEvent> events, RuntimeException failure) {
            super(plan);
            this.events = events.iterator();
            this.failure = failure;
        }

        @Override
        protected Optional<TransferEvent> readEvent(CancellationToken cancellation) {
            if (events.hasNext()) {
                return Optional.of(events.next());
            }
            if (failure != null) {
                throw failure;
            }
            return Optional.empty();
        }

        @Override
        protected void release() {
            closed = true;
        }
    }
}
