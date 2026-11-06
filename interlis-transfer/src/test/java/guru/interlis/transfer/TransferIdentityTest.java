package guru.interlis.transfer;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.transfer.TransferIdentity.IdOrigin.SOURCE;
import static guru.interlis.transfer.TransferIdentity.IdOrigin.SYNTHETIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferIdentityTest {
    @Test
    void keepsSourceAndSyntheticTransferIdsSeparateFromTheStableObjectOid() {
        var current = new TransferIdentity.BasketId("basket", SYNTHETIC);
        var original = new TransferIdentity.BasketId("original", SOURCE);
        var source = new SourceId("memory:/transfer");
        var synthetic = new TransferIdentity.TransferId("42", SYNTHETIC);
        var explicit = new TransferIdentity.TransferId("42", SOURCE);
        var stable = new TransferIdentity.ObjectOid("42");
        var identity = new TransferIdentity(current, Optional.of(original), Optional.of(synthetic),
                Optional.empty(), Optional.of(source));
        var identified = new TransferIdentity(current, Optional.of(original), Optional.of(explicit),
                Optional.of(stable), Optional.of(source));

        assertEquals(current, identity.currentBasket());
        assertEquals(SYNTHETIC, identity.currentBasket().origin());
        assertEquals(Optional.of(original), identity.originalBasket());
        assertEquals(Optional.of(source), identity.source());
        assertEquals(Optional.of(synthetic), identity.tid());
        assertEquals(Optional.empty(), identity.oid());
        assertEquals(Optional.of(explicit), identified.tid());
        assertEquals(Optional.of(stable), identified.oid());
        assertEquals(synthetic.value(), stable.value());
        assertNotEquals(synthetic, explicit);
        assertNotEquals(synthetic, stable);
        assertNotEquals(explicit, stable);
    }

    @Test
    void keepsAnUnidentifiedObjectAndItsDataWithoutInventingAKey() {
        var basket = new TransferIdentity.BasketId("basket", SOURCE);
        var identity = new TransferIdentity(basket, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
        var snapshot = new SnapshotId("transfer-identity-fixture");
        var type = new SymbolRef(snapshot, new SymbolId("type"));
        var slot = new SymbolRef(snapshot, new SymbolId("name"));
        var value = new TransferValue.Text("same object data");
        var data = new TransferValue.Structured(type, Map.of(slot, value));
        var object = new TransferObject(identity, data);

        assertEquals(object, new TransferObject(identity, new TransferValue.Structured(type, data.values())));
        assertEquals(identity, object.identity());
        assertEquals(Optional.empty(), object.identity().tid());
        assertEquals(Optional.empty(), object.identity().oid());
        assertEquals(type, object.type());
        assertEquals(Optional.of(value), object.value(slot));
        assertEquals(Optional.empty(), object.value(new SymbolRef(snapshot, new SymbolId("missing"))));
    }

    @Test
    void rejectsAForeignNestedStructureInsideARepeatedValue() {
        var basket = new TransferIdentity.BasketId("basket", SOURCE);
        var identity = new TransferIdentity(basket, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
        var snapshot = new SnapshotId("transfer-identity-fixture");
        var type = new SymbolRef(snapshot, new SymbolId("type"));
        var slot = new SymbolRef(snapshot, new SymbolId("children"));
        var foreignType = new SymbolRef(new SnapshotId("other-identity-fixture"), new SymbolId("structure"));
        var nested = new TransferValue.Structured(foreignType, Map.of());
        var list = new TransferValue.Repeated(CollectionKind.LIST, List.of(nested));

        assertThrows(SnapshotMismatchException.class,
                () -> new TransferObject(identity, new TransferValue.Structured(type, Map.of(slot, list))));
    }

    @Test
    void checksDeepValuesWithoutRecursingThroughTheJavaStack() {
        var basket = new TransferIdentity.BasketId("basket", SOURCE);
        var identity = new TransferIdentity(basket, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());
        var snapshot = new SnapshotId("deep-transfer");
        var type = new SymbolRef(snapshot, new SymbolId("type"));
        var slot = new SymbolRef(snapshot, new SymbolId("child"));
        TransferValue value = new TransferValue.Text("leaf");
        for (int depth = 0; depth < 8_192; depth++) {
            value = new TransferValue.Structured(type, Map.of(slot, value));
        }

        var object = new TransferObject(identity, (TransferValue.Structured) value);
        TransferValue actual = object.data();
        for (int depth = 0; depth < 8_192; depth++) {
            actual = ((TransferValue.Structured) actual).value(slot).orElseThrow();
        }
        assertEquals(new TransferValue.Text("leaf"), actual);
    }
}
