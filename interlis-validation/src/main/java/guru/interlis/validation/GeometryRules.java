package guru.interlis.validation;

import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.transfer.TransferGeometry;

import java.util.ArrayList;
import java.util.List;

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
    private boolean fullyChecked;

    GeometryRules(CoordinateType vertices, List<String> forms, Runnable visit, Findings findings) {
        this.vertices = vertices;
        this.forms = forms;
        this.visit = visit;
        this.findings = findings;
        fullyChecked = vertices != null;
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
        if (line.lineAttributes().isPresent() || forms.isEmpty()
                || forms.stream().anyMatch(form -> !form.equals("INTERLIS.STRAIGHTS") && !form.equals("INTERLIS.ARCS"))) {
            fullyChecked = false;
        }
        for (int partIndex = 0; partIndex < line.parts().size(); partIndex++) {
            visit.run();
            var part = line.parts().get(partIndex);
            var partPath = at(path, PART, partIndex);
            coordinate(part.start(), at(partPath, START, 0));
            for (int i = 0; i < part.segments().size(); i++) {
                visit.run();
                var segment = part.segments().get(i);
                var segmentPath = at(partPath, SEGMENT, i);
                String expected = segment instanceof TransferGeometry.Arc ? "ARCS" : "STRAIGHTS";
                if (!forms.isEmpty() && !forms.contains("INTERLIS." + expected)) {
                    findings.report(SEGMENT_FORM_NOT_ALLOWED, segmentPath, List.of(expected));
                }
                if (segment instanceof TransferGeometry.Arc arc) {
                    coordinate(arc.support(), at(segmentPath, SUPPORT, 0));
                    if (arc.radius().isPresent()) {
                        visit.run();
                        fullyChecked = false;
                    }
                }
                coordinate(segment.end(), at(segmentPath, END, 0));
            }
        }
    }

    private void coordinate(TransferGeometry.Coordinate value, List<DataLocation.GeometryComponent> path) {
        visit.run();
        if (vertices == null) {
            return;
        }
        if (value.ordinates().size() != vertices.dimensions()) {
            findings.report(COORDINATE_DIMENSION_MISMATCH, path,
                    List.of(Integer.toString(value.ordinates().size()), Integer.toString(vertices.dimensions())));
            return;
        }
        for (int i = 0; i < vertices.axes().size(); i++) {
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
