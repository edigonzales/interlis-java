package guru.interlis.transfer;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferEvent.EndBasket;
import guru.interlis.transfer.TransferEvent.EndTransfer;
import guru.interlis.transfer.TransferEvent.ModelDeclaration;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.transfer.TransferEvent.StartTransfer;
import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferGeometry.Ring;
import guru.interlis.transfer.TransferGeometry.Straight;
import guru.interlis.transfer.TransferGeometry.Surface;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.transfer.TransferEvent.BasketKind.FULL;
import static guru.interlis.transfer.TransferEvent.Consistency.COMPLETE;
import static guru.interlis.transfer.TransferEvent.Consistency.INCOMPLETE;
import static guru.interlis.transfer.TransferIdentity.IdOrigin.SOURCE;
import static guru.interlis.transfer.TransferTestSchemas.compileInline;
import static guru.interlis.transfer.TransferTestSchemas.fixture;
import static guru.interlis.transfer.TransferTestSchemas.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransferStreamingContractTest {
    @Test
    void streamsTwoBasketsWithOwnedNestedValuesRolesAndGeometry() throws IOException {
        var original = fixture("reference/iox/ili23/Reference23.ili").text();
        var withLinkPayload = original.replace("children -- {0..*} Item;",
                "children -- {0..*} Item;\n      label : TEXT*20;");
        var schema = compileInline("Reference23", withLinkPayload);
        var fingerprint = schema.fingerprint();
        var graph = schema.semanticGraph();
        var plan = ready(schema);
        var source = new SourceId("memory:/reference-transfer");
        var model = ref(schema, "Reference23");
        var topic = ref(schema, "Reference23", "Records");
        var item = ref(schema, "Reference23", "Records", "Item");
        var detail = ref(schema, "Reference23", "Records", "Detail");
        var description = ref(schema, "Reference23", "Records", "Detail", "description");
        var code = ref(schema, "Reference23", "Records", "Item", "code");
        var details = ref(schema, "Reference23", "Records", "Item", "details");
        var parcel = ref(schema, "Reference23", "Records", "Parcel");
        var shape = ref(schema, "Reference23", "Records", "Parcel", "shape");
        var parent = ref(schema, "Reference23", "Records", "Parent");
        var link = ref(schema, "Reference23", "Records", "ParentLink");
        var parentRole = ref(schema, "Reference23", "Records", "ParentLink", "parent");
        var childrenRole = ref(schema, "Reference23", "Records", "ParentLink", "children");
        var label = ref(schema, "Reference23", "Records", "ParentLink", "label");
        var first = new BasketId("basket-1", SOURCE);
        var second = new BasketId("basket-2", SOURCE);
        var itemId = new TransferId("item-1", SOURCE);
        var parentId = new TransferId("parent-1", SOURCE);
        var ownedDetails = new TransferValue.Repeated(CollectionKind.LIST, List.of(
                new Structured(detail, Map.of(description, new Text("first"))),
                new Structured(detail, Map.of(description, new Text("second")))));
        var fields = new LinkedHashMap<SymbolRef, TransferValue>();
        fields.put(code, new Text("ABC"));
        fields.put(details, ownedDetails);
        var itemEvent = object(source, first, Optional.of(itemId), item, fields);
        fields.clear();

        var start = new StartTransfer(Optional.of(source), Optional.of("reference sender"), Optional.of("two baskets"),
                List.of(new ModelDeclaration(model, LanguageVersion.ILI23, Optional.of("2026-11-06"),
                        Optional.of("https://example.invalid/iox-reference"))));
        var surface = surface();
        var linkEvent = object(source, second, Optional.empty(), link, Map.of(
                parentRole, new Reference(parentId, Optional.empty(), false, Optional.empty()),
                childrenRole, new Reference(itemId, Optional.of(first), false, Optional.empty()),
                label, new Text("relationship payload")));
        List<TransferEvent> expected = List.of(start,
                new StartBasket(first, topic, List.of(), FULL, COMPLETE,
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of()),
                itemEvent, object(source, first, Optional.of(new TransferId("parcel-1", SOURCE)),
                        parcel, Map.of(shape, surface)),
                new EndBasket(first),
                new StartBasket(second, topic, List.of(), FULL, INCOMPLETE,
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of()),
                object(source, second, Optional.of(parentId), parent, Map.of()),
                linkEvent, new EndBasket(second), new EndTransfer());

        var writer = new MemoryWriter(plan);
        try (writer) {
            for (TransferEvent event : expected) {
                writer.write(event);
            }
        }
        assertEquals(expected, writer.events);
        assertEquals(1, writer.releases);
        var reader = new MemoryReader(plan, writer.events.iterator());
        try (reader) {
            assertEquals(0, reader.pulls);
            for (int index = 0; index < expected.size(); index++) {
                assertEquals(Optional.of(expected.get(index)), reader.readNext(CancellationToken.none()));
                assertEquals(index + 1, reader.pulls);
            }
            assertEquals(Optional.empty(), reader.readNext(CancellationToken.none()));
        }
        assertEquals(1, reader.releases);
        assertEquals(Optional.of(new Text("ABC")), itemEvent.object().value(code));
        assertEquals(Optional.of(ownedDetails), itemEvent.object().value(details));
        assertEquals(Optional.of(new Reference(itemId, Optional.of(first), false, Optional.empty())),
                linkEvent.object().value(childrenRole));
        assertSame(graph, schema.semanticGraph());
        assertEquals(fingerprint, schema.fingerprint());
        assertEquals(fingerprint, plan.schemaFingerprint());
    }

    private static ObjectEvent object(SourceId source, BasketId basket, Optional<TransferId> id,
                                      SymbolRef type, Map<SymbolRef, TransferValue> fields) {
        var identity = new TransferIdentity(basket, Optional.empty(), id, Optional.empty(), Optional.of(source));
        return new ObjectEvent(new TransferObject(identity, new Structured(type, fields)), Optional.empty(), COMPLETE);
    }

    private static Surface surface() {
        var start = point("0.00", "0.00");
        var path = new CurvePart(start, List.of(new Straight(point("10.00", "0.00")),
                new Arc(point("15.0000001", "5.0000001"), point("10.00", "10.00"), Optional.empty()),
                new Straight(point("0.00", "10.00")), new Straight(start)));
        return new Surface(new Ring(List.of(new Polyline(List.of(path)))), List.of());
    }

    private static Coordinate point(String x, String y) {
        return new Coordinate(List.of(new NumberValue(x, new BigDecimal(x)), new NumberValue(y, new BigDecimal(y))));
    }

    static TransferPlan ready(CompiledSchema schema) {
        return assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema)).plan();
    }

    static final class MemoryReader extends TransferReader {
        private final Iterator<TransferEvent> events;
        int pulls;
        int releases;

        MemoryReader(TransferPlan plan, Iterator<TransferEvent> events) {
            super(plan);
            this.events = events;
        }

        @Override
        protected Optional<TransferEvent> readEvent(CancellationToken cancellation) {
            pulls++;
            return events.hasNext() ? Optional.of(events.next()) : Optional.empty();
        }

        @Override
        protected void release() {
            releases++;
        }
    }

    static final class MemoryWriter extends TransferWriter {
        final List<TransferEvent> events = new ArrayList<>();
        int releases;

        MemoryWriter(TransferPlan plan) {
            super(plan);
        }

        @Override
        protected void writeEvent(TransferEvent event, CancellationToken cancellation) {
            events.add(event);
        }

        @Override
        protected void release() {
            releases++;
        }
    }
}
