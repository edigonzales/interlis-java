package guru.interlis.validator.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeometryReviewTest {
    @TempDir Path directory;

    @Test
    void distinguishesGeometricArcIdentityFromNativeXtfAreaIdentity() throws IOException {
        String group = "area-arc-24";
        // INTERLIS 2.4 §4.3.11.15. The same upper semicircle has supports (5,10) and (2,9).
        passes(group, "ReviewSurface.ili", "geometric-arc-identity.xtf");
        passes(group, "Review.ili", "matching-intermediate.xtf");
        var support = evidence(group, "Review.ili", "different-intermediate.xtf",
                "GEOMETRY_BOUNDARY_MISMATCH", "GEOMETRY_BOUNDARY_MISMATCH", "shared-boundary", "b",
                participant("1", shape(1, 1)), participant("2", shape(1, 1)));
        assertPoint(support, "10", "5");
        assertTrue(support.get("detail").getAsString().contains("different intermediate ordinates"));

        var radius = evidence(group, "Review.ili", "missing-reciprocal-radius.xtf",
                "GEOMETRY_BOUNDARY_MISMATCH", "GEOMETRY_BOUNDARY_MISMATCH", "shared-boundary", "b",
                participant("1", shape(1, 1)), participant("2", shape(1, 1)));
        assertPoint(radius, "10", "5");
        assertTrue(radius.get("detail").getAsString().contains("different transferred radii"));
        passes("area-radius-23", "Review.ili", "opposite-signed-radii.xtf");
    }

    @Test
    void comparesTheThirdOrdinateOfNativeAreaSupportPointsInBothTransferFormats() throws IOException {
        for (var group : List.of("area-elevation-23", "area-elevation-24")) {
            passes(group, "Shares.ili", "matching-elevations.xtf");
            var mismatch = evidence(group, "Shares.ili", "different-elevations.xtf",
                    "GEOMETRY_BOUNDARY_MISMATCH", "GEOMETRY_BOUNDARY_MISMATCH", "shared-boundary", "review",
                    participant("left", shape(1, 2)), participant("right", shape(1, 4)));
            assertPoint(mismatch, "5", "5");
            var detail = mismatch.get("detail").getAsString();
            assertTrue(detail.contains("[5, 5, 2]") && detail.contains("[5, 5, 3]"), detail);
        }

        String group = "area-elevation-24";
        passes(group, "Shares.ili", "same-model-ordinates.xtf");
        var direction = evidence(group, "Shares.ili", "same-direction.xtf",
                "GEOMETRY_BOUNDARY_MISMATCH", "GEOMETRY_BOUNDARY_MISMATCH", "shared-boundary", "review",
                participant("left", shape(1, 2)), participant("right", shape(1, 1)));
        assertPoint(direction, "5", "1");
        assertTrue(direction.get("detail").getAsString().contains("run in the same direction"));
    }

    @Test
    void distinguishesOneSharedContactFromASeparatingCycle() throws IOException {
        // Three holes at one vertex leave a connected perimeter collar in both versions.
        passes("contacts-23", "Witness23.ili", "common-contact.xtf");
        passes("contacts-23", "Witness23.ili", "common-contact-integer.xtf");
        passes("contacts-24", "Witness.ili", "common-contact.xtf");
        passes("contacts-24", "Witness.ili", "common-contact-integer.xtf");

        // Three distinct contacts enclose the triangle (5,5), (15,5), (10,15).
        var cycle = evidence("contacts-24", "Witness.ili", "separating-cycle.xtf",
                "GEOMETRY_INTERIOR_DISCONNECTED", "GEOMETRY_INTERIOR_DISCONNECTED", "vertex", "review",
                participant("barrier", shape(2, 2)), participant("barrier", shape(3, 2)),
                participant("barrier", shape(4, 1)));
        assertPoint(cycle, "10", "15");
    }

    @Test
    void preservesTheCoveredRegionAcrossRingStartsInXtf23() throws IOException {
        // In Figure 22's single walk the two exact areas round to the same double.
        for (var transfer : List.of("outer-first.xtf", "enclave-first.xtf", "from-corner.xtf")) {
            passes("ring-start-23", "Contact.ili", transfer);
        }
    }

    @Test
    void findsAllSegmentsAtTheSelfContactInBothTransferFormats() throws IOException {
        for (var version : List.of("23", "24")) {
            String model = version.equals("23") ? "Witness23.ili" : "Witness.ili";
            var touch = evidence("contacts-" + version, model, "self-contact.xtf",
                    "GEOMETRY_SELF_INTERSECTION", "GEOMETRY_SELF_INTERSECTION", "intersection", "review",
                    participant("pinch", shape(1, 1)), participant("pinch", shape(1, 4)),
                    participant("pinch", shape(1, 5)));
            assertPoint(touch, "3", "0");
        }
    }

    @Test
    void computesTheIntersectionEvenWhenBinary64DeterminantsOverflow() throws IOException {
        var first = participant("var", shape(1, 1));
        var second = participant("var", shape(1, 3));
        var unit = evidence("large-crossing", "Huge.ili", "unit-crossing.xtf",
                "GEOMETRY_SELF_INTERSECTION", "GEOMETRY_SELF_INTERSECTION", "intersection", "review", first, second);
        assertPoint(unit, "1.75", "1.75");

        // The two lines y=x and x+3y=7·2^516 meet at (7·2^514, 7·2^514).
        var huge = evidence("large-crossing", "Huge.ili", "crossing.xtf",
                "GEOMETRY_SELF_INTERSECTION", "GEOMETRY_SELF_INTERSECTION", "intersection", "review", first, second);
        String coordinate = BigInteger.valueOf(7).shiftLeft(514).toString();
        assertPoint(huge, coordinate, coordinate);
    }

    @Test
    void identifiesTheContainedRingAtTheNestedHoleWitness() throws IOException {
        var nested = evidence("contacts-24", "Witness.ili", "nested-holes.xtf",
                "GEOMETRY_HOLE_OUTSIDE_SHELL", "GEOMETRY_HOLE_OUTSIDE_SHELL", "contained-boundary", "review",
                participant("outer", "/shape/RING[2]"), participant("outer", "/shape/RING[3]"));
        assertPoint(nested, "4", "4");
        assertEquals("/shape/RING[2]", nested.getAsJsonObject("location").get("path").getAsString());
    }

    @Test
    void provesTheSideOfACurvedHoleWhenAllItsSupportPointsTouchTheShell() throws IOException {
        var outside = evidence("curved-contacts", "OutsideLens.ili", "outside.xtf",
                "GEOMETRY_HOLE_OUTSIDE_SHELL", "GEOMETRY_HOLE_OUTSIDE_SHELL", "outside-boundary", "b",
                participant("1", "/shape/RING[2]"), participant("1", "/shape/RING[1]"));
        // The lower semicircle has centre (5,0), radius 3 and lies outside the square.
        assertPoint(outside, "5", "-3");

        var inside = evidence("curved-contacts", "OutsideLens.ili", "inside.xtf",
                "GEOMETRY_INTERIOR_DISCONNECTED", "GEOMETRY_INTERIOR_DISCONNECTED", "vertex", "b",
                participant("1", shape(2, 2)), participant("1", shape(1, 3)));
        assertPoint(inside, "8", "0");
    }

    @Test
    void testsTheDiameterBeforeUsingTheIntermediatePointAndRespectsTheSignedRadius() throws IOException {
        for (var group : List.of("radius-23", "radius-24")) {
            var invalid = evidence(group, "Review.ili", "impossible.xtf",
                    "GEOMETRY_INVALID_ARC", "GEOMETRY_INVALID_ARC", "vertex", "b",
                    participant("o", "/line/PART[1]/SEGMENT[1]"));
            assertPoint(invalid, "10", "0");
            var detail = invalid.get("detail").getAsString();
            assertTrue(detail.contains("squared chord 100") && detail.contains("squared diameter 4"), detail);
        }
        passes("radius-23", "Review.ili", "signed-offset.xtf");
    }

    @Test
    void usesTheExactImplicitTrackWidthIncludingEquality() throws IOException {
        passes("implicit-width", "Review.ili", "equal.xtf");
        var overlap = evidence("implicit-width", "Review.ili", "above.xtf",
                "GEOMETRY_SELF_INTERSECTION", "GEOMETRY_SELF_INTERSECTION", "intersection", "b",
                participant("o", "/line/PART[1]/SEGMENT[1]"), participant("o", "/line/PART[1]/SEGMENT[2]"));
        // On y=x the second root is 1+10^-35/2.
        String crossing = BigDecimal.ONE.add(new BigDecimal("5e-36")).toPlainString();
        assertPoint(overlap, crossing, crossing);
        assertTrue(overlap.get("detail").getAsString().contains("exceeds tolerance 1 / sqrt(2)"));
    }

    @Test
    void includesAllBoundaryComponentsOfTheSameGap() throws IOException {
        var shell = participant("shell", shape(2, 1));
        var island = participant("island", shape(1, 1));
        var flat = evidence("gap", "CoverageGaps.ili", "Island.xtf", "SET_CONSTRAINT_FALSE", "GEOMETRY_GAP",
                "gap-boundary", "cases", shell, island);
        assertPoint(flat, "8", "2");
        for (var transfer : List.of("CurvedIsland.xtf", "TangentIsland.xtf")) {
            var curved = evidence("gap", "CoverageGaps.ili", transfer, "SET_CONSTRAINT_FALSE", "GEOMETRY_GAP",
                    "gap-boundary", "cases", shell, island);
            assertPoint(curved, "-10", "0");
        }
        var nested = evidence("gap", "CoverageGaps.ili", "NestedIslands.xtf", "SET_CONSTRAINT_FALSE", "GEOMETRY_GAP",
                "gap-boundary", "cases", shell, participant("outerIsland", shape(1, 1)));
        assertPoint(nested, "18", "2");
    }

    @Test
    void locatesTheDerivedGapBoundaryAfterAnAllowedOverlap() throws IOException {
        var gap = evidence("gap", "CoverageGaps.ili", "AlgebraicWitness.xtf", "SET_CONSTRAINT_FALSE", "GEOMETRY_GAP",
                "gap-boundary", "cases", participant("a", shape(1, 3)), participant("b", shape(1, 1)),
                participant("c", shape(1, 5)));
        var crossing = new BigDecimal("4881").divide(new BigDecimal("610"), new MathContext(68));
        assertTrue(ordinate(gap, "x").subtract(crossing).abs().compareTo(new BigDecimal("2e-34")) < 0, gap.toString());
        assertEquals(0, ordinate(gap, "y").signum());
    }

    private void passes(String group, String model, String transfer) throws IOException {
        var input = fixtures(group, model, transfer);
        var run = run(input, "--offline", "--json", transfer);
        assertEquals(0, run.exit(), run::description);
        var report = run.json();
        assertEquals("passed", report.get("status").getAsString());
        assertTrue(report.get("completed").getAsBoolean());
        assertEquals(0, report.get("violations").getAsInt());
        assertTrue(report.getAsJsonArray("unchecked").isEmpty());
    }

    private JsonObject evidence(String group, String model, String transfer, String code, String geometryCode,
                                String kind, String basket, Participant... participants) throws IOException {
        var input = fixtures(group, model, transfer);
        var run = run(input, "--offline", "--json", transfer);
        assertEquals(1, run.exit(), run::description);
        var report = run.json();
        assertTrue(report.get("completed").getAsBoolean(), run::description);
        assertEquals(1, report.get("violations").getAsInt(), run::description);
        var diagnostic = report.getAsJsonArray("diagnostics").get(0).getAsJsonObject();
        assertEquals(code, diagnostic.get("code").getAsString(), run::description);
        var evidence = diagnostic.getAsJsonObject("geometryEvidence");
        assertNotNull(evidence, run::description);
        assertEquals(geometryCode, evidence.get("code").getAsString());
        assertEquals(kind, evidence.getAsJsonObject("witness").get("kind").getAsString());
        var actual = new HashSet<Participant>();
        addParticipant(evidence.getAsJsonObject("location"), actual, basket, transfer);
        for (var location : evidence.getAsJsonArray("relatedLocations")) {
            addParticipant(location.getAsJsonObject(), actual, basket, transfer);
        }
        assertEquals(Set.of(participants), actual, evidence::toString);

        var text = run(input, "--offline", transfer);
        assertEquals(1, text.exit(), text::description);
        String xy = "XY=(" + ordinate(evidence, "x").toPlainString() + ", " + ordinate(evidence, "y").toPlainString() + ")";
        var message = text.error().lines().filter(line -> line.startsWith(code + " ") && line.contains(xy))
                .findFirst().orElseThrow(() -> new AssertionError(text.description()));
        assertTrue(message.contains(transfer + " BID=" + basket), message);
        for (var participant : participants) {
            assertTrue(message.contains("TID=" + participant.tid()) && message.contains(participant.path()), message);
        }
        return evidence;
    }

    private static void addParticipant(JsonObject location, Set<Participant> participants, String basket, String transfer) {
        assertEquals(basket, location.get("bid").getAsString());
        assertTrue(location.get("source").getAsString().endsWith("/" + transfer), location::toString);
        assertTrue(participants.add(participant(location.get("tid").getAsString(), location.get("path").getAsString())),
                location::toString);
    }

    private Path fixtures(String group, String model, String transfer) throws IOException {
        var input = Files.createDirectories(directory.resolve(group));
        for (var filename : List.of(model, transfer)) {
            try (var source = getClass().getResourceAsStream("/geometry-review/" + group + "/" + filename)) {
                assertNotNull(source, filename);
                Files.copy(source, input.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return input;
    }

    private record Participant(String tid, String path) {}

    private static Participant participant(String tid, String path) {
        return new Participant(tid, path);
    }

    private static String shape(int ring, int segment) {
        return "/shape/RING[" + ring + "]/LINE[1]/PART[1]/SEGMENT[" + segment + "]";
    }

    private static void assertPoint(JsonObject evidence, String x, String y) {
        assertEquals(0, ordinate(evidence, "x").compareTo(new BigDecimal(x)), evidence::toString);
        assertEquals(0, ordinate(evidence, "y").compareTo(new BigDecimal(y)), evidence::toString);
    }

    private static BigDecimal ordinate(JsonObject evidence, String axis) {
        var value = evidence.getAsJsonObject("witness").getAsJsonPrimitive(axis);
        assertTrue(value.isString(), evidence::toString);
        return new BigDecimal(value.getAsString());
    }

    private static Run run(Path input, String... arguments) {
        var output = new StringWriter();
        var error = new StringWriter();
        int exit = ValidatorCommand.run(List.of(arguments), input, new PrintWriter(output), new PrintWriter(error),
                CancellationToken.none());
        return new Run(exit, output.toString(), error.toString());
    }

    private record Run(int exit, String output, String error) {
        JsonObject json() { return JsonParser.parseString(output).getAsJsonObject(); }
        String description() { return output + error; }
    }
}
