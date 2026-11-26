package guru.interlis.validation.jts.internal;

import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Origin;
import guru.interlis.model.TypeId;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.ArcRadius;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.RadiusConvention;
import guru.interlis.transfer.TransferGeometry.Straight;
import guru.interlis.transfer.TransferReader.XtfEncoding;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.GeometryEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.jts.internal.TransferGeometryAdapterTest.point;
import static org.junit.jupiter.api.Assertions.*;

class AreaTransferIdentityTest {
    @Test
    void distinguishesOppositeTransferOrderFromGeometricDirection() {
        // XTF 2.4 §4.3.11.15 requires reverse support order on either side of a shared AREA edge.
        var nativeEdge = segment(point("0", "0"), new Straight(point("2", "0")), Optional.of(XtfEncoding.XTF24));
        var geometricEdge = segment(point("0", "0"), new Straight(point("2", "0")), Optional.empty());
        assertEquals(nativeEdge.edge(), geometricEdge.edge());
        assertTrue(nativeEdge.transferIdentityMismatch(geometricEdge, true).isEmpty());
        assertTrue(nativeEdge.transferIdentityMismatch(nativeEdge.reversed(), false).isEmpty());
        var sameDirection = nativeEdge.transferIdentityMismatch(nativeEdge, true).orElseThrow();
        assertTrue(sameDirection.detail().contains("same direction"));
        assertEquals(nativeEdge.curve().start(), sameDirection.witness());
    }

    @Test
    void usesTheActualSupportPointWhoseElevationDiffers() {
        // The first point agrees even in Z. Only the other endpoint can substantiate the transfer mismatch.
        var first = segment(point("0", "0", "1"), new Straight(point("2", "0", "1.000")), Optional.of(XtfEncoding.XTF23));
        var second = segment(point("0", "0", "1.0"), new Straight(point("2", "0", "1.0001")), Optional.of(XtfEncoding.XTF23));
        assertEquals(first.edge(), second.edge());
        var mismatch = first.transferIdentityMismatch(second, true).orElseThrow();
        assertTrue(mismatch.detail().contains("support point ordinates"));
        assertEquals(first.curve().end(), mismatch.witness());
    }

    @Test
    void keepsSignedReversalAndRadiusPresenceAsTransferData() {
        // XTF 2.3 §3.3.11.13 permits the sign change caused by reversing the same arc.
        var first = segment(point("0", "0"), new Arc(point("1", "1"), point("2", "0"), radius("1")),
                Optional.of(XtfEncoding.XTF23));
        var second = segment(point("2", "0"), new Arc(point("1.00", "1.00"), point("0", "0"), radius("-1.0")),
                Optional.of(XtfEncoding.XTF23));
        var omitted = segment(point("2", "0"), new Arc(point("1", "1"), point("0", "0"), Optional.empty()),
                Optional.of(XtfEncoding.XTF23));
        assertEquals(first.edge().reversed(), second.edge());
        assertEquals(second.edge(), omitted.edge());
        assertTrue(first.transferIdentityMismatch(second, false).isEmpty());
        assertTrue(first.transferIdentityMismatch(omitted, false).orElseThrow().detail().contains("transferred radii"));
    }

    private static Optional<ArcRadius> radius(String value) {
        return Optional.of(new ArcRadius(new NumberValue(value, new BigDecimal(value)), RadiusConvention.SIGNED_CLOCKWISE));
    }

    private static TransferGeometryAdapter.Segment segment(TransferGeometry.Coordinate start,
            TransferGeometry.Segment segment, Optional<XtfEncoding> encoding) {
        var language = encoding.orElse(null) == XtfEncoding.XTF23 ? LanguageVersion.ILI23 : LanguageVersion.ILI24;
        var input = new GeometryEngine.Input(new TransferGeometry.Polyline(List.of(new CurvePart(start, List.of(segment)))),
                new GeometryType(new TypeId("area"), new Origin.PredefinedOrigin(language, "area"), GeometryKind.AREA,
                        List.of(), Optional.empty(), Optional.empty(), false, true, Optional.empty()),
                new DataLocation(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of()),
                language, Optional.of(new BigDecimal("0.001")), Optional.empty(), false,
                Optional.empty(), Optional.empty(), encoding);
        var result = new TransferGeometryAdapter(input, new GeometryEngine.Control(() -> {},
                finding -> fail(finding.detail())), new TransferGeometryAdapter.Position(BigDecimal.ZERO, BigDecimal.ZERO))
                .read();
        assertEquals(GeometryEngine.Check.VALID, result.check());
        return result.lines().getFirst().segments().getFirst();
    }
}
