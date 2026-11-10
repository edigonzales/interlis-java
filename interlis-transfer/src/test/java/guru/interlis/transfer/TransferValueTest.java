package guru.interlis.transfer;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferValueTest {
    private static final SnapshotId SNAPSHOT = new SnapshotId("transfer-value-fixture");

    @Test
    void retainsBinaryPayloadByValueAndCopiesBothDirections() {
        byte[] bytes = {1, 2, -1};
        var value = new TransferValue.Binary(bytes);
        bytes[0] = 0;
        value.bytes()[1] = 0;

        assertArrayEquals(new byte[] {1, 2, -1}, value.bytes());
        var equal = new TransferValue.Binary(new byte[] {1, 2, -1});
        assertEquals(equal, value);
        assertEquals(equal.hashCode(), value.hashCode());
        assertNotEquals(value, new TransferValue.Binary(new byte[0]));
    }

    @Test
    void keepsModelNamesAndOidsDistinctFromPlainText() {
        var name = QualifiedName.of("Model", "Topic", "Object");
        assertNotEquals(new TransferValue.ClassName(name), new TransferValue.AttributePath(name));
        assertNotEquals(new TransferValue.Oid("domain:value"), new TransferValue.Text("domain:value"));
        assertNotEquals(new TransferValue.XmlFragment(""), new TransferValue.Text(""));
    }

    @Test
    void retainsImmutableNamespaceBindingsForXmlFragments() {
        var bindings = new java.util.HashMap<String, String>();
        bindings.put("p", "urn:payload");
        var value = new TransferValue.XmlFragment("p:Name", bindings);
        bindings.clear();
        assertEquals(Map.of("p", "urn:payload"), value.namespaceBindings());
        assertThrows(UnsupportedOperationException.class, () -> value.namespaceBindings().clear());
        assertEquals(Map.of(), new TransferValue.XmlFragment("<x/>").namespaceBindings());
    }

    @Test
    void checksOwnershipInsideEachGeometryContainerIncludingHoles() {
        var type = ref("object");
        var field = ref("geometry");
        var identity = new TransferIdentity(new TransferIdentity.BasketId("basket", TransferIdentity.IdOrigin.SOURCE),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        var point = new TransferGeometry.Coordinate(List.of(
                new TransferValue.NumberValue("0", BigDecimal.ZERO),
                new TransferValue.NumberValue("0", BigDecimal.ZERO)));
        var part = new TransferGeometry.CurvePart(point, List.of());
        var foreign = new SymbolRef(new SnapshotId("foreign-line-attributes"), new SymbolId("marks"));
        var marked = new TransferGeometry.Polyline(List.of(part),
                Optional.of(new TransferValue.Structured(foreign, Map.of())));
        var ring = new TransferGeometry.Ring(List.of(marked));
        var plain = new TransferGeometry.Ring(List.of(new TransferGeometry.Polyline(List.of(part))));
        var surface = new TransferGeometry.Surface(plain, List.of(ring));
        for (var geometry : List.of(marked, new TransferGeometry.MultiPolyline(List.of(marked)),
                new TransferGeometry.Surface(ring, List.of()), surface,
                new TransferGeometry.MultiSurface(List.of(surface)))) {
            assertThrows(SnapshotMismatchException.class, () -> new TransferObject(identity,
                    new TransferValue.Structured(type, Map.of(field, geometry))));
        }
    }

    @Test
    void separatesMissingUndefinedAndPresentEmptyValues() {
        var type = ref("type");
        var missing = ref("missing");
        var undefined = ref("undefined");
        var emptyText = ref("empty-text");
        var emptyBag = ref("empty-bag");
        var emptyList = ref("empty-list");
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        values.put(undefined, TransferValue.Undefined.INSTANCE);
        values.put(emptyText, new TransferValue.Text(""));
        values.put(emptyBag, new TransferValue.Repeated(CollectionKind.BAG, List.of()));
        values.put(emptyList, new TransferValue.Repeated(CollectionKind.LIST, List.of()));
        var structure = new TransferValue.Structured(type, values);
        values.put(missing, new TransferValue.BooleanValue(true));

        assertEquals(Optional.empty(), structure.value(missing));
        assertEquals(Optional.of(TransferValue.Undefined.INSTANCE), structure.value(undefined));
        assertEquals(Optional.of(new TransferValue.Text("")), structure.value(emptyText));
        assertEquals(Optional.of(new TransferValue.Repeated(CollectionKind.BAG, List.of())), structure.value(emptyBag));
        assertEquals(Optional.of(new TransferValue.Repeated(CollectionKind.LIST, List.of())), structure.value(emptyList));
        assertEquals(List.of(undefined, emptyText, emptyBag, emptyList), List.copyOf(structure.values().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> structure.values().clear());

        values.put(missing, null);
        assertThrows(NullPointerException.class, () -> new TransferValue.Structured(type, values));
        assertThrows(NullPointerException.class, () -> new TransferValue.Text(null));
    }

    @Test
    void preservesPrecisionAndOriginalLexemeSeparately() {
        var value = new TransferValue.NumberValue("000123456789012345678901234567890.0012300",
                new BigDecimal("123456789012345678901234567890.0012300"));

        assertEquals("000123456789012345678901234567890.0012300", value.lexeme());
        assertEquals(new BigDecimal("123456789012345678901234567890.0012300"), value.exactValue());
        assertEquals(7, value.exactValue().scale());
        assertNotEquals(value, new TransferValue.NumberValue("123456789012345678901234567890.0012300",
                value.exactValue()));
        assertThrows(IllegalArgumentException.class, () -> new TransferValue.NumberValue(" ", BigDecimal.ZERO));
    }

    @Test
    void roundTripsNestedStructuresAndPrimitiveRepetitionsWithTheirOrderAndKind() {
        var type = ref("object-type");
        var innerType = ref("structure-type");
        var bagSlot = ref("bag-slot");
        var listSlot = ref("list-slot");
        var structureSlot = ref("structure-slot");
        var enumSlot = ref("enum-slot");
        var falseValue = new TransferValue.BooleanValue(false);
        var trueValue = new TransferValue.BooleanValue(true);
        var primitives = new ArrayList<TransferValue>(List.of(falseValue, trueValue, falseValue));
        var bag = new TransferValue.Repeated(CollectionKind.BAG, primitives);
        var list = new TransferValue.Repeated(CollectionKind.LIST, primitives);
        primitives.clear();

        var enumPath = new ArrayList<>(List.of("surface", "roof"));
        var enumeration = new TransferValue.Enumeration(enumPath);
        enumPath.clear();
        var inner = new TransferValue.Structured(innerType, Map.of(enumSlot, enumeration));
        var original = new TransferValue.Structured(type, Map.of(bagSlot, bag, listSlot, list, structureSlot, inner));
        var replay = new TransferValue.Structured(original.type(), original.values());

        assertEquals(original, replay);
        assertEquals(CollectionKind.BAG, bag.kind());
        assertEquals(CollectionKind.LIST, list.kind());
        assertEquals(List.of(falseValue, trueValue, falseValue), bag.values());
        assertEquals(List.of(falseValue, trueValue, falseValue), list.values());
        assertNotEquals(bag, list);
        var nested = assertInstanceOf(TransferValue.Structured.class, replay.value(structureSlot).orElseThrow());
        assertEquals(Optional.of(new TransferValue.Enumeration(List.of("surface", "roof"))), nested.value(enumSlot));
        assertThrows(UnsupportedOperationException.class, () -> list.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> enumeration.path().clear());
    }

    @Test
    void retainsReferenceTargetBasketAndOrderIndependentlyOfTheTargetKey() {
        var tid = new TransferIdentity.TransferId("target", TransferIdentity.IdOrigin.SOURCE);
        var oid = new TransferIdentity.ObjectOid("target");
        var targetBasket = new TransferIdentity.BasketId("destination", TransferIdentity.IdOrigin.SOURCE);
        var position = new BigInteger("18446744073709551616");
        var local = new TransferValue.Reference(tid, Optional.empty(), false, Optional.empty());
        var external = new TransferValue.Reference(oid, Optional.of(targetBasket), true, Optional.of(position));

        assertEquals(tid, local.target());
        assertEquals(Optional.empty(), local.targetBasket());
        assertFalse(local.external());
        assertEquals(Optional.empty(), local.orderPosition());
        assertEquals(oid, external.target());
        assertEquals(Optional.of(targetBasket), external.targetBasket());
        assertTrue(external.external());
        assertEquals(Optional.of(position), external.orderPosition());
        assertNotEquals(local, external);
        assertThrows(IllegalArgumentException.class,
                () -> new TransferValue.Reference(tid, Optional.empty(), false, Optional.of(BigInteger.ZERO)));
    }

    @Test
    void rejectsForeignSlotsEvenWhenTheSymbolIdMatches() {
        var type = ref("type");
        var slot = ref("attribute");
        var foreignSlot = new SymbolRef(new SnapshotId("foreign-value-fixture"), slot.symbol());
        var text = new TransferValue.Text("value");
        var structure = new TransferValue.Structured(type, Map.of(slot, text));

        assertEquals(Optional.of(text), structure.value(slot));
        assertThrows(SnapshotMismatchException.class, () -> structure.value(foreignSlot));
        assertThrows(SnapshotMismatchException.class,
                () -> new TransferValue.Structured(type, Map.of(foreignSlot, text)));
    }

    private static SymbolRef ref(String id) {
        return new SymbolRef(SNAPSHOT, new SymbolId(id));
    }
}
