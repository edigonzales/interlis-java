package guru.interlis.validation.jts.internal;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.validation.*;
import guru.interlis.validation.jts.JtsGeometryEngine;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.polygonize.Polygonizer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeometryExpressionIntegrationTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String DOMAIN = "DOMAIN Point = COORD 0.000..10.000, 0.000..10.000;";
    private static final String ARC = "<g:polyline>" + point(0, 0)
            + "<g:arc><g:c1>4</g:c1><g:c2>0</g:c2><g:a1>2</g:a1><g:a2>2</g:a2><g:r>2</g:r></g:arc></g:polyline>";
    private static final String SQUARE = "<g:surface><g:exterior>" + ring(0, 0, 4, 4) + "</g:exterior></g:surface>";
    private static final String DONUT = "<g:surface><g:exterior>" + ring(0, 0, 4, 4)
            + "</g:exterior><g:interior>" + ring(1, 1, 2, 2) + "</g:interior></g:surface>";
    private static final String LEGACY_AREA_MODEL = """
            INTERLIS 2.3;
            CONTRACTED TYPE MODEL INTERLIS_ext (en) AT "http://www.interlis.ch/models" VERSION "2016-05-30" =
              FUNCTION areAreas2(Object: OBJECT OF ANYCLASS; SurfaceBag: TEXT; SurfaceAttr: TEXT): BOOLEAN;
              FUNCTION areAreas3(Objects: OBJECTS OF ANYCLASS; SurfaceBag: TEXT; SurfaceAttr: TEXT): BOOLEAN;
            END INTERLIS_ext.
            """;
    private static final String LEGACY_AREA_STRUCTURES = """
            STRUCTURE Tile =
              shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.001;
            END Tile;
            STRUCTURE Group =
              tiles : BAG {0..*} OF Tile;
            END Group;
            """;

    @Test
    void roundsSuppliedScalarsAndCoordinatesBeforeRulesInBothXtfVersions() {
        var declarations = """
                CLASS Item =
                  amount : 0.0 .. 10.0;
                  negative : -1.0 .. 0.0;
                  edge : POLYLINE WITH (STRAIGHTS) VERTEX Point;
                  MANDATORY CONSTRAINT amount == 10.0 AND negative == -1.0;
                  UNIQUE amount;
                END Item;
                """;
        var legacy = """
                <Checks.Data.Item TID="one"><amount>10.01</amount><negative>-1.05</negative>
                  <edge><POLYLINE><COORD><C1>0</C1><C2>0</C2></COORD>
                    <COORD><C1>10.0004</C1><C2>0</C2></COORD></POLYLINE></edge>
                </Checks.Data.Item>
                """;
        var current = """
                <Item i:tid="one"><amount>10.01</amount><negative>-1.05</negative>
                  <edge><g:polyline><g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                    <g:coord><g:c1>10.0004</g:c1><g:c2>0</g:c2></g:coord></g:polyline></edge>
                </Item>
                """;
        assertEquals(0, validate23(declarations, legacy).detectedViolations());
        assertEquals(0, validate24(declarations, current, true).detectedViolations());

        var exact = ValidationOptions.defaults().withNumericRounding(false);
        assertEquals(3, validate23(declarations, legacy, exact).examples().stream()
                .filter(finding -> finding.code() == ValidationCode.VALUE_OUT_OF_RANGE).count());
        assertEquals(3, validate24(declarations, current, true, exact).examples().stream()
                .filter(finding -> finding.code() == ValidationCode.VALUE_OUT_OF_RANGE).count());

        assertEquals(List.of(ValidationCode.GEOMETRY_DEGENERATE_SEGMENT),
                validate23(declarations, legacy.replace("10.0004", "0.0004")).examples().stream()
                        .map(ValidationDiagnostic::code).toList());
        assertEquals(List.of(ValidationCode.GEOMETRY_DEGENERATE_SEGMENT),
                validate24(declarations, current.replace("10.0004", "0.0004"), true).examples().stream()
                        .map(ValidationDiagnostic::code).toList());

        assertEquals(List.of(ValidationCode.DATASET_UNIQUE_DUPLICATE),
                validate24(declarations, current + current.replace("one", "two").replace("10.01", "10.02"), true)
                        .examples().stream().map(ValidationDiagnostic::code).toList());
    }

    @Test
    void preservesShallowArcIntermediatePointsInBothXtfVersions() {
        var declarations = """
                CLASS Item =
                  edge : POLYLINE WITH (ARCS) VERTEX Point;
                END Item;
                """;
        var legacy = """
                <Checks.Data.Item TID="arc"><edge><POLYLINE>
                  <COORD><C1>0</C1><C2>1</C2></COORD>
                  <ARC><C1>10.0004</C1><C2>1</C2><A1>5</A1><A2>1.0004</A2></ARC>
                </POLYLINE></edge></Checks.Data.Item>
                """;
        var current = """
                <Item i:tid="arc"><edge><g:polyline>
                  <g:coord><g:c1>0</g:c1><g:c2>1</g:c2></g:coord>
                  <g:arc><g:c1>10.0004</g:c1><g:c2>1</g:c2><g:a1>5</g:a1><g:a2>1.0004</g:a2></g:arc>
                </g:polyline></edge></Item>
                """;
        assertEquals(0, validate23(declarations, legacy).detectedViolations());
        assertEquals(0, validate24(declarations, current, true).detectedViolations());

        var exact = ValidationOptions.defaults().withNumericRounding(false);
        assertEquals(List.of(ValidationCode.VALUE_OUT_OF_RANGE),
                validate23(declarations, legacy, exact).examples().stream().map(ValidationDiagnostic::code).toList());
        assertEquals(List.of(ValidationCode.VALUE_OUT_OF_RANGE),
                validate24(declarations, current, true, exact).examples().stream().map(ValidationDiagnostic::code).toList());

        assertEquals(List.of(ValidationCode.GEOMETRY_INVALID_ARC),
                validate23(declarations, legacy.replace("1.0004", "1.0000")).examples().stream()
                        .map(ValidationDiagnostic::code).toList());
        assertEquals(List.of(ValidationCode.GEOMETRY_INVALID_ARC),
                validate24(declarations, current.replace("1.0004", "1.0000"), true).examples().stream()
                        .map(ValidationDiagnostic::code).toList());
    }

    @Test
    void checksDeliveredIntermediatePointAgainstTheXtf24Radius() {
        var declarations = """
                CLASS Item =
                  edge : POLYLINE WITH (ARCS) VERTEX Point;
                END Item;
                """;
        var contents = """
                <Item i:tid="arc"><edge><g:polyline>
                  <g:coord><g:c1>0</g:c1><g:c2>1</g:c2></g:coord>
                  <g:arc><g:c1>4</g:c1><g:c2>1</g:c2><g:a1>2</g:a1><g:a2>3.0006</g:a2><g:r>2</g:r></g:arc>
                </g:polyline></edge></Item>
                """;
        assertEquals(0, validate24(declarations, contents, true).detectedViolations());
        assertEquals(List.of(ValidationCode.GEOMETRY_INVALID_ARC),
                validate24(declarations, contents.replace("3.0006", "3.001"), true).examples().stream()
                        .map(ValidationDiagnostic::code).toList());
    }

    @Test
    void requiresSharedSegmentEndpointsAtPolylineCrossingsInBothXtfVersions() {
        var declarations = """
                CLASS Item =
                  edge : POLYLINE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.001;
                END Item;
                """;
        var legacy = "<Checks.Data.Item TID='line'><edge><POLYLINE>"
                + point23(0, 0) + point23(2, 2) + point23(4, 4) + point23(0, 4)
                + "%s" + point23(4, 0) + "</POLYLINE></edge></Checks.Data.Item>";
        var current = "<Item i:tid='line'><edge><g:polyline>"
                + point(0, 0) + point(2, 2) + point(4, 4) + point(0, 4)
                + "%s" + point(4, 0) + "</g:polyline></edge></Item>";
        assertEquals(0, validate23(declarations, legacy.formatted(point23(2, 2))).detectedViolations());
        assertEquals(0, validate24(declarations, current.formatted(point(2, 2)), true).detectedViolations());

        for (var report : List.of(validate23(declarations, legacy.formatted("")),
                validate24(declarations, current.formatted(""), true))) {
            assertFalse(report.examples().isEmpty());
            for (var finding : report.examples()) {
                assertEquals(ValidationCode.GEOMETRY_SELF_INTERSECTION, finding.code());
                assertEquals("line", finding.data().tid().orElseThrow().value());
                assertEquals(new DataLocation.Slot(finding.rule().declaration()), finding.data().path().getFirst());
                var segment = assertInstanceOf(DataLocation.GeometryComponent.class, finding.data().path().getLast());
                assertEquals(DataLocation.GeometryComponent.Kind.SEGMENT, segment.kind());
            }
        }
    }

    @Test
    void rejectsStartOnlyPolylineAtTheXtf24WireBoundary() {
        var error = assertThrows(guru.interlis.xtf24.Xtf24Exception.class, () -> validate24("""
                CLASS Item =
                  edge: POLYLINE WITH (STRAIGHTS) VERTEX Point;
                END Item;
                """, """
                <Item i:tid="line"><edge><g:polyline>
                  <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                </g:polyline></edge></Item>
                """, true));
        assertTrue(error.getMessage().contains("Polyline needs a start and at least one segment"));
    }

    @Test
    void measuresAnExplicitArcThroughAComputedGeometryAndRenamedViewBase() {
        var model = """
                CLASS Item =
                  trace : POLYLINE WITH (ARCS) VERTEX Point;
                  copy : POLYLINE WITH (ARCS) VERTEX Point := trace;
                END Item;
                VIEW Projection PROJECTION OF renamed~Item; =
                  MANDATORY CONSTRAINT INTERLIS.length(renamed->copy) > %s;
                END Projection;
                """;
        var xtf = "<Item i:tid='parcel'><trace>" + ARC + "</trace></Item>";
        assertEquals(0, validate24(model.formatted("6.2"), xtf, true).detectedViolations());
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE),
                validate24(model.formatted("6.3"), xtf, true).examples().stream().map(ValidationDiagnostic::code).toList());
        var disabled = validate24(model.formatted("6.2"), xtf, false);
        assertEquals(ValidationCoverage.ConstraintStatus.UNSUPPORTED_VALUE,
                disabled.coverage().constraints().getFirst().status());
    }

    @Test
    void distinguishesAreaFromGapFreeCoverageAndEvaluatesBothRepeatedCalls() {
        var declaration = """
                CLASS Item =
                  shape : SURFACE WITH (STRAIGHTS) VERTEX Point;
                  active : BOOLEAN;
                  SET CONSTRAINT WHERE active : INTERLIS.areAreas2(ALL,UNDEFINED,"shape");
                  SET CONSTRAINT WHERE active : INTERLIS.areAreasWithoutGaps(ALL,UNDEFINED,>>shape)
                    AND INTERLIS.areAreasWithoutGaps2(ALL,UNDEFINED,"shape");
                END Item;
                """;
        assertEquals(0, validate24(declaration, item(SQUARE, true), true).detectedViolations());
        var hole = validate24(declaration, item(DONUT, true), true);
        assertEquals(List.of(DatasetRuleCoverage.Outcome.SATISFIED, DatasetRuleCoverage.Outcome.VIOLATED),
                hole.coverage().datasetRules().stream().map(DatasetRuleCoverage::outcome).toList());
        assertEquals(0, validate24(declaration, item(DONUT, false), true).detectedViolations());
        var invalid = "<g:surface><g:exterior><g:polyline>" + point(0, 0) + point(4, 4) + point(0, 4)
                + point(4, 0) + point(0, 0) + "</g:polyline></g:exterior></g:surface>";
        assertTrue(validate24(declaration, item(invalid, true), true).examples().stream()
                .anyMatch(finding -> finding.code() == ValidationCode.GEOMETRY_SELF_INTERSECTION));
    }

    @Test
    void evaluatesAttributeAreaFunctionInXtf23() {
        var declarations = """
                CLASS Item =
                  shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.001;
                  SET CONSTRAINT INTERLIS.areAreas(ALL, UNDEFINED, >>shape);
                END Item;
                """;
        var a = object23("one", 0, 2);
        assertEquals(0, validate23(declarations, a + object23("two", 3, 5)).detectedViolations());
        var report = validate23(declarations, a + object23("two", 1, 3));
        assertEquals(List.of(ValidationCode.SET_CONSTRAINT_FALSE), codes(report));
        var evidence = report.examples().getFirst().geometryEvidence().orElseThrow();
        assertEquals(ValidationCode.GEOMETRY_BOUNDARY_MISMATCH, evidence.code());
        assertEquals(Set.of("one", "two"), Set.of(evidence.location().tid().orElseThrow().value(),
                evidence.relatedData().orElseThrow().tid().orElseThrow().value()));
    }

    @Test
    void checksGapFreeSingletonAndReportsTheBoundaryOnTheFailedConstraint() {
        var model = """
                CLASS Item =
                  shape : SURFACE WITH (STRAIGHTS) VERTEX Point;
                  active : BOOLEAN;
                  MANDATORY CONSTRAINT INTERLIS.areAreasWithoutGaps(THIS, UNDEFINED, >>shape);
                END Item;
                """;
        var valid = validate24(model, item(SQUARE, true), true);
        assertEquals(0, valid.detectedViolations(), valid.examples().toString());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, valid.coverage().constraints().getFirst().status());

        var invalid = validate24(model, item(DONUT, true), true);
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE), codes(invalid));
        var evidence = invalid.examples().getFirst().geometryEvidence().orElseThrow();
        assertEquals(ValidationCode.GEOMETRY_GAP, evidence.code());
        assertEquals(GeometryWitness.Kind.GAP_BOUNDARY, evidence.witness().kind());
        assertEquals("parcel", evidence.location().tid().orElseThrow().value());
        assertTrue(evidence.location().path().contains(new DataLocation.GeometryComponent(
                DataLocation.GeometryComponent.Kind.RING, 2)));
    }

    @Test
    void attachesGeometryEvidenceOnlyWhenItExplainsTheFailedBooleanRule() {
        var model = """
                CLASS Item =
                  shape : SURFACE WITH (STRAIGHTS) VERTEX Point;
                  active : BOOLEAN;
                  %s
                END Item;
                """;
        var check = "INTERLIS.areAreasWithoutGaps(THIS, UNDEFINED, >>shape)";
        var donut = item(DONUT, true);
        for (var condition : List.of(check + " OR #true", "NOT (" + check + ")")) {
            var report = validate24(model.formatted("MANDATORY CONSTRAINT " + condition + ";"), donut, true);
            assertEquals(0, report.detectedViolations(), report.examples().toString());
            assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, report.coverage().constraints().getFirst().status());
        }
        var unrelated = validate24(model.formatted("MANDATORY CONSTRAINT NOT (" + check + " OR #true);"), donut, true);
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE), codes(unrelated));
        assertTrue(unrelated.examples().getFirst().geometryEvidence().isEmpty());

        var caused = validate24(model.formatted("MANDATORY CONSTRAINT NOT (NOT (" + check + "));"), donut, true);
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE), codes(caused));
        assertEquals(ValidationCode.GEOMETRY_GAP,
                caused.examples().getFirst().geometryEvidence().orElseThrow().code());

        var set = "SET CONSTRAINT WHERE " + check + ": INTERLIS.objectCount(ALL) ";
        var filtered = validate24(model.formatted(set + "== 0;"), donut, true);
        assertEquals(0, filtered.detectedViolations(), filtered.examples().toString());
        var emptySelection = validate24(model.formatted(set + "> 0;"), donut, true);
        assertEquals(List.of(ValidationCode.SET_CONSTRAINT_FALSE), codes(emptySelection));
        assertTrue(emptySelection.examples().getFirst().geometryEvidence().isEmpty());
    }

    @Test
    void carriesTheGapThroughAFailedPlausibilityThreshold() {
        var model = """
                CLASS Item =
                  shape : SURFACE WITH (STRAIGHTS) VERTEX Point;
                  active : BOOLEAN;
                  CONSTRAINT >= 100% INTERLIS.areAreasWithoutGaps(THIS, UNDEFINED, >>shape);
                  CONSTRAINT <= 0% NOT (INTERLIS.areAreasWithoutGaps(THIS, UNDEFINED, >>shape));
                END Item;
                """;
        var report = validate24(model, item(DONUT, true), true);
        assertEquals(List.of(ValidationCode.PLAUSIBILITY_CONSTRAINT_FALSE, ValidationCode.PLAUSIBILITY_CONSTRAINT_FALSE),
                codes(report));
        for (var finding : report.examples()) {
            assertEquals(ValidationCode.GEOMETRY_GAP, finding.geometryEvidence().orElseThrow().code());
        }
    }

    @Test
    void checksLegacyAreasPerObjectOrAcrossTheSelectedSet() {
        var item = LEGACY_AREA_STRUCTURES + """
                CLASS Item =
                  groups : LIST {0..*} OF Group;
                  %s
                END Item;
                """;
        var single = item.formatted("""
                MANDATORY CONSTRAINT INTERLIS_ext.areAreas2(THIS,UNDEFINED,"groups->tiles->shape");
                """);
        var set = item.formatted("""
                SET CONSTRAINT INTERLIS_ext.areAreas3(ALL,UNDEFINED,"groups->tiles->shape");
                """);
        var separateObjects = legacyItem("a", legacyGroup(tile23(0, 3))) + legacyItem("b", legacyGroup(tile23(2, 5)));
        var withinObject = legacyItem("a", legacyGroup(tile23(0, 3), tile23(2, 5)));
        var local = validateLegacyAreas(single, separateObjects, ValidationProfile.LOCAL_ONLY);
        assertEquals(0, local.detectedViolations());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, local.coverage().constraints().getFirst().status());
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE),
                codes(validateLegacyAreas(single, withinObject, ValidationProfile.LOCAL_ONLY)));
        assertEquals(List.of(ValidationCode.SET_CONSTRAINT_FALSE),
                codes(validateLegacyAreas(set, separateObjects, ValidationProfile.DATASET)));
    }

    @Test
    void keepsLegacyAllNativeWhenAHostFunctionIsRegistered() {
        var declarations = LEGACY_AREA_STRUCTURES + """
                CLASS Item =
                  groups : LIST {0..*} OF Group;
                  SET CONSTRAINT INTERLIS_ext.areAreas3(ALL,UNDEFINED,"groups->tiles->shape");
                END Item;
                """;
        var data = transfer23(legacyItem("a", legacyGroup(tile23(0, 3)))
                + legacyItem("b", legacyGroup(tile23(2, 5))));
        var report = validate("2.3", declarations, data, true, ValidationOptions.defaults(),
                ValidationProfile.DATASET, true, true);
        assertEquals(List.of(ValidationCode.SET_CONSTRAINT_FALSE), codes(report));
        var dynamicPath = declarations.replace("\"groups->tiles->shape\"", "INTERLIS.trim(\"groups->tiles->shape\")");
        var dynamic = validate("2.3", dynamicPath, data, true, ValidationOptions.defaults(),
                ValidationProfile.DATASET, true, true);
        assertEquals(DatasetRuleCoverage.Outcome.UNSUPPORTED, dynamic.coverage().datasetRules().getFirst().outcome());
    }

    @Test
    void checksEachLegacySurfaceBagGroupSeparately() {
        var item = LEGACY_AREA_STRUCTURES + """
                CLASS Item =
                  groups : LIST {0..*} OF Group;
                  %s
                END Item;
                """;
        var across = legacyItem("a", legacyGroup(tile23(0, 3)), legacyGroup(tile23(2, 5)), legacyGroup());
        var within = legacyItem("a", legacyGroup(tile23(0, 3), tile23(2, 5)));
        var single = item.formatted("""
                MANDATORY CONSTRAINT INTERLIS_ext.areAreas2(THIS,"groups","tiles->shape");
                """);
        var set = item.formatted("""
                SET CONSTRAINT INTERLIS_ext.areAreas3(ALL,"groups","tiles->shape");
                """);
        var local = validateLegacyAreas(single, across, ValidationProfile.LOCAL_ONLY);
        assertEquals(0, local.detectedViolations());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, local.coverage().constraints().getFirst().status());
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE),
                codes(validateLegacyAreas(single, within, ValidationProfile.LOCAL_ONLY)));
        var dataset = validateLegacyAreas(set, across, ValidationProfile.DATASET);
        assertEquals(0, dataset.detectedViolations());
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, dataset.coverage().datasetRules().getFirst().outcome());
        assertEquals(List.of(ValidationCode.SET_CONSTRAINT_FALSE),
                codes(validateLegacyAreas(set, within, ValidationProfile.DATASET)));
    }

    @Test
    void measuresCurvedSurfaceAndEveryMultiComponentInExpressions() {
        var halfDisc = "<g:surface><g:exterior>" + ARC.replace("</g:polyline>", point(0, 0) + "</g:polyline>")
                + "</g:exterior></g:surface>";
        var model = """
                CLASS Item =
                  shape : SURFACE WITH (STRAIGHTS,ARCS) VERTEX Point;
                  lines : MULTIPOLYLINE WITH (ARCS) VERTEX Point;
                  shapes : MULTISURFACE WITH (STRAIGHTS) VERTEX Point;
                  MANDATORY CONSTRAINT INTERLIS.surface(shape) > 6.2 AND INTERLIS.surface(shape) < 6.3;
                  MANDATORY CONSTRAINT INTERLIS.multilength(lines) > 12.5 AND INTERLIS.multilength(lines) < 12.6;
                  MANDATORY CONSTRAINT INTERLIS.multisurface(shapes) == 17;
                END Item;
                """;
        var small = "<g:surface><g:exterior>" + ring(5, 5, 6, 6) + "</g:exterior></g:surface>";
        var data = "<Item i:tid='parcel'><shape>" + halfDisc + "</shape><lines><g:multipolyline>"
                + ARC + ARC + "</g:multipolyline></lines><shapes><g:multisurface>" + SQUARE + small
                + "</g:multisurface></shapes></Item>";
        var report = validate24(model, data, true);
        assertEquals(0, report.detectedViolations(), report.examples().toString());
        assertEquals(3, report.coverage().constraints().stream()
                .filter(result -> result.status() == ValidationCoverage.ConstraintStatus.CHECKED).count());
    }

    @Test
    void checksEmptySingleAndDuplicateBagWithTheRealEngine() {
        var model = """
                STRUCTURE Piece = shape : SURFACE WITH (STRAIGHTS) VERTEX Point; END Piece;
                CLASS Item =
                  pieces : BAG {0..*} OF Piece;
                  SET CONSTRAINT INTERLIS.areAreas2(ALL,"pieces","shape");
                END Item;
                """;
        var piece = "<pieces><Piece><shape>" + SQUARE + "</shape></Piece></pieces>";
        for (int count : List.of(0, 1, 2)) {
            var report = validate24(model, "<Item i:tid='parcel'>" + piece.repeat(count) + "</Item>", true);
            var outcome = count == 2 ? DatasetRuleCoverage.Outcome.VIOLATED : DatasetRuleCoverage.Outcome.SATISFIED;
            assertEquals(outcome, report.coverage().datasetRules().getFirst().outcome());
        }
    }

    @Test
    void reportsOpenWhereContextInsteadOfTreatingItAsAnEmptySet() {
        var model = """
                CLASS Flag = enabled : BOOLEAN; END Flag;
                STRUCTURE Link = flag : REFERENCE TO Flag; END Link;
                CLASS Item =
                  shape : SURFACE WITH (STRAIGHTS) VERTEX Point;
                  link : Link;
                  SET CONSTRAINT WHERE link->flag->enabled : INTERLIS.areAreas2(ALL,UNDEFINED,"shape");
                END Item;
                """;
        var data = "<Item i:tid='parcel'><shape>" + SQUARE
                + "</shape><link><Link><flag i:ref='missing'/></Link></link></Item>";
        var report = validate24(model, data, true);
        var rule = report.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.Outcome.UNDETERMINED, rule.outcome());
        assertEquals(1, rule.undecidedCandidates());
        assertTrue(report.examples().stream().noneMatch(finding -> finding.code() == ValidationCode.SET_CONSTRAINT_FALSE));
    }

    @Test
    void acceptsTheOriginalPermissibleAreaOverlaps() throws IOException {
        var model = fixture("iox-overlap/Overlap23.ili");
        for (var name : List.of("Area_1Boundary_NoOverlap", "Area_1Boundary_PermissibleOverlap",
                "Area_3Boundaries_PermissibleArcOverlaps", "Area_3Boundaries_PermissibleOverlap_GH366")) {
            var report = validateFixture("2.3", "Overlap23", model, fixture("iox-overlap/" + name + ".xtf"));
            assertEquals(0, report.detectedViolations(), () -> name + ": " + report.examples());
            assertEquals(DatasetRuleCoverage.Outcome.SATISFIED,
                    report.coverage().datasetRules().getFirst().outcome(), name);
        }
    }

    @Test
    void requiresBothRingsToHaveASupportPointAtTheirContact() throws IOException {
        var report = validateFixture("2.3", "Overlap23", fixture("iox-overlap/Overlap23.ili"),
                fixture("iox-overlap/Area_2Boundaries_NoOverlap.xtf"));
        var evidence = report.examples().stream()
                .filter(finding -> finding.code() == ValidationCode.GEOMETRY_SELF_INTERSECTION)
                .map(finding -> finding.geometryEvidence().orElseThrow()).findFirst().orElseThrow();
        assertEquals(GeometryWitness.Kind.INTERSECTION, evidence.witness().kind());
        assertEquals(2600000.100, evidence.witness().x().doubleValue(), 1e-8);
        assertEquals(1050011.411, evidence.witness().y().doubleValue(), 0.001);
    }

    @Test
    void acceptsTheNoiseZonesWithEitherAreaRule() throws IOException {
        var model = fixture("NoiseZones.ili");
        var byConstraint = model.replace("shape : AREA", "shape : SURFACE").replace("END Zone;", """
                SET CONSTRAINT INTERLIS.areAreas(ALL, UNDEFINED, >>shape);
                END Zone;""");
        var data = fixture("NoiseZones.xtf");
        for (var variant : List.of(model, byConstraint)) {
            var report = validateFixture("2.3", "NoiseZones", variant, data);
            assertEquals(2, report.inspectedObjects());
            assertEquals(0, report.detectedViolations(), report.examples().toString());
            assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, report.coverage().datasetRules().getFirst().outcome());
        }
    }

    @Test
    void distinguishesBoundedGapsFromTheOutsideAfterToleratedOverlaps() throws IOException {
        var model = fixture("CoverageGaps.ili");
        var ordinaryArea = model.replace("areAreasWithoutGaps", "areAreas");
        for (var name : List.of("ThinStraightGap", "GapBetweenArcAndStraight", "GapAfterToleratedOverlap",
                "EnclaveWithToleratedOverlap", "OverlapOpenToOutside")) {
            var data = fixture(name + ".xtf");
            var area = validateFixture("2.4", "CoverageGaps", ordinaryArea, data);
            assertEquals(0, area.detectedViolations(), () -> name + ": " + area.examples());
            assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, area.coverage().datasetRules().getFirst().outcome(), name);

            var gaps = validateFixture("2.4", "CoverageGaps", model, data);
            if (name.equals("OverlapOpenToOutside")) {
                assertEquals(0, gaps.detectedViolations(), gaps.examples().toString());
                assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, gaps.coverage().datasetRules().getFirst().outcome());
            } else {
                assertEquals(List.of(ValidationCode.SET_CONSTRAINT_FALSE), codes(gaps), name);
                var evidence = gaps.examples().getFirst().geometryEvidence().orElseThrow();
                assertEquals(ValidationCode.GEOMETRY_GAP, evidence.code(), name);
                assertEquals(GeometryWitness.Kind.GAP_BOUNDARY, evidence.witness().kind(), name);
                assertTrue(evidence.location().tid().isPresent(), name);
                if (name.equals("GapAfterToleratedOverlap")) {
                    var participants = new java.util.HashSet<String>();
                    participants.add(evidence.location().tid().orElseThrow().value());
                    evidence.relatedLocations().forEach(location -> participants.add(location.tid().orElseThrow().value()));
                    assertEquals(Set.of("a", "b", "c"), participants);
                }
            }
        }
    }

    @Test
    void appliesTheOverlapToleranceToTheActualNearCrossingFromIssue476() throws IOException {
        // INTERLIS 2.3 §2.8.12. The common vertex and the independently calculated second
        // root (0.433 mm away) bound a lens that is permitted at .050. Use finer model
        // precision for the rejection case so the tolerance can be positive and below the sagitta.
        var model = fixture("NearArcOverlap.ili");
        var transfer = fixture("NearArcOverlap.xtf");
        var tolerated = validateFixture("2.3", "NearArcOverlap", model, transfer);
        assertEquals(0, tolerated.detectedViolations(), tolerated.examples().toString());
        var finerModel = model.replace(".000", ".000000000").replace("0.050", "0.000000001");
        var rejected = validateFixture("2.3", "NearArcOverlap", finerModel, transfer);
        assertEquals(List.of(ValidationCode.GEOMETRY_SELF_INTERSECTION), codes(rejected));
        var witness = rejected.examples().getFirst().geometryEvidence().orElseThrow().witness();
        assertEquals(GeometryWitness.Kind.INTERSECTION, witness.kind());
        assertEquals(0.000433336, Math.hypot(witness.x().doubleValue() - 2754777.422,
                witness.y().doubleValue() - 1260634.983), 1e-9);
    }

    @Test
    void polygonizesTheThreeStraightEdgesOfTheThinGap() {
        var start = new Coordinate(2716165.803, 1280939.889);
        var offset = new Coordinate(2716166.623, 1280939.467);
        var end = new Coordinate(2716177.128, 1280934.062);
        var factory = new GeometryFactory();
        var polygonizer = new Polygonizer();
        polygonizer.add(List.of(factory.createLineString(new Coordinate[]{start, offset}),
                factory.createLineString(new Coordinate[]{offset, end}),
                factory.createLineString(new Coordinate[]{end, start})));
        assertEquals(1, polygonizer.getPolygons().size());
        assertEquals(0.000505, polygonizer.getGeometry().getArea(), 1e-8);
    }

    @Test
    void identifiesTheSameCurvedGapAfterRingRotationReversalAndTranslation() throws IOException {
        // INTERLIS 2.4 §3.8.13.3 and §3.14. The arc and vertical chord enclose the same lens in every ordering.
        var model = fixture("CoverageGaps.ili");
        var transfer = fixture("GapBetweenArcAndStraight.xtf");
        int firstItem = transfer.indexOf("<Item");
        int afterLastItem = transfer.lastIndexOf("</Item>") + "</Item>".length();
        for (int offset : List.of(0, 1_000_000)) {
            var a = point(offset, offset);
            var b = point(offset + 10, offset);
            var c = point(offset + 10, offset + 10);
            var d = point(offset, offset + 10);
            var toB = arcPoint(offset + 10, offset, offset + 8, offset + 5);
            var toC = arcPoint(offset + 10, offset + 10, offset + 8, offset + 5);
            var right = point(offset + 20, offset) + point(offset + 20, offset + 10);
            for (var ring : List.of(a + b + toC + d + a, b + toC + d + a + b, d + c + toB + a + d)) {
                var contents = coverageItem("curved", ring) + coverageItem("straight", c + b + right + c);
                var data = transfer.substring(0, firstItem) + contents + transfer.substring(afterLastItem);
                var report = validateFixture("2.4", "CoverageGaps", model, data);
                assertEquals(List.of(ValidationCode.SET_CONSTRAINT_FALSE), codes(report));
                var evidence = report.examples().getFirst().geometryEvidence().orElseThrow();
                assertEquals(GeometryWitness.Kind.GAP_BOUNDARY, evidence.witness().kind());
                assertEquals(offset + 10, evidence.witness().x().intValueExact());
                assertTrue(Set.of(offset, offset + 10).contains(evidence.witness().y().intValueExact()));
            }
        }
    }

    private static String coverageItem(String id, String ring) {
        return "<Item i:tid='" + id + "'><shape><g:surface><g:exterior><g:polyline>" + ring
                + "</g:polyline></g:exterior></g:surface></shape></Item>";
    }

    private static String arcPoint(int x, int y, int ax, int ay) {
        return "<g:arc><g:c1>" + x + "</g:c1><g:c2>" + y + "</g:c2><g:a1>" + ax
                + "</g:a1><g:a2>" + ay + "</g:a2></g:arc>";
    }

    private static String fixture(String name) throws IOException {
        try (var stream = GeometryExpressionIntegrationTest.class.getResourceAsStream("/geometry/" + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ValidationReport validateFixture(String version, String name, String model, String xtf) {
        var source = new SourceText(new SourceId("memory:/" + name + ".ili"), new SourceRevision(1), model);
        return validateModel(version, name, source, xtf, true, ValidationOptions.defaults(),
                ValidationProfile.DATASET, false, false);
    }

    private static ValidationReport validate24(String declarations, String contents, boolean engine) {
        return validate24(declarations, contents, engine, ValidationOptions.defaults());
    }

    private static ValidationReport validate24(String declarations, String contents, boolean engine,
                                               ValidationOptions options) {
        var xtf = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                  xmlns="http://www.interlis.ch/xtf/2.4/Checks" xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection><Data i:bid="basket">%s</Data></i:datasection>
                </i:transfer>
                """.formatted(contents);
        return validate("2.4", declarations, xtf, engine, options);
    }

    private static ValidationReport validate23(String declarations, String contents) {
        return validate23(declarations, contents, ValidationOptions.defaults());
    }

    private static ValidationReport validate23(String declarations, String contents, ValidationOptions options) {
        return validate("2.3", declarations, transfer23(contents), true, options);
    }

    private static String transfer23(String contents) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="Checks" VERSION="1" URI="https://example.org/checks"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION><Checks.Data BID="basket">%s</Checks.Data></DATASECTION>
                </TRANSFER>
                """.formatted(contents);
    }

    private static ValidationReport validateLegacyAreas(String declarations, String contents, ValidationProfile profile) {
        return validate("2.3", declarations, transfer23(contents), true, ValidationOptions.defaults(), profile, true, false);
    }

    private static ValidationReport validate(String version, String declarations, String xtf, boolean engine,
                                             ValidationOptions options) {
        return validate(version, declarations, xtf, engine, options, ValidationProfile.DATASET, false, false);
    }

    private static ValidationReport validate(String version, String declarations, String xtf, boolean engine,
                                             ValidationOptions options, ValidationProfile profile, boolean legacyAreas,
                                             boolean hostAreaSet) {
        var id = new SourceId("memory:/Checks.ili");
        var source = new SourceText(id, new SourceRevision(1), """
                %s
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  %s %s TOPIC Data = %s END Data;
                END Checks.
                """.formatted(legacyAreas ? LEGACY_AREA_MODEL : "INTERLIS " + version + ";",
                legacyAreas ? "IMPORTS INTERLIS_ext;" : "", DOMAIN, declarations));
        return validateModel(version, "Checks", source, xtf, engine, options, profile, legacyAreas, hostAreaSet);
    }

    private static ValidationReport validateModel(String version, String model, SourceText source, String xtf,
                                                   boolean engine, ValidationOptions options,
                                                   ValidationProfile profile, boolean legacyAreas, boolean hostAreaSet) {
        var request = new CompilationRequest(List.of(new ModelSelector.ByName(model, Optional.empty(), Optional.empty())),
                legacyAreas ? withLegacyImport(source) : SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        var compiled = CompilerEngines.create().compile(request, TOKEN);
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, compiled,
                () -> compiled instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().diagnostics().toString() : compiled.toString()).schema();
        var transfer = assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema)).plan();
        var bindings = engine ? RuntimeBindings.empty().withGeometry(JtsGeometryEngine.standard()) : RuntimeBindings.empty();
        if (hostAreaSet) {
            var function = schema.semanticGraph().symbols().stream()
                    .filter(guru.interlis.model.FunctionSymbol.class::isInstance)
                    .map(guru.interlis.model.FunctionSymbol.class::cast)
                    .filter(symbol -> symbol.name().value().equals("areAreas3")).findFirst().orElseThrow();
            var ref = new guru.interlis.model.SymbolRef(schema.semanticGraph().snapshotId(), function.id());
            bindings = new RuntimeBindings(Map.of(), Map.of(ref, new FunctionBinding(function, "test", "1",
                    (arguments, context, token) -> fail("ALL must use the native evaluator"))), bindings.geometry());
        }
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, profile, bindings)).plan();
        var bytes = new ByteArrayInputStream(xtf.getBytes(StandardCharsets.UTF_8));
        try (var reader = version.equals("2.3") ? new Xtf23Reader(transfer, bytes) : new Xtf24Reader(transfer, bytes)) {
            if (profile == ValidationProfile.LOCAL_ONLY) {
                return LocalValidator.validate(plan, reader, options, TOKEN);
            }
            try (var storage = new InMemoryValidationStorage(1000, 1_000_000);
                 var validation = DatasetValidator.session(plan,
                         new DatasetOptions(options, DatasetLimits.defaults(), DatasetScope.SUPPLIED_DATASET), storage)) {
                validation.readInput(new InputDescriptor(new InputId("data"), InputRole.PRIMARY), reader, TOKEN);
                return validation.finish(TOKEN);
            }
        }
    }

    private static List<ValidationCode> codes(ValidationReport report) {
        return report.examples().stream().map(ValidationDiagnostic::code).toList();
    }

    private static SourceSet withLegacyImport(SourceText source) {
        var models = SyntaxServices.create().parse(source, ParseOptions.defaults(ParseMode.EDITOR), TOKEN).root().models();
        var extension = models.getFirst();
        var importer = models.getLast();
        var site = new ImportSite(importer.imports().getFirst().range(), importer.name(), extension.name());
        var target = new ModelLocation(source.id(), source.revision(), extension.name(),
                Optional.of(extension.modelVersion()), extension.range().startOffset());
        return SourceSet.of(Map.of(source.id(), source),
                List.of(new ImportBinding(site, new ImportResolution.Resolved(target))), List.of(), ResolutionFingerprint.none());
    }

    private static String legacyItem(String id, String... groups) {
        return "<Checks.Data.Item TID='" + id + "'><groups>" + String.join("", groups) + "</groups></Checks.Data.Item>";
    }

    private static String legacyGroup(String... tiles) {
        return "<Checks.Data.Group>" + (tiles.length == 0 ? "" : "<tiles>" + String.join("", tiles) + "</tiles>")
                + "</Checks.Data.Group>";
    }

    private static String tile23(int x0, int x1) {
        return "<Checks.Data.Tile>" + surface23(x0, x1) + "</Checks.Data.Tile>";
    }

    private static String item(String shape, boolean active) {
        return "<Item i:tid='parcel'><shape>" + shape + "</shape><active>" + active + "</active></Item>";
    }

    private static String ring(int x0, int y0, int x1, int y1) {
        return "<g:polyline>" + point(x0, y0) + point(x1, y0) + point(x1, y1) + point(x0, y1)
                + point(x0, y0) + "</g:polyline>";
    }

    private static String point(int x, int y) {
        return "<g:coord><g:c1>" + x + "</g:c1><g:c2>" + y + "</g:c2></g:coord>";
    }

    private static String object23(String id, int x0, int x1) {
        return "<Checks.Data.Item TID='" + id + "'>" + surface23(x0, x1) + "</Checks.Data.Item>";
    }

    private static String surface23(int x0, int x1) {
        return "<shape><SURFACE><BOUNDARY><POLYLINE>"
                + point23(x0, 0) + point23(x1, 0) + point23(x1, 2) + point23(x0, 2) + point23(x0, 0)
                + "</POLYLINE></BOUNDARY></SURFACE></shape>";
    }

    private static String point23(int x, int y) {
        return "<COORD><C1>" + x + "</C1><C2>" + y + "</C2></COORD>";
    }
}
