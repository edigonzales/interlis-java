package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetGeometryTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String AREA = "AREA WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.002";
    private static final String POINT = "DOMAIN Point = COORD 0.000 .. 100.000, 0.000 .. 100.000;";
    private static final String POLYGON = """
            <shape><g:surface><g:exterior><g:polyline>
            <g:coord><g:c1>1</g:c1><g:c2>1</g:c2></g:coord>
            <g:coord><g:c1>2</g:c1><g:c2>1</g:c2></g:coord>
            <g:coord><g:c1>2</g:c1><g:c2>2</g:c2></g:coord>
            <g:coord><g:c1>1</g:c1><g:c2>1</g:c2></g:coord>
            </g:polyline></g:exterior></g:surface></shape>
            """;

    @Test
    void referenceOnlyAreaHasAnEmptyPrimaryPopulationWithoutReplayingObjects() {
        var engine = new CoverageProbe();
        var plan = plan("2.4", POINT + """
                CLASS Other = END Other;
                CLASS Parcel = shape: %s; END Parcel;
                """.formatted(AREA), RuntimeBindings.empty().withGeometry(engine));
        var rows = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            rows.append("<Other i:tid='other").append(i).append("'/>");
        }
        var defaults = DatasetLimits.defaults();
        var limits = new DatasetLimits(defaults.maxInputs(), defaults.maxBaskets(), defaults.maxObjects(),
                defaults.maxRecordBytes(), defaults.maxRelationWork(), 128, defaults.maxKeyBytes());

        for (var scope : DatasetScope.values()) {
            var options = new DatasetOptions(ValidationOptions.defaults(), limits, scope);
            try (var storage = new InMemoryValidationStorage(10_000, 10_000_000);
                 var session = DatasetValidator.session(plan, options, storage);
                 var reference = reader(plan, basket("ref", "<Parcel i:tid='r'>" + POLYGON + "</Parcel>"));
                 var primary = reader(plan, basket("data", rows.toString()))) {
                session.readInput(new InputDescriptor(new InputId("ref"), InputRole.REFERENCE), reference, TOKEN);
                session.readInput(new InputDescriptor(new InputId("primary"), InputRole.PRIMARY), primary, TOKEN);
                var report = session.finish(TOKEN);
                assertEquals(256, report.inspectedObjects());
                assertEquals(0, report.detectedViolations());
                var coverage = report.coverage().datasetRules().getFirst();
                assertEquals(scope == DatasetScope.SUPPLIED_DATASET ? DatasetRuleCoverage.Outcome.SATISFIED
                        : DatasetRuleCoverage.Outcome.NO_VIOLATION_IN_AVAILABLE_SCOPE, coverage.outcome());
                assertEquals(1, coverage.scopes());
                assertEquals(0, coverage.candidates());
                assertEquals(0, coverage.evaluations());
            }
        }
        assertEquals(List.of(List.of(), List.of()), engine.paths);
    }

    @Test
    void includesInheritedAndNestedAreaFieldsInTheSelectedPopulation() {
        var engine = new CoverageProbe();
        var plan = plan("2.4", POINT + """
                STRUCTURE Patch = shape: %1$s; END Patch;
                CLASS Holder = patch: Patch; END Holder;
                CLASS Base (ABSTRACT) = shape: %1$s; END Base;
                CLASS Parcel EXTENDS Base = END Parcel;
                """.formatted(AREA), RuntimeBindings.empty().withGeometry(engine));
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("b",
                "<Holder i:tid='h'><patch><Patch>" + POLYGON + "</Patch></patch></Holder>"
                        + "<Parcel i:tid='p'>" + POLYGON + "</Parcel>")));

        assertEquals(2, report.inspectedObjects());
        assertEquals(0, report.detectedViolations());
        assertEquals(List.of(1L, 1L), report.coverage().datasetRules().stream()
                .map(DatasetRuleCoverage::evaluations).toList());
        assertEquals(List.of(1, 2), engine.paths.stream().map(List::getFirst).sorted().toList());
    }

    private static final class CoverageProbe implements GeometryEngine {
        private final List<List<Integer>> paths = new ArrayList<>();

        @Override public Check checkValue(Input input, Control control) { return Check.VALID; }

        @Override public Measurement measure(Measure operation, Input input, Control control) {
            throw new AssertionError();
        }

        @Override public Check checkCoverage(Source source, CoveragePurpose purpose, Control control) {
            assertEquals(CoveragePurpose.AREA, purpose);
            var locations = read(source);
            assertEquals(locations, read(source));
            paths.add(locations);
            return source.complete() ? Check.VALID : Check.MISSING_PARAMETERS;
        }

        private List<Integer> read(Source source) {
            var result = new ArrayList<Integer>();
            try (var cursor = source.open()) {
                for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                    result.add(next.orElseThrow().location().path().size());
                }
            }
            return result;
        }
    }
}
