package guru.interlis.validation.jts.internal;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.validation.*;
import guru.interlis.validation.jts.JtsGeometryEngine;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GeometryExpressionIntegrationTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String DOMAIN = "DOMAIN Point = COORD 0.000..10.000, 0.000..10.000;";
    private static final String ARC = "<g:polyline>" + point(0, 0)
            + "<g:arc><g:c1>4</g:c1><g:c2>0</g:c2><g:a1>2</g:a1><g:a2>2</g:a2><g:r>2</g:r></g:arc></g:polyline>";
    private static final String SQUARE = "<g:surface><g:exterior>" + ring(0, 0, 4, 4) + "</g:exterior></g:surface>";
    private static final String DONUT = "<g:surface><g:exterior>" + ring(0, 0, 4, 4)
            + "</g:exterior><g:interior>" + ring(1, 1, 2, 2) + "</g:interior></g:surface>";

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
        assertEquals(List.of(ValidationCode.SET_CONSTRAINT_FALSE), validate23(declarations, a + object23("two", 1, 3))
                .examples().stream().map(ValidationDiagnostic::code).toList());
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

    private static ValidationReport validate24(String declarations, String contents, boolean engine) {
        var xtf = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                  xmlns="http://www.interlis.ch/xtf/2.4/Checks" xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection><Data i:bid="basket">%s</Data></i:datasection>
                </i:transfer>
                """.formatted(contents);
        return validate("2.4", declarations, xtf, engine);
    }

    private static ValidationReport validate23(String declarations, String contents) {
        var xtf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="Checks" VERSION="1" URI="https://example.org/checks"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION><Checks.Data BID="basket">%s</Checks.Data></DATASECTION>
                </TRANSFER>
                """.formatted(contents);
        return validate("2.3", declarations, xtf, true);
    }

    private static ValidationReport validate(String version, String declarations, String xtf, boolean engine) {
        var id = new SourceId("memory:/Checks.ili");
        var source = new SourceText(id, new SourceRevision(1), """
                INTERLIS %s;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  %s TOPIC Data = %s END Data;
                END Checks.
                """.formatted(version, DOMAIN, declarations));
        var request = new CompilationRequest(List.of(new ModelSelector.ByName("Checks", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(id, source)), CompilerOptions.defaults());
        var compiled = CompilerEngines.create().compile(request, TOKEN);
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, compiled,
                () -> compiled instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().diagnostics().toString() : compiled.toString()).schema();
        var transfer = assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema)).plan();
        var bindings = engine ? RuntimeBindings.empty().withGeometry(JtsGeometryEngine.standard()) : RuntimeBindings.empty();
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET, bindings)).plan();
        var bytes = new ByteArrayInputStream(xtf.getBytes(StandardCharsets.UTF_8));
        try (var reader = version.equals("2.3") ? new Xtf23Reader(transfer, bytes) : new Xtf24Reader(transfer, bytes);
             var storage = new InMemoryValidationStorage(1000, 1_000_000);
             var validation = DatasetValidator.session(plan, DatasetOptions.defaults(), storage)) {
            validation.readInput(new InputDescriptor(new InputId("data"), InputRole.PRIMARY), reader, TOKEN);
            return validation.finish(TOKEN);
        }
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
        return "<Checks.Data.Item TID='" + id + "'><shape><SURFACE><BOUNDARY><POLYLINE>"
                + point23(x0, 0) + point23(x1, 0) + point23(x1, 2) + point23(x0, 2) + point23(x0, 0)
                + "</POLYLINE></BOUNDARY></SURFACE></shape></Checks.Data.Item>";
    }

    private static String point23(int x, int y) {
        return "<COORD><C1>" + x + "</C1><C2>" + y + "</C2></COORD>";
    }
}
