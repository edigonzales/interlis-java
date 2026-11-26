package guru.interlis.validation.jts.internal;

import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Origin;
import guru.interlis.model.TypeId;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.IdOrigin;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.DataLocation.GeometryComponent;
import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.GeometryWitness;
import guru.interlis.validation.ValidationCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.validation.DataLocation.GeometryComponent.Kind.*;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapterTest.point;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CurveCoverageTest {
    @Test
    void reportsTheInnerBoundariesOfEachGapWithoutIncludingOtherGaps() {
        // INTERLIS 2.4 section 3.14. Each bounded free face has a separate outer contour.
        var inputs = List.of(
                input("shell", rectangle("0", "0", "20", "20"), rectangle("2", "2", "18", "18")),
                input("outerIsland", rectangle("4", "4", "16", "16"), rectangle("6", "6", "14", "14")),
                input("innerIsland", rectangle("8", "8", "12", "12")),
                input("secondIsland", rectangle("2.5", "3", "3.5", "4")),
                input("outside", rectangle("21", "21", "22", "22")));
        var source = new GeometryEngine.Source() {
            @Override public boolean complete() {
                return true;
            }

            @Override public GeometryEngine.Cursor open() {
                var iterator = inputs.iterator();
                return new GeometryEngine.Cursor() {
                    @Override public Optional<GeometryEngine.Input> next() {
                        return iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty();
                    }

                    @Override public void close() {}
                };
            }
        };
        var findings = new ArrayList<GeometryEngine.Finding>();
        assertEquals(GeometryEngine.Check.INVALID, CurveCoverage.check(source, GeometryEngine.CoveragePurpose.AREA_WITHOUT_GAPS,
                new GeometryEngine.Control(() -> {}, findings::add)));
        assertEquals(2, findings.size());
        assertGap(findings.getFirst(), "shell", "18", "2", Set.of("outerIsland", "secondIsland"));
        assertGap(findings.getLast(), "outerIsland", "14", "6", Set.of("innerIsland"));
    }

    private static void assertGap(GeometryEngine.Finding finding, String source, String x, String y, Set<String> islands) {
        assertEquals(ValidationCode.GEOMETRY_GAP, finding.code());
        assertEquals(source, tid(finding.location()));
        assertEquals(0, new BigDecimal(x).compareTo(finding.witness().orElseThrow().x()));
        assertEquals(0, new BigDecimal(y).compareTo(finding.witness().orElseThrow().y()));
        assertEquals(GeometryWitness.Kind.GAP_BOUNDARY, finding.witness().orElseThrow().kind());
        assertEquals(path(2), finding.path());
        assertEquals(islands, finding.relatedLocations().stream().map(CurveCoverageTest::tid).collect(Collectors.toSet()));
        finding.relatedLocations().forEach(location -> assertEquals(path(1), location.path()));
    }

    private static List<GeometryComponent> path(int ring) {
        return List.of(new GeometryComponent(RING, ring), new GeometryComponent(LINE, 1),
                new GeometryComponent(PART, 1), new GeometryComponent(SEGMENT, 1));
    }

    private static String tid(DataLocation location) {
        return location.tid().orElseThrow().value();
    }

    private static GeometryEngine.Input input(String tid, Ring shell, Ring... holes) {
        var type = new GeometryType(new TypeId("surface"), new Origin.PredefinedOrigin(LanguageVersion.ILI24, "surface"),
                GeometryKind.SURFACE, List.of(), Optional.empty(), Optional.empty(), false, true, Optional.empty());
        var location = new DataLocation(Optional.empty(), new BasketId("basket", IdOrigin.SOURCE),
                Optional.of(new TransferId(tid, IdOrigin.SOURCE)), Optional.empty(), Optional.empty(), List.of());
        return new GeometryEngine.Input(new Surface(shell, List.of(holes)), type, location, LanguageVersion.ILI24,
                Optional.of(new BigDecimal("0.001")), Optional.of(new BigDecimal("0.001")), false);
    }

    private static Ring rectangle(String lowX, String lowY, String highX, String highY) {
        var start = point(lowX, lowY);
        var part = new CurvePart(start, List.of(new Straight(point(highX, lowY)), new Straight(point(highX, highY)),
                new Straight(point(lowX, highY)), new Straight(start)));
        return new Ring(List.of(new Polyline(List.of(part))));
    }
}
