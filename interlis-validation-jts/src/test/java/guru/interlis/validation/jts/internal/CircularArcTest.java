package guru.interlis.validation.jts.internal;

import guru.interlis.transfer.TransferGeometry.ArcRadius;
import guru.interlis.transfer.TransferGeometry.RadiusConvention;
import guru.interlis.transfer.TransferValue.NumberValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static org.junit.jupiter.api.Assertions.*;

class CircularArcTest {
    @Test
    void measuresAndReversesAnAnalyticalSemicircleInSwissCoordinates() {
        var start = new Point(2_600_001, 1_200_000);
        var support = new Point(2_600_000, 1_200_001);
        var end = new Point(2_599_999, 1_200_000);
        var arc = built(CircularArc.build(start, support, end, Optional.empty(), 0));
        assertEquals(Math.PI, arc.length(), 1e-12);
        assertEquals(Math.PI / 2, arc.signedArea(start), 1e-12);
        assertEquals(1_200_001, arc.envelope().getMaxY());
        assertEquals(arc.envelope(), arc.reversed().envelope());
        assertEquals(-arc.signedArea(start), arc.reversed().signedArea(start), 1e-12);
        assertEquals(arc, arc.reversed().reversed());
    }

    @Test
    void selectsTwoDifferentCirclesWithTheSameRadiusAndEndpoints() {
        var start = new Point(-1, 0);
        var end = new Point(1, 0);
        double height = Math.sqrt(3);
        var radius = radius("2", RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT);
        var minor = built(CircularArc.build(start, new Point(0, 2 - height), end, radius, 1e-12));
        var major = built(CircularArc.build(start, new Point(0, 2 + height), end, radius, 1e-12));
        assertEquals(-height, minor.center().y(), 1e-12);
        assertEquals(height, major.center().y(), 1e-12);
        assertEquals(-Math.PI / 3, minor.sweep(), 1e-12);
        assertEquals(-5 * Math.PI / 3, major.sweep(), 1e-12);
        assertEquals(2, major.envelope().getMaxX(), 1e-12);
    }

    @Test
    void distinguishesWireRadiusConventions() {
        var start = new Point(-1, 0);
        var support = new Point(0, 1);
        var end = new Point(1, 0);
        assertEquals(-Math.PI, built(CircularArc.build(start, support, end,
                radius("1", RadiusConvention.SIGNED_CLOCKWISE), 0)).sweep());
        assertEquals(Math.PI, built(CircularArc.build(end, support, start,
                radius("-1", RadiusConvention.SIGNED_CLOCKWISE), 0)).sweep());
        assertInstanceOf(CircularArc.Construction.Invalid.class, CircularArc.build(start, support, end,
                radius("-1", RadiusConvention.SIGNED_CLOCKWISE), 0));
        assertInstanceOf(CircularArc.Construction.Invalid.class, CircularArc.build(end, support, start,
                radius("-1", RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT), 0));
        assertInstanceOf(CircularArc.Construction.Invalid.class, CircularArc.build(start, support, end,
                radius("0.99", RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT), 0));
    }

    @Test
    void rejectsRadiusBelowExactHalfChordEvenWhenBinary64RoundsItToEquality() {
        var start = new Point(-1, 0);
        var end = new Point(1, 0);
        var support = new Point(0, 1);
        assertInstanceOf(CircularArc.Construction.Invalid.class, CircularArc.build(start, support, end,
                radius("0.99999999999999999999999999999999", RadiusConvention.POSITIVE_SELECTED_BY_SUPPORT), 0.001));
    }

    @Test
    void rejectsImpossibleDiameterWithCollinearIntermediatePositionInBothFormats() {
        for (var convention : RadiusConvention.values()) {
            var invalid = assertInstanceOf(CircularArc.Construction.Invalid.class, CircularArc.build(
                    new Point(0, 0), new Point(5, 0), new Point(10, 0), radius("1", convention),
                    BigDecimal.ONE, true, () -> {}));
            assertTrue(invalid.chordTooLong());
            assertEquals("Radius is smaller than half the chord (squared chord 100, squared diameter 4)",
                    invalid.detail());
        }
    }

    @Test
    void signedRadiusSelectsMinorClockwiseArcAcrossTheChordFromDisplacedIntermediatePosition() {
        // ILI 2.3 §2.8.12.2 and §3.3.11.12. Distance to the short clockwise branch is
        // 11 - sqrt(96), while the nearest points on the long branch are sqrt(5) away.
        var arc = built(CircularArc.build(new Point(0, 0), new Point(2, -1), new Point(4, 0),
                radius("10", RadiusConvention.SIGNED_CLOCKWISE), BigDecimal.ONE, true, () -> {}));
        assertEquals(-Math.sqrt(96), arc.center().y(), 1e-12);
        assertEquals(-2 * Math.asin(0.2), arc.sweep(), 1e-12);
    }

    private static CircularArc built(CircularArc.Construction result) {
        return assertInstanceOf(CircularArc.Construction.Built.class, result).arc();
    }

    private static Optional<ArcRadius> radius(String value, RadiusConvention convention) {
        return Optional.of(new ArcRadius(new NumberValue(value, new BigDecimal(value)), convention));
    }
}
