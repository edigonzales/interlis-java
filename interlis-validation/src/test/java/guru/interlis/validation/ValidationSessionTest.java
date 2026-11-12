package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.EndBasket;
import guru.interlis.transfer.TransferEvent.EndTransfer;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.transfer.TransferEvent.StartTransfer;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.IdOrigin;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferProtocolException;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.ready;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class ValidationSessionTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final SourceId SOURCE = new SourceId("memory:/checks.xtf");
    private final Fixture fixture = new Fixture();

    @Test
    void finishesOnlyAfterReaderEofAndLeavesReaderOpen() {
        try (var reader = new Reader(fixture.transfer, List.of(fixture.start(), new EndTransfer()))) {
            var report = LocalValidator.validate(fixture.plan, reader, ValidationOptions.defaults(), RUN);
            assertEquals(3, reader.reads);
            assertFalse(reader.released);
            assertEquals(0, report.inspectedBaskets());
            assertEquals(0, report.inspectedObjects());
            assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE, report.result());
            assertEquals(1, report.coverage().notChecked().size());
        }
    }

    @Test
    void propagatesFailuresAndRejectsPrematureSourceEnd() {
        try (var reader = new Reader(fixture.transfer, List.of(fixture.start(), new EndTransfer()))) {
            var failure = new IllegalStateException("trailing input");
            reader.failure = failure;
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> LocalValidator.validate(fixture.plan, reader, ValidationOptions.defaults(), RUN)));
            assertFalse(reader.released);
        }
        try (var reader = new Reader(fixture.transfer, List.of(fixture.start()))) {
            var failure = assertThrows(TransferProtocolException.class,
                    () -> LocalValidator.validate(fixture.plan, reader, ValidationOptions.defaults(), RUN));
            assertEquals(TransferProtocolException.Reason.INCOMPLETE_TRANSFER, failure.reason());
        }
        var basket = fixture.basket("one");
        List<TransferEvent> events = List.of(fixture.start(), basket, fixture.object(basket, 1, true),
                fixture.object(basket, 2, true), fixture.object(basket, 3, true));
        try (var reader = new Reader(fixture.transfer, events)) {
            var failure = new java.io.UncheckedIOException(new java.io.IOException("truncated source"));
            reader.failure = failure;
            assertSame(failure, assertThrows(java.io.UncheckedIOException.class,
                    () -> LocalValidator.validate(fixture.plan, reader, ValidationOptions.defaults(), RUN)));
            assertEquals(6, reader.reads);
            assertFalse(reader.released);
        }
    }

    @Test
    void capsExamplesAndKeepsCountsAndProvenance() {
        var basket = fixture.basket("one");
        var events = new ArrayList<TransferEvent>(List.of(fixture.start(), basket));
        for (int i = 1; i <= 10; i++) {
            events.add(fixture.object(basket, i, false));
        }
        events.add(new EndBasket(basket.id()));
        var empty = fixture.basket("empty");
        events.add(empty);
        events.add(new EndBasket(empty.id()));
        events.add(new EndTransfer());
        try (var reader = new Reader(fixture.transfer, events)) {
            var report = LocalValidator.validate(fixture.plan, reader, new ValidationOptions(2, 16, 50), RUN);
            assertEquals(ValidationReport.Result.FAILED_CHECKED_SCOPE, report.result());
            assertEquals(2, report.inspectedBaskets());
            assertEquals(10, report.inspectedObjects());
            assertEquals(10, report.detectedViolations());
            assertEquals(List.of("1", "2"), report.examples().stream()
                    .map(example -> example.data().tid().orElseThrow().value()).toList());
            var diagnostic = report.examples().getFirst();
            assertEquals(ValidationCode.REQUIRED_VALUE_MISSING, diagnostic.code());
            assertEquals(Optional.of(SOURCE), diagnostic.data().source());
            assertEquals(List.of(new DataLocation.Slot(fixture.code)), diagnostic.data().path());
            assertEquals(fixture.code, diagnostic.rule().declaration());
            assertTrue(diagnostic.rule().range().isPresent());
            assertEquals(10, report.coverage().checked().getFirst().occurrences());
            assertThrows(UnsupportedOperationException.class, () -> report.examples().clear());
        }
    }

    @Test
    void invalidSequencesAndEarlyFinishAreTerminal() {
        var basket = fixture.basket("one");
        for (var events : List.of(
                List.of(fixture.start(), fixture.start()),
                List.of(fixture.start(), fixture.object(basket, 1, true)),
                List.of(fixture.start(), new EndTransfer(), basket),
                List.of(fixture.start(), basket, new EndBasket(fixture.basket("two").id())))) {
            try (var session = LocalValidator.session(fixture.plan, ValidationOptions.defaults())) {
                events.subList(0, events.size() - 1).forEach(event -> session.accept(event, RUN));
                assertThrows(ValidationStreamException.class, () -> session.accept(events.getLast(), RUN));
                assertThrows(ValidationStreamException.class, session::finish);
            }
        }
        var session = LocalValidator.session(fixture.plan, ValidationOptions.defaults());
        session.accept(fixture.start(), RUN);
        assertThrows(ValidationStreamException.class, session::finish);
        assertThrows(ValidationStreamException.class, () -> session.accept(new EndTransfer(), RUN));
        session.close();
        session.close();
        assertThrows(ValidationStreamException.class, session::finish);
    }

    @Test
    void cancelsBeforeReadingOrDuringAnObjectAndEnforcesItsBudget() {
        try (var reader = new Reader(fixture.transfer, List.of(fixture.start()))) {
            assertThrows(OperationCancelledException.class,
                    () -> LocalValidator.validate(fixture.plan, reader, ValidationOptions.defaults(), () -> true));
            assertEquals(0, reader.reads);
        }
        var basket = fixture.basket("one");
        try (var session = LocalValidator.session(fixture.plan, ValidationOptions.defaults())) {
            session.accept(fixture.start(), RUN);
            session.accept(basket, RUN);
            var firstPoll = new AtomicBoolean(true);
            assertThrows(OperationCancelledException.class,
                    () -> session.accept(fixture.object(basket, 1, true), () -> !firstPoll.getAndSet(false)));
            assertThrows(ValidationStreamException.class, session::finish);
        }
        try (var session = LocalValidator.session(fixture.plan, new ValidationOptions(2, 2, 1))) {
            session.accept(fixture.start(), RUN);
            session.accept(basket, RUN);
            assertThrows(ValidationLimitException.class, () -> session.accept(fixture.object(basket, 1, true), RUN));
            assertThrows(ValidationStreamException.class, session::finish);
        }
    }

    @Test
    void rejectsDeepNeutralValuesBeforePublishingAReport() {
        var basket = fixture.basket("one");
        var object = fixture.object(basket, 1, true).object();
        TransferValue value = new TransferValue.Text("ok");
        for (int i = 0; i < 20; i++) {
            value = new TransferValue.Repeated(CollectionKind.LIST, List.of(value));
        }
        var deep = new TransferObject(object.identity(),
                new TransferValue.Structured(object.type(), Map.of(fixture.code, value)));
        try (var session = LocalValidator.session(fixture.plan, new ValidationOptions(2, 3, 100))) {
            session.accept(fixture.start(), RUN);
            session.accept(basket, RUN);
            assertThrows(ValidationLimitException.class,
                    () -> session.accept(new ObjectEvent(deep, Optional.empty(),
                            TransferEvent.Consistency.COMPLETE), RUN));
            assertThrows(ValidationStreamException.class, session::finish);
        }
    }

    @Test
    void sharedPlanKeepsSessionsIndependent() {
        var one = fixture.basket("one");
        var two = fixture.basket("two");
        try (var first = LocalValidator.session(fixture.plan, ValidationOptions.defaults());
             var second = LocalValidator.session(fixture.plan, ValidationOptions.defaults())) {
            first.accept(fixture.start(), RUN);
            first.accept(one, RUN);
            second.accept(fixture.start(), RUN);
            second.accept(two, RUN);
            first.accept(fixture.object(one, 1, false), RUN);
            second.accept(fixture.object(two, 2, true), RUN);
            first.accept(new EndBasket(one.id()), RUN);
            second.accept(new EndBasket(two.id()), RUN);
            first.accept(new EndTransfer(), RUN);
            second.accept(new EndTransfer(), RUN);
            assertEquals(ValidationReport.Result.FAILED_CHECKED_SCOPE, first.finish().result());
            assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE, second.finish().result());
            assertThrows(ValidationStreamException.class, second::finish);
        }
    }

    private static final class Fixture {
        private final guru.interlis.model.CompiledSchema schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC T =
                    CLASS Item =
                      code : MANDATORY TEXT*20;
                      comment : TEXT;
                      UNIQUE code;
                    END Item;
                  END T;
                END Checks.
                """);
        private final TransferPlan transfer = transfer(schema);
        private final ValidationPlan plan = ready(schema, transfer);
        private final SymbolRef model = ref("Checks");
        private final SymbolRef topic = ref("Checks", "T");
        private final SymbolRef item = ref("Checks", "T", "Item");
        private final SymbolRef code = ref("Checks", "T", "Item", "code");

        private SymbolRef ref(String... path) {
            return schema.queries().findQualifiedName(QualifiedName.of(path)).getFirst();
        }

        private StartTransfer start() {
            return new StartTransfer(Optional.of(SOURCE), Optional.empty(), Optional.empty(),
                    List.of(new TransferEvent.ModelDeclaration(model, LanguageVersion.ILI24,
                            Optional.empty(), Optional.empty())));
        }

        private StartBasket basket(String id) {
            return new StartBasket(new BasketId(id, IdOrigin.SOURCE), topic, List.of(), TransferEvent.BasketKind.FULL,
                    TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        }

        private ObjectEvent object(StartBasket basket, int id, boolean withCode) {
            var identity = new TransferIdentity(basket.id(), Optional.empty(),
                    Optional.of(new TransferId(Integer.toString(id), IdOrigin.SOURCE)), Optional.empty(), Optional.empty());
            var data = new TransferValue.Structured(item, withCode ? Map.of(code, new TransferValue.Text("ok")) : Map.of());
            return new ObjectEvent(new TransferObject(identity, data), Optional.empty(), TransferEvent.Consistency.COMPLETE);
        }
    }

    private static final class Reader extends TransferReader {
        private final List<TransferEvent> events;
        private int reads;
        private boolean released;
        private RuntimeException failure;

        Reader(TransferPlan plan, List<TransferEvent> events) {
            super(plan);
            this.events = List.copyOf(events);
        }

        @Override
        protected Optional<TransferEvent> readEvent(CancellationToken cancellation) {
            int next = reads++;
            if (next < events.size()) {
                return Optional.of(events.get(next));
            }
            if (failure != null) {
                throw failure;
            }
            return Optional.empty();
        }

        @Override
        protected void release() {
            released = true;
        }
    }
}
