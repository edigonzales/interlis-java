package guru.interlis.validation;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferIdentity.*;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.*;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StoredValueCodecTest {
    @Test
    void retainsExactValuesAndInvalidButDecodableGeometry() {
        var snapshot = new SnapshotId("test");
        var codec = new StoredValueCodec(snapshot, 20_000);
        var type = new SymbolRef(snapshot, new SymbolId("structure"));
        var slot = new SymbolRef(snapshot, new SymbolId("slot"));
        var undefined = new Structured(type, Map.of(slot, Undefined.INSTANCE));
        var missing = new Structured(type, Map.of());
        var number = new NumberValue("1.000E+2", new BigDecimal("100.0"));
        var point = new Coordinate(List.of(number, number));
        var curve = new Polyline(List.of(new CurvePart(point, List.of(new Straight(point),
                new Arc(point, point, Optional.of(new ArcRadius(number, RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT)))))), Optional.of(undefined));
        var surface = new Surface(new Ring(List.of(curve)), List.of(new Ring(List.of(curve))));
        var fields = new LinkedHashMap<SymbolRef, TransferValue>();
        fields.put(slot, Undefined.INSTANCE);
        fields.put(new SymbolRef(snapshot, new SymbolId("duplicate")), new Repeated(CollectionKind.BAG,
                List.of(number, number, new XmlFragment("<p:x/>", Map.of("p", "urn:test")), missing)));
        var values = List.<TransferValue>of(
                Undefined.INSTANCE, new Text(""), new BooleanValue(false), number, new Enumeration(List.of("a", "b")),
                new Binary(new byte[]{1, 0, -1}), new Oid("space:ABC"), new ClassName(QualifiedName.of("A", "B")),
                new AttributePath(QualifiedName.of("A", "B", "c")), new Structured(type, fields),
                new Reference(new TransferId("id", IdOrigin.SYNTHETIC),
                        Optional.of(new BasketId("other", IdOrigin.SOURCE)), true, Optional.of(BigInteger.TEN)),
                new Reference(new ObjectOid("space:ABC"), Optional.empty(), false, Optional.empty()),
                point, new MultiCoordinate(List.of(point)), curve, new MultiPolyline(List.of(curve)),
                surface, new MultiSurface(List.of(surface)));
        for (var value : values) {
            byte[] encoded = codec.encode(value);
            var decoded = codec.decode(encoded);
            assertEquals(value, decoded);
            assertArrayEquals(encoded, codec.encode(decoded));
            assertThrows(UncheckedIOException.class, () -> codec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
        }
        assertNotEquals(codec.decode(codec.encode(undefined)), codec.decode(codec.encode(missing)));
        assertThrows(ValidationLimitException.class, () -> new StoredValueCodec(snapshot, 8).encode(number));
    }
}
