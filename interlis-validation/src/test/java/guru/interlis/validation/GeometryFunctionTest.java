package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GeometryFunctionTest {
    private static final String MODEL = """
            DOMAIN Point = COORD 0.000..10.000, 0.000..10.000;
            STRUCTURE Tile =
              shape : SURFACE WITH (STRAIGHTS) VERTEX Point;
            END Tile;
            CLASS Parcel =
              tiles : BAG {0..*} OF Tile;
              active : BOOLEAN;
              SET CONSTRAINT WHERE active : INTERLIS.areAreas(ALL, >>tiles, >>Tile->shape);
              SET CONSTRAINT WHERE active : INTERLIS.areAreas2(ALL, "tiles", "shape");
              SET CONSTRAINT WHERE active : INTERLIS.areAreas3(ALL, "tiles", "shape");
              SET CONSTRAINT WHERE active : INTERLIS.areAreasWithoutGaps(ALL, >>tiles, >>Tile->shape);
              SET CONSTRAINT WHERE active : INTERLIS.areAreasWithoutGaps2(ALL, "tiles", "shape");
            END Parcel;
            """;
    private static final String TILE = """
            <Tile><shape><g:surface><g:exterior><g:polyline>
            <g:coord><g:c1>1</g:c1><g:c2>1</g:c2></g:coord>
            <g:coord><g:c1>2</g:c1><g:c2>1</g:c2></g:coord>
            <g:coord><g:c1>2</g:c1><g:c2>2</g:c2></g:coord>
            <g:coord><g:c1>1</g:c1><g:c2>1</g:c2></g:coord>
            </g:polyline></g:exterior></g:surface></shape></Tile>
            """;

    @Test
    void passesEveryHolderOfTheFilteredPopulationToAllAreaVariants() {
        List<List<GeometryEngine.Input>> selections = new ArrayList<>();
        var engine = new GeometryEngine() {
            public Check checkValue(Input input, Control control) { return Check.VALID; }
            public Measurement measure(Measure measure, Input input, Control control) { throw new AssertionError(); }
            public Check checkCoverage(Source source, CoveragePurpose purpose, Control control) {
                var inputs = new ArrayList<Input>();
                try (var cursor = source.open()) {
                    for (var value = cursor.next(); value.isPresent(); value = cursor.next()) {
                        inputs.add(value.orElseThrow());
                    }
                }
                assertTrue(source.complete());
                selections.add(List.copyOf(inputs));
                return Check.VALID;
            }
        };
        var plan = plan("2.4", MODEL, RuntimeBindings.empty().withGeometry(engine));
        var data = "<Parcel i:tid='selected'><tiles>" + TILE + "</tiles><tiles>" + TILE + "</tiles><active>true</active></Parcel>"
                + "<Parcel i:tid='excluded'><tiles>" + TILE + "</tiles><active>false</active></Parcel>"
                + "<Parcel i:tid='empty'><active>true</active></Parcel>";
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("b", data)));
        assertEquals(0, report.detectedViolations(), report.examples().toString());
        assertEquals(5, selections.size(), report.coverage().datasetRules().toString());
        for (var selection : selections) {
            assertEquals(2, selection.size());
            assertEquals(List.of(1, 2), selection.stream().map(input ->
                    ((DataLocation.Index) input.location().path().get(1)).position()).toList());
        }
    }

    @Test
    void routesLengthThroughTheBoundStandardFunction() {
        var operations = new ArrayList<GeometryEngine.Measure>();
        var engine = new GeometryEngine() {
            public Check checkValue(Input input, Control control) { return Check.VALID; }
            public Measurement measure(Measure measure, Input input, Control control) {
                operations.add(measure);
                return new Measurement.Value(5);
            }
            public Check checkCoverage(Source source, CoveragePurpose purpose, Control control) { throw new AssertionError(); }
        };
        var plan = plan("2.4", """
                DOMAIN Point = COORD 0.00..10.00, 0.00..10.00;
                CLASS Item =
                  line : POLYLINE WITH (STRAIGHTS) VERTEX Point;
                  MANDATORY CONSTRAINT INTERLIS.length(line) > 4;
                END Item;
                """, RuntimeBindings.empty().withGeometry(engine));
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("b", """
                <Item i:tid="item"><line><g:polyline>
                <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                <g:coord><g:c1>3</g:c1><g:c2>4</g:c2></g:coord>
                </g:polyline></line></Item>
                """)));
        assertEquals(List.of(GeometryEngine.Measure.LENGTH), operations);
        assertEquals(0, report.detectedViolations());
    }
}
