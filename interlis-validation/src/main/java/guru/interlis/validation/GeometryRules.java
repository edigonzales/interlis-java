package guru.interlis.validation;

import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.Origin;
import guru.interlis.transfer.TransferGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static guru.interlis.validation.DataLocation.GeometryComponent.Kind.*;
import static guru.interlis.validation.ValidationCode.*;

final class GeometryRules {
    @FunctionalInterface
    interface Findings {
        void report(ValidationCode code, List<DataLocation.GeometryComponent> path, List<String> arguments);
    }

    private final CoordinateType vertices;
    private final List<String> forms;
    private final Runnable visit;
    private final Findings findings;
    private final BiConsumer<TransferGeometry.Coordinate, List<DataLocation.GeometryComponent>> verticesFound;
    private boolean fullyChecked;
    private final boolean checkRadiusSeparately;

    GeometryRules(CoordinateType vertices, List<String> forms, Runnable visit, Findings findings) {
        this(vertices, forms, visit, findings, (coordinate, path) -> { }, false);
    }

    GeometryRules(CoordinateType vertices, List<String> forms, Runnable visit, Findings findings,
                  boolean checkRadiusSeparately) {
        this(vertices, forms, visit, findings, (coordinate, path) -> { }, checkRadiusSeparately);
    }

    private GeometryRules(CoordinateType vertices, List<String> forms, Runnable visit, Findings findings,
                          BiConsumer<TransferGeometry.Coordinate, List<DataLocation.GeometryComponent>> verticesFound,
                          boolean checkRadiusSeparately) {
        this.vertices = vertices;
        this.forms = forms;
        this.visit = visit;
        this.findings = findings;
        this.verticesFound = verticesFound;
        this.checkRadiusSeparately = checkRadiusSeparately;
        fullyChecked = vertices != null;
    }

    static List<String> forms(ValidationPlan plan, GeometryType type) {
        return type.lineForms().stream().map(id -> plan.symbol(id).orElseThrow().origin())
                .map(origin -> origin instanceof Origin.PredefinedOrigin predefined ? predefined.key() : "custom")
                .toList();
    }

    static boolean standardForms(List<String> forms) {
        return !forms.isEmpty() && forms.stream().allMatch(
                form -> form.equals("INTERLIS.STRAIGHTS") || form.equals("INTERLIS.ARCS"));
    }

    static void vertices(TransferGeometry geometry, List<String> forms, Runnable visit,
                         BiConsumer<TransferGeometry.Coordinate, List<DataLocation.GeometryComponent>> consumer) {
        new GeometryRules(null, forms, visit, (code, path, arguments) -> { }, consumer, false).check(geometry);
    }

    static boolean accepts(GeometryType type, TransferGeometry value, boolean incomplete) {
        return switch (type.geometryKind()) {
            case POLYLINE -> value instanceof TransferGeometry.Polyline
                    || incomplete && value instanceof TransferGeometry.MultiPolyline;
            case MULTIPOLYLINE -> value instanceof TransferGeometry.MultiPolyline;
            case SURFACE, AREA -> value instanceof TransferGeometry.Surface
                    || incomplete && value instanceof TransferGeometry.MultiSurface;
            case MULTISURFACE, MULTIAREA -> value instanceof TransferGeometry.MultiSurface;
        };
    }

    boolean check(TransferGeometry geometry) {
        switch (geometry) {
            case TransferGeometry.Coordinate coordinate -> coordinate(coordinate, List.of());
            case TransferGeometry.MultiCoordinate multi -> {
                for (int i = 0; i < multi.coordinates().size(); i++) {
                    coordinate(multi.coordinates().get(i), at(List.of(), COORDINATE, i));
                }
            }
            case TransferGeometry.Polyline line -> line(line, List.of());
            case TransferGeometry.MultiPolyline multi -> {
                for (int i = 0; i < multi.lines().size(); i++) {
                    line(multi.lines().get(i), at(List.of(), LINE, i));
                }
            }
            case TransferGeometry.Surface surface -> surface(surface, List.of());
            case TransferGeometry.MultiSurface multi -> {
                for (int i = 0; i < multi.surfaces().size(); i++) {
                    surface(multi.surfaces().get(i), at(List.of(), SURFACE, i));
                }
            }
        }
        return fullyChecked;
    }

    private void surface(TransferGeometry.Surface surface, List<DataLocation.GeometryComponent> path) {
        visit.run();
        ring(surface.shell(), at(path, RING, 0));
        for (int i = 0; i < surface.holes().size(); i++) {
            ring(surface.holes().get(i), at(path, RING, i + 1));
        }
    }

    private void ring(TransferGeometry.Ring ring, List<DataLocation.GeometryComponent> path) {
        visit.run();
        for (int i = 0; i < ring.lines().size(); i++) {
            line(ring.lines().get(i), at(path, LINE, i));
        }
    }

    private void line(TransferGeometry.Polyline line, List<DataLocation.GeometryComponent> path) {
        visit.run();
        if (line.lineAttributes().isPresent() || !standardForms(forms)) {
            fullyChecked = false;
        }
        for (int partIndex = 0; partIndex < line.parts().size(); partIndex++) {
            visit.run();
            var part = line.parts().get(partIndex);
            var partPath = at(path, PART, partIndex);
            vertex(part.start(), at(partPath, START, 0));
            for (int i = 0; i < part.segments().size(); i++) {
                visit.run();
                var segment = part.segments().get(i);
                var segmentPath = at(partPath, SEGMENT, i);
                String expected = segment instanceof TransferGeometry.Arc ? "ARCS" : "STRAIGHTS";
                if (!forms.isEmpty() && !forms.contains("INTERLIS." + expected)) {
                    findings.report(SEGMENT_FORM_NOT_ALLOWED, segmentPath, List.of(expected));
                }
                if (segment instanceof TransferGeometry.Arc arc) {
                    coordinate(arc.support(), at(segmentPath, SUPPORT, 0), 2);
                    if (!checkRadiusSeparately && arc.radius().isPresent()) {
                        visit.run();
                        fullyChecked = false;
                    }
                }
                vertex(segment.end(), at(segmentPath, END, 0));
            }
        }
    }

    private void vertex(TransferGeometry.Coordinate value, List<DataLocation.GeometryComponent> path) {
        verticesFound.accept(value, path);
        coordinate(value, path);
    }

    private void coordinate(TransferGeometry.Coordinate value, List<DataLocation.GeometryComponent> path) {
        coordinate(value, path, vertices == null ? 0 : vertices.dimensions());
    }

    private void coordinate(TransferGeometry.Coordinate value, List<DataLocation.GeometryComponent> path, int dimensions) {
        visit.run();
        if (vertices == null) {
            return;
        }
        if (value.ordinates().size() != dimensions || dimensions > vertices.dimensions()) {
            findings.report(COORDINATE_DIMENSION_MISMATCH, path,
                    List.of(Integer.toString(value.ordinates().size()), Integer.toString(dimensions)));
            return;
        }
        for (int i = 0; i < dimensions; i++) {
            visit.run();
            var range = vertices.axes().get(i);
            if (range.minimum().isEmpty() || range.maximum().isEmpty()) {
                fullyChecked = false;
            }
            var axisPath = at(path, AXIS, i);
            ScalarRules.checkNumber(range, value.ordinates().get(i).exactValue(),
                    (code, arguments) -> findings.report(code, axisPath, arguments));
        }
    }

    private static List<DataLocation.GeometryComponent> at(List<DataLocation.GeometryComponent> path,
                                                           DataLocation.GeometryComponent.Kind kind, int index) {
        var result = new ArrayList<>(path);
        result.add(new DataLocation.GeometryComponent(kind, index + 1));
        return result;
    }
}
