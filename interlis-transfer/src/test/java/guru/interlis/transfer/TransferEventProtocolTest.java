package guru.interlis.transfer;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TopicSymbol.BindingState;
import guru.interlis.model.TopicSymbol.GenericBinding;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferEvent.BasketKind;
import guru.interlis.transfer.TransferEvent.DomainBinding;
import guru.interlis.transfer.TransferEvent.EndBasket;
import guru.interlis.transfer.TransferEvent.EndTransfer;
import guru.interlis.transfer.TransferEvent.ModelDeclaration;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.transfer.TransferEvent.StartTransfer;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.ObjectOid;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferStreamingContractTest.MemoryReader;
import guru.interlis.transfer.TransferStreamingContractTest.MemoryWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static guru.interlis.transfer.TransferEvent.BasketKind.FULL;
import static guru.interlis.transfer.TransferEvent.BasketKind.INITIAL;
import static guru.interlis.transfer.TransferEvent.BasketKind.UPDATE;
import static guru.interlis.transfer.TransferEvent.Consistency.ADAPTED;
import static guru.interlis.transfer.TransferEvent.Consistency.COMPLETE;
import static guru.interlis.transfer.TransferIdentity.IdOrigin.SOURCE;
import static guru.interlis.transfer.TransferIdentity.IdOrigin.SYNTHETIC;
import static guru.interlis.transfer.TransferProtocolException.Reason.CLOSED;
import static guru.interlis.transfer.TransferProtocolException.Reason.CONTEXT_MISMATCH;
import static guru.interlis.transfer.TransferProtocolException.Reason.DUPLICATE_ID;
import static guru.interlis.transfer.TransferProtocolException.Reason.FAILED;
import static guru.interlis.transfer.TransferProtocolException.Reason.FOREIGN_SNAPSHOT;
import static guru.interlis.transfer.TransferProtocolException.Reason.INCOMPLETE_TRANSFER;
import static guru.interlis.transfer.TransferProtocolException.Reason.UNEXPECTED_EVENT;
import static guru.interlis.transfer.TransferStreamingContractTest.ready;
import static guru.interlis.transfer.TransferTestSchemas.compile;
import static guru.interlis.transfer.TransferTestSchemas.compileInline;
import static guru.interlis.transfer.TransferTestSchemas.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferEventProtocolTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String MODEL = "ProtocolFixture";
    private static final String MODEL_TEXT = """
            INTERLIS 2.4;
            MODEL ProtocolFixture AT "https://example.org" VERSION "1" =
              TOPIC Data = CLASS Entry = END Entry; END Data;
            END ProtocolFixture.
            """;
    private static final BasketId BASKET = new BasketId("basket", SOURCE);
    private static CompiledSchema schema;
    private static TransferPlan plan;

    @BeforeAll
    static void preparePlan() {
        schema = compileInline(MODEL, MODEL_TEXT);
        plan = ready(schema);
    }

    @Test
    void rejectsObjectsBeforeABasketAndLetsAWriterRetryRejectedEvents() {
        var start = startTransfer();
        var basket = startBasket();
        var wrongBasket = new BasketId("wrong-basket", SOURCE);
        var object = object(BASKET);
        var writer = new MemoryWriter(plan);
        try (writer) {
            expect(UNEXPECTED_EVENT, () -> writer.write(object));
            writer.write(start);
            expect(UNEXPECTED_EVENT, () -> writer.write(object));
            writer.write(basket);
            expect(CONTEXT_MISMATCH, () -> writer.write(object(wrongBasket)));
            expect(CONTEXT_MISMATCH, () -> writer.write(new EndBasket(wrongBasket)));
            writer.write(object);
            writer.write(new EndBasket(BASKET));
            writer.write(new EndTransfer());
            expect(UNEXPECTED_EVENT, () -> writer.write(new EndTransfer()));
        }
        assertEquals(List.of(start, basket, object, new EndBasket(BASKET), new EndTransfer()), writer.events);

        try (var reader = new MemoryReader(plan, List.<TransferEvent>of(object).iterator())) {
            expect(UNEXPECTED_EVENT, () -> reader.readNext(TOKEN));
        }
    }

    @Test
    void failsAnIncompleteReadAndFreesAnIncompleteWriterExactlyOnce() {
        var start = startTransfer();
        var basket = startBasket();
        try (var reader = new MemoryReader(plan, List.<TransferEvent>of(start, basket).iterator())) {
            assertEquals(Optional.of(start), reader.readNext(TOKEN));
            assertEquals(Optional.of(basket), reader.readNext(TOKEN));
            expect(INCOMPLETE_TRANSFER, () -> reader.readNext(TOKEN));
        }
        var writer = new MemoryWriter(plan);
        writer.write(start);
        writer.write(basket);
        expect(INCOMPLETE_TRANSFER, writer::close);
        writer.close();
        assertEquals(1, writer.releases);
        expect(CLOSED, () -> writer.write(new EndBasket(BASKET)));
    }

    @Test
    void canAbandonAnActiveReaderWithoutReadingThroughTheEnd() {
        var start = startTransfer();
        var basket = startBasket();
        var reader = new MemoryReader(plan,
                List.<TransferEvent>of(start, basket, new EndBasket(BASKET), new EndTransfer()).iterator());
        assertEquals(Optional.of(start), reader.readNext(TOKEN));
        assertEquals(Optional.of(basket), reader.readNext(TOKEN));

        reader.close();
        reader.close();

        assertEquals(2, reader.pulls);
        assertEquals(1, reader.releases);
        expect(CLOSED, () -> reader.readNext(TOKEN));
    }

    @Test
    void detectsAnExtraEndAfterEmittingEndTransfer() {
        var start = startTransfer();
        var end = new EndTransfer();
        try (var reader = new MemoryReader(plan, List.<TransferEvent>of(start, end, end).iterator())) {
            assertEquals(Optional.of(start), reader.readNext(TOKEN));
            assertEquals(Optional.of(end), reader.readNext(TOKEN));
            expect(UNEXPECTED_EVENT, () -> reader.readNext(TOKEN));
        }
    }

    @Test
    void preCancellationDoesNotConsumeAndRepeatedCloseReleasesOnlyOnce() {
        var start = startTransfer();
        var end = new EndTransfer();
        var cancelled = CancellationToken.requested(CancellationReason.SUPERSEDED);
        var reader = new MemoryReader(plan, List.<TransferEvent>of(start, end).iterator());
        try (reader) {
            var cancellation = assertThrows(OperationCancelledException.class, () -> reader.readNext(cancelled));
            assertEquals(CancellationReason.SUPERSEDED, cancellation.reason());
            assertEquals(0, reader.pulls);
            assertEquals(Optional.of(start), reader.readNext(TOKEN));
            assertEquals(Optional.of(end), reader.readNext(TOKEN));
            assertEquals(Optional.empty(), reader.readNext(TOKEN));
        }
        reader.close();
        assertEquals(1, reader.releases);
        expect(CLOSED, () -> reader.readNext(TOKEN));

        var writer = new MemoryWriter(plan);
        try (writer) {
            var cancellation = assertThrows(OperationCancelledException.class, () -> writer.write(start, cancelled));
            assertEquals(CancellationReason.SUPERSEDED, cancellation.reason());
            assertEquals(List.of(), writer.events);
            writer.write(start);
            writer.write(end);
        }
        writer.close();
        assertEquals(List.of(start, end), writer.events);
        assertEquals(1, writer.releases);
        expect(CLOSED, () -> writer.write(end));
    }

    @Test
    void cancellationDuringAReadAndAdapterWriteFailureLeaveTheStreamFailed() {
        var cancelled = new AtomicBoolean();
        var readReleases = new AtomicInteger();
        var start = startTransfer();
        var reader = new TransferReader(plan) {
            @Override
            protected Optional<TransferEvent> readEvent(CancellationToken cancellation) {
                cancelled.set(true);
                return Optional.of(start);
            }

            @Override
            protected void release() {
                readReleases.incrementAndGet();
            }
        };
        try (reader) {
            assertThrows(OperationCancelledException.class, () -> reader.readNext(cancelled::get));
            expect(FAILED, () -> reader.readNext(TOKEN));
        }
        assertEquals(1, readReleases.get());

        var failure = new IllegalStateException("destination failed after a partial write");
        var writeReleases = new AtomicInteger();
        var writer = new TransferWriter(plan) {
            @Override
            protected void writeEvent(TransferEvent event, CancellationToken cancellation) {
                throw failure;
            }

            @Override
            protected void release() {
                writeReleases.incrementAndGet();
            }
        };
        try (writer) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> writer.write(start)));
            expect(FAILED, () -> writer.write(start));
        }
        assertEquals(1, writeReleases.get());
    }

    @Test
    void rejectsForeignModelAndTopicReferencesDespiteEqualSymbolIds() {
        var foreign = compile(MODEL, new SourceText(new SourceId("memory:/" + MODEL + ".ili"),
                new SourceRevision(2), MODEL_TEXT));
        var model = new SymbolRef(foreign.snapshotId(), ref(schema, MODEL).symbol());
        var topic = new SymbolRef(foreign.snapshotId(), startBasket().topic().symbol());
        var start = startTransfer();
        var wrongModel = new StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(new ModelDeclaration(model, LanguageVersion.ILI24, Optional.empty(), Optional.empty())));
        var wrongBasket = new StartBasket(BASKET, topic, List.of(), FULL, COMPLETE,
                Optional.empty(), Optional.empty(), Optional.empty(), List.of());

        var writer = new MemoryWriter(plan);
        try (writer) {
            expect(FOREIGN_SNAPSHOT, () -> writer.write(wrongModel));
            writer.write(start);
            expect(FOREIGN_SNAPSHOT, () -> writer.write(wrongBasket));
            writer.write(new EndTransfer());
        }
        assertEquals(List.of(start, new EndTransfer()), writer.events);
        try (var reader = new MemoryReader(plan, List.<TransferEvent>of(start, wrongBasket).iterator())) {
            assertEquals(Optional.of(start), reader.readNext(TOKEN));
            expect(FOREIGN_SNAPSHOT, () -> reader.readNext(TOKEN));
        }
    }

    @Test
    void requiresTheBasketModelInTheHeaderAndChecksTheSuppliedVersion() {
        var emptyHeader = new StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        var incorrectVersion = new StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of(
                new ModelDeclaration(ref(schema, MODEL), LanguageVersion.ILI24, Optional.of("wrong"),
                        Optional.empty())));
        var writer = new MemoryWriter(plan);
        try (writer) {
            expect(CONTEXT_MISMATCH, () -> writer.write(incorrectVersion));
            writer.write(emptyHeader);
            expect(CONTEXT_MISMATCH, () -> writer.write(startBasket()));
            writer.write(new EndTransfer());
        }
        assertEquals(List.of(emptyHeader, new EndTransfer()), writer.events);
        try (var reader = new MemoryReader(plan,
                List.<TransferEvent>of(emptyHeader, startBasket()).iterator())) {
            assertEquals(Optional.of(emptyHeader), reader.readNext(TOKEN));
            expect(CONTEXT_MISMATCH, () -> reader.readNext(TOKEN));
        }
    }

    @ParameterizedTest
    @EnumSource(LanguageVersion.class)
    void checksBasketAndFullObjectIdsAcrossBasketsInSeparateNamespaces(LanguageVersion language) {
        var fixture = compileInline(MODEL, MODEL_TEXT.replace("INTERLIS 2.4;", "INTERLIS " + language.literal() + ";"));
        var fixturePlan = ready(fixture);
        var second = new BasketId("second", SOURCE);
        var duplicateBid = new BasketId(BASKET.value(), SYNTHETIC);
        var firstTid = new TransferId(second.value(), SYNTHETIC);
        var duplicateTid = new TransferId(firstTid.value(), SOURCE);
        var secondTid = new TransferId(BASKET.value(), SOURCE);
        var firstObject = object(fixture, BASKET, Optional.of(firstTid), Optional.empty());
        var secondObject = object(fixture, second, Optional.of(secondTid), Optional.empty());
        var start = startTransfer(fixture, language);
        var firstBasket = startBasket(fixture, BASKET, FULL);
        var secondBasket = startBasket(fixture, second, FULL);
        List<TransferEvent> expected = List.of(start, firstBasket, firstObject, new EndBasket(BASKET),
                secondBasket, secondObject, new EndBasket(second), new EndTransfer());
        var writer = new MemoryWriter(fixturePlan);
        try (writer) {
            writer.write(start);
            writer.write(firstBasket);
            writer.write(firstObject);
            writer.write(new EndBasket(BASKET));
            expect(DUPLICATE_ID, () -> writer.write(startBasket(fixture, duplicateBid, FULL)));
            writer.write(secondBasket);
            expect(DUPLICATE_ID, () -> writer.write(object(fixture, second, Optional.of(duplicateTid), Optional.empty())));
            writer.write(secondObject);
            writer.write(new EndBasket(second));
            writer.write(new EndTransfer());
        }
        assertEquals(expected, writer.events);
        assertReads(fixturePlan, writer.events);

        var independentWriter = new MemoryWriter(fixturePlan);
        try (independentWriter) {
            expected.forEach(independentWriter::write);
        }
        assertEquals(expected, independentWriter.events);
        assertReads(fixturePlan, independentWriter.events);
    }

    @Test
    void tracksBasketIdsAcrossAllKindsAndKeepsIncrementalKeysOutsideTheFullTidScope() {
        var initial = new BasketId("initial", SOURCE);
        var update = new BasketId("update", SOURCE);
        var last = new BasketId("last", SOURCE);
        var fullTid = new TransferId("full-id", SOURCE);
        var incrementalTid = new TransferId("incremental-id", SOURCE);
        var stableOid = new ObjectOid(fullTid.value());
        var firstObject = object(schema, BASKET, Optional.of(fullTid), Optional.empty());
        var initialObject = new ObjectEvent(objectData(schema, initial, Optional.of(incrementalTid),
                Optional.of(stableOid)), Optional.of(TransferEvent.Operation.INSERT), COMPLETE);
        var updatedObject = new ObjectEvent(objectData(schema, update, Optional.of(incrementalTid),
                Optional.of(stableOid)), Optional.of(TransferEvent.Operation.UPDATE), COMPLETE);
        var lastObject = object(schema, last, Optional.of(incrementalTid), Optional.empty());
        var start = startTransfer();
        var fullBasket = startBasket();
        var initialBasket = startBasket(schema, initial, INITIAL);
        var updatedBasket = startBasket(schema, update, UPDATE);
        var lastBasket = startBasket(schema, last, FULL);
        var writer = new MemoryWriter(plan);
        try (writer) {
            writer.write(start);
            writer.write(fullBasket);
            writer.write(firstObject);
            writer.write(new EndBasket(BASKET));
            expect(DUPLICATE_ID, () -> writer.write(startBasket(schema, BASKET, INITIAL)));
            writer.write(initialBasket);
            writer.write(initialObject);
            writer.write(new EndBasket(initial));
            expect(DUPLICATE_ID, () -> writer.write(startBasket(schema, initial, UPDATE)));
            writer.write(updatedBasket);
            writer.write(updatedObject);
            writer.write(new EndBasket(update));
            expect(DUPLICATE_ID, () -> writer.write(startBasket(schema, update, FULL)));
            writer.write(lastBasket);
            expect(DUPLICATE_ID, () -> writer.write(object(schema, last, Optional.of(fullTid), Optional.empty())));
            writer.write(lastObject);
            writer.write(new EndBasket(last));
            writer.write(new EndTransfer());
        }
        List<TransferEvent> expected = List.of(start, fullBasket, firstObject, new EndBasket(BASKET),
                initialBasket, initialObject,
                new EndBasket(initial), updatedBasket, updatedObject, new EndBasket(update), lastBasket, lastObject,
                new EndBasket(last), new EndTransfer());
        assertEquals(expected, writer.events);
        assertReads(plan, writer.events);
    }

    @Test
    void preservesIncrementalStatesConsistencyAndRuntimeDomainAssignments() {
        var generic = compileInline("GenericExample", """
                INTERLIS 2.4;
                MODEL GenericExample AT "https://example.org" VERSION "1" =
                  DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                         A = COORD 0..100,0..100; B = COORD 200..300,200..300;
                         Other = COORD 400..500,400..500;
                  CONTEXT Default = G = A OR B;
                  TOPIC Data = DEFERRED GENERICS G; CLASS Entry = point: G; END Entry; END Data;
                END GenericExample.
                """);
        var genericPlan = ready(generic);
        var start = new StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of(
                new ModelDeclaration(ref(generic, "GenericExample"), LanguageVersion.ILI24,
                        Optional.of("1"), Optional.empty())));
        var genericDomain = ref(generic, "GenericExample", "G");
        var concreteDomain = ref(generic, "GenericExample", "B");
        var assignment = new DomainBinding(genericDomain, concreteDomain);
        var assignments = new ArrayList<>(List.of(assignment));
        var basket = updatedBasket(generic, assignments);
        assignments.clear();
        assertEquals(List.of(assignment), basket.domainBindings());
        var identity = new TransferIdentity(BASKET, Optional.empty(), Optional.empty(),
                Optional.of(new TransferIdentity.ObjectOid("entry-oid")), Optional.empty());
        var entry = new TransferObject(identity, new TransferValue.Structured(
                ref(generic, "GenericExample", "Data", "Entry"), Map.of()));
        var update = new ObjectEvent(entry, Optional.of(TransferEvent.Operation.UPDATE), ADAPTED);
        List<TransferEvent> expected = List.of(start, basket, update, new EndBasket(BASKET), new EndTransfer());
        var writer = new MemoryWriter(genericPlan);
        try (writer) {
            writer.write(start);
            var reversed = new DomainBinding(concreteDomain, genericDomain);
            var invalidAlternative = new DomainBinding(genericDomain, ref(generic, "GenericExample", "Other"));
            expect(CONTEXT_MISMATCH, () -> writer.write(updatedBasket(generic, List.of())));
            expect(CONTEXT_MISMATCH, () -> writer.write(updatedBasket(generic, List.of(reversed))));
            expect(CONTEXT_MISMATCH, () -> writer.write(updatedBasket(generic, List.of(invalidAlternative))));
            expect(CONTEXT_MISMATCH, () -> writer.write(updatedBasket(generic, List.of(assignment, assignment))));
            expected.subList(1, expected.size()).forEach(writer::write);
        }
        try (var reader = new MemoryReader(genericPlan, writer.events.iterator())) {
            for (TransferEvent event : expected) {
                assertEquals(Optional.of(event), reader.readNext(TOKEN));
            }
            assertEquals(Optional.empty(), reader.readNext(TOKEN));
        }
    }

    @Test
    void acceptsAnImplicitOrMatchingConcreteAssignmentForInheritedUses() {
        var fixture = compileInline(MODEL, """
                INTERLIS 2.4;
                MODEL ProtocolFixture AT "https://example.org" VERSION "1" =
                  DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                         A = COORD 0..100,0..100;
                         B = COORD 200..300,200..300;
                  CONTEXT Default = G = A;
                  TOPIC Base =
                    CLASS Entry = first: G; second: G; END Entry;
                  END Base;
                  TOPIC Data EXTENDS Base = END Data;
                END ProtocolFixture.
                """);
        var fixturePlan = ready(fixture);
        var topic = ref(fixture, MODEL, "Data");
        var generic = ref(fixture, MODEL, "G");
        var fixed = ref(fixture, MODEL, "A");
        var descriptor = fixturePlan.topic(topic).orElseThrow();
        assertEquals(List.of(ref(fixture, MODEL, "Base", "Entry")), descriptor.objectTypes());
        assertEquals(List.of(new GenericBinding(generic.symbol(), List.of(fixed.symbol()),
                List.of(ref(fixture, MODEL, "Default").symbol()), BindingState.CONCRETE)),
                descriptor.genericBindings());

        var start = startTransfer(fixture, LanguageVersion.ILI24);
        var implicit = domainBasket(BASKET, topic, List.of());
        var other = new BasketId("explicit", SOURCE);
        var matching = domainBasket(other, topic, List.of(new DomainBinding(generic, fixed)));
        var mismatched = domainBasket(BASKET, topic, List.of(new DomainBinding(generic, ref(fixture, MODEL, "B"))));
        List<TransferEvent> expected = List.of(start, implicit, new EndBasket(BASKET),
                matching, new EndBasket(other), new EndTransfer());
        var writer = new MemoryWriter(fixturePlan);
        try (writer) {
            writer.write(start);
            expect(CONTEXT_MISMATCH, () -> writer.write(mismatched));
            expected.subList(1, expected.size()).forEach(writer::write);
        }
        assertEquals(expected, writer.events);
        assertReads(fixturePlan, expected);
    }

    @Test
    void keepsAnAbstractGenericInThePlanAndRejectsItsBasket() {
        var fixture = compileInline(MODEL, """
                INTERLIS 2.4;
                MODEL ProtocolFixture AT "https://example.org" VERSION "1" =
                  DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                         A = COORD 0..100,0..100;
                  TOPIC Data (ABSTRACT) =
                    CLASS Entry = point: G; END Entry;
                  END Data;
                END ProtocolFixture.
                """);
        var fixturePlan = ready(fixture);
        var topic = ref(fixture, MODEL, "Data");
        var generic = ref(fixture, MODEL, "G");
        assertEquals(List.of(new GenericBinding(generic.symbol(), List.of(), List.of(), BindingState.ABSTRACT)),
                fixturePlan.topic(topic).orElseThrow().genericBindings());

        var start = startTransfer(fixture, LanguageVersion.ILI24);
        var supplied = new DomainBinding(generic, ref(fixture, MODEL, "A"));
        var writer = new MemoryWriter(fixturePlan);
        try (writer) {
            writer.write(start);
            expect(CONTEXT_MISMATCH, () -> writer.write(domainBasket(BASKET, topic, List.of())));
            expect(CONTEXT_MISMATCH, () -> writer.write(domainBasket(BASKET, topic, List.of(supplied))));
            writer.write(new EndTransfer());
        }
        assertEquals(List.of(start, new EndTransfer()), writer.events);
        assertReads(fixturePlan, writer.events);
    }

    private static StartBasket domainBasket(BasketId id, SymbolRef topic, List<DomainBinding> bindings) {
        return new StartBasket(id, topic, List.of(), FULL, COMPLETE,
                Optional.empty(), Optional.empty(), Optional.empty(), bindings);
    }

    private static StartBasket updatedBasket(CompiledSchema schema, List<DomainBinding> assignments) {
        return new StartBasket(BASKET, ref(schema, "GenericExample", "Data"), List.of(), UPDATE, ADAPTED,
                Optional.of("state-9"), Optional.of("state-10"), Optional.of("basket-oid"), assignments);
    }

    private static void expect(TransferProtocolException.Reason reason, Executable operation) {
        assertEquals(reason, assertThrows(TransferProtocolException.class, operation).reason());
    }

    private static void assertReads(TransferPlan plan, List<TransferEvent> events) {
        try (var reader = new MemoryReader(plan, events.iterator())) {
            for (TransferEvent event : events) {
                assertEquals(Optional.of(event), reader.readNext(TOKEN));
            }
            assertEquals(Optional.empty(), reader.readNext(TOKEN));
        }
    }

    private static StartTransfer startTransfer() {
        return startTransfer(schema, LanguageVersion.ILI24);
    }

    private static StartTransfer startTransfer(CompiledSchema fixture, LanguageVersion language) {
        return new StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of(
                new ModelDeclaration(ref(fixture, MODEL), language, Optional.of("1"), Optional.empty())));
    }

    private static StartBasket startBasket() {
        return startBasket(schema, BASKET, FULL);
    }

    private static StartBasket startBasket(CompiledSchema fixture, BasketId id, BasketKind kind) {
        return new StartBasket(id, ref(fixture, MODEL, "Data"), List.of(), kind, COMPLETE,
                Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private static ObjectEvent object(BasketId basket) {
        return object(schema, basket, Optional.empty(), Optional.empty());
    }

    private static ObjectEvent object(CompiledSchema fixture, BasketId basket, Optional<TransferId> tid,
                                      Optional<ObjectOid> oid) {
        return new ObjectEvent(objectData(fixture, basket, tid, oid), Optional.empty(), COMPLETE);
    }

    private static TransferObject objectData(CompiledSchema fixture, BasketId basket, Optional<TransferId> tid,
                                             Optional<ObjectOid> oid) {
        var identity = new TransferIdentity(basket, Optional.empty(), tid, oid, Optional.empty());
        return new TransferObject(identity, new TransferValue.Structured(ref(fixture, MODEL, "Data", "Entry"),
                Map.of()));
    }
}
