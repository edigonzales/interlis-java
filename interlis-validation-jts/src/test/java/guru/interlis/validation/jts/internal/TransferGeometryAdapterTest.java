package guru.interlis.validation.jts.internal;

import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Origin;
import guru.interlis.model.TypeId;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.ValidationCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TransferGeometryAdapterTest {
    @Test
    void translatesExactSourcePositionsBeforeConvertingToDouble() {
        var start = point("2600000.000000000000", "1200000", "5");
        var end = point("2600000.000000000001", "1200000", "9");
        var result = read(start, List.of(new Straight(end)), new ArrayList<>());
        assertEquals(GeometryEngine.Check.VALID, result.check());
        assertTrue(result.threeDimensional());
        assertEquals(1e-12, result.lines().getFirst().segments().getFirst().curve().length());
    }

    @Test
    void retainsSourcePathAndRejectsTheInvalidArcWithoutConvertingItToAChord() {
        var findings = new ArrayList<GeometryEngine.Finding>();
        var result = read(point("0", "0"), List.of(new Straight(point("1", "0")),
                new Arc(point("2", "0"), point("3", "0"), Optional.empty())), findings);
        assertEquals(GeometryEngine.Check.INVALID, result.check());
        assertEquals(1, findings.size());
        assertEquals(ValidationCode.GEOMETRY_INVALID_ARC, findings.getFirst().code());
        assertEquals(new DataLocation.GeometryComponent(DataLocation.GeometryComponent.Kind.SEGMENT, 2),
                findings.getFirst().path().getLast());
    }

    private static TransferGeometryAdapter.Result read(Coordinate start, List<Segment> segments,
                                                        List<GeometryEngine.Finding> findings) {
        var type = new GeometryType(new TypeId("line"), new Origin.PredefinedOrigin(LanguageVersion.ILI24, "line"),
                GeometryKind.POLYLINE, List.of(), Optional.empty(), Optional.empty(), false, false, Optional.empty());
        var data = new DataLocation(Optional.empty(), Optional.<guru.interlis.transfer.TransferIdentity.BasketId>empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        var input = new GeometryEngine.Input(new Polyline(List.of(new CurvePart(start, segments))),
                type, data, LanguageVersion.ILI24, Optional.empty(), Optional.empty(), false);
        return new TransferGeometryAdapter(input, new GeometryEngine.Control(() -> {}, findings::add)).read();
    }

    private static Coordinate point(String... xy) {
        return new Coordinate(Arrays.stream(xy).map(x -> new NumberValue(x, new BigDecimal(x))).toList());
    }
}
