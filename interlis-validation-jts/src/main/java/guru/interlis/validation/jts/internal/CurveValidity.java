package guru.interlis.validation.jts.internal;

import guru.interlis.validation.DataLocation;
import guru.interlis.validation.DataLocation.GeometryComponent;
import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.ValidationCode;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.GeometryEngine.Check.*;
import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapter.*;

final class CurveValidity {
    private final GeometryEngine.Input input;
    private final GeometryEngine.Control control;
    private GeometryEngine.Check status = VALID;

    CurveValidity(GeometryEngine.Input input, GeometryEngine.Control control) {
        this.input = input;
        this.control = control;
    }

    GeometryEngine.Check check(Result value) {
        control.visit().run();
        if (value.check() != VALID) {
            return value.check();
        }
        for (Part part : value.lines()) {
            control.visit().run();
            if (part.segments().isEmpty() && !input.partial()) {
                invalid(part.path(), GEOMETRY_DEGENERATE_SEGMENT, "Line has no segments");
            }
            if (input.type().withoutOverlaps()) {
                var segments = part.segments();
                boolean loop = !segments.isEmpty()
                        && part.start().equals(segments.getLast().curve().end());
                selfIntersections(segments, loop);
            }
        }
        if (!input.partial()) {
            for (Surface surface : value.surfaces()) {
                surface(surface);
            }
            for (int i = 0; i < value.surfaces().size(); i++) {
                for (int j = 0; j < i; j++) {
                    compareSurfaces(value.surfaces().get(j), value.surfaces().get(i));
                }
            }
        }
        return input.partial() && status == VALID ? MISSING_PARAMETERS : status;
    }

    private void surface(Surface surface) {
        var rings = rings(surface);
        var segments = new ArrayList<List<Segment>>();
        for (Ring ring : rings) {
            var boundary = closed(ring);
            if (boundary.isEmpty()) {
                return;
            }
            segments.add(boundary);
            selfIntersections(boundary, true);
            double area = signedArea(boundary);
            if (!Double.isFinite(area)) {
                uncertain();
            } else if (area == 0) {
                invalid(ring.path(), GEOMETRY_DEGENERATE_SEGMENT, "Ring encloses no area");
            }
        }
        if (status != VALID) {
            return;
        }
        if (segments.stream().flatMap(List::stream).noneMatch(segment -> segment.curve() instanceof CircularArc)) {
            checkLinearSurface(surface, segments);
            return;
        }

        // A cycle of boundary contacts cuts off part of the surface interior.
        int[] connected = new int[rings.size()];
        for (int i = 0; i < connected.length; i++) {
            connected[i] = i;
        }
        for (int i = 1; i < rings.size(); i++) {
            var hole = segments.get(i);
            for (int j = 0; j < i; j++) {
                var other = segments.get(j);
                var contacts = boundaryContacts(other, hole, GEOMETRY_SELF_INTERSECTION);
                int first = root(connected, j);
                int second = root(connected, i);
                if (contacts.size() > 1 || !contacts.isEmpty() && first == second) {
                    invalid(rings.get(i).path(), rings.get(j).path(), GEOMETRY_INTERIOR_DISCONNECTED, "Boundary contacts separate the interior");
                } else if (!contacts.isEmpty()) {
                    connected[first] = second;
                }
                if (j == 0 && location(other, hole, contacts) != Position.INSIDE) {
                    invalid(rings.get(i).path(), rings.get(j).path(), GEOMETRY_HOLE_OUTSIDE_SHELL, "Hole is outside its shell");
                }
                if (j > 0 && (location(other, hole, contacts) == Position.INSIDE
                        || location(hole, other, contacts) == Position.INSIDE)) {
                    invalid(rings.get(i).path(), rings.get(j).path(), GEOMETRY_HOLE_OUTSIDE_SHELL, "Holes are nested");
                }
            }
        }
    }

    private void checkLinearSurface(Surface surface, List<List<Segment>> boundaries) {
        var factory = new GeometryFactory();
        var rings = boundaries.stream().map(boundary -> {
            var points = new org.locationtech.jts.geom.Coordinate[boundary.size() + 1];
            for (int i = 0; i < boundary.size(); i++) {
                control.visit().run();
                points[i] = boundary.get(i).curve().start().coordinate();
            }
            points[points.length - 1] = boundary.getLast().curve().end().coordinate();
            return factory.createLinearRing(points);
        }).toList();
        var polygon = factory.createPolygon(rings.getFirst(), rings.subList(1, rings.size())
                .toArray(org.locationtech.jts.geom.LinearRing[]::new));
        var error = new IsValidOp(polygon).getValidationError();
        if (error != null) {
            var code = switch (error.getErrorType()) {
                case TopologyValidationError.HOLE_OUTSIDE_SHELL, TopologyValidationError.NESTED_HOLES ->
                        GEOMETRY_HOLE_OUTSIDE_SHELL;
                case TopologyValidationError.DISCONNECTED_INTERIOR -> GEOMETRY_INTERIOR_DISCONNECTED;
                default -> GEOMETRY_SELF_INTERSECTION;
            };
            var at = surface.shell().path();
            if (error.getCoordinate() != null) {
                for (var boundary : boundaries) {
                    for (var segment : boundary) {
                        control.visit().run();
                        var hit = error.getCoordinate();
                        if (segment.curve().envelope().covers(hit)) {
                            at = segment.path();
                        }
                    }
                }
            }
            invalid(at, code, error.getMessage());
        }
    }

    private List<Segment> closed(Ring ring) {
        var boundary = new ArrayList<Segment>();
        Point end = null;
        for (Part part : ring.parts()) {
            control.visit().run();
            if (part.segments().isEmpty() || end != null && !end.equals(part.start())) {
                invalid(part.path(), GEOMETRY_RING_NOT_CLOSED, "Ring parts do not form a continuous boundary");
                return List.of();
            }
            boundary.addAll(part.segments());
            end = part.segments().getLast().curve().end();
        }
        if (boundary.isEmpty() || !boundary.getFirst().curve().start().equals(end)) {
            invalid(ring.path(), GEOMETRY_RING_NOT_CLOSED, "Ring is open");
            return List.of();
        }
        return boundary;
    }

    private void selfIntersections(List<Segment> segments, boolean closed) {
        var index = new STRtree();
        for (int i = 0; i < segments.size(); i++) {
            control.visit().run();
            index.insert(segments.get(i).curve().envelope(), i);
        }
        for (int i = 0; i < segments.size(); i++) {
            var a = segments.get(i);
            for (Object candidate : index.query(a.curve().envelope())) {
                control.visit().run();
                int j = (Integer) candidate;
                if (j <= i) {
                    continue;
                }
                var b = segments.get(j);
                var result = CurveIntersections.between(a.curve(), b.curve());
                if (result.undetermined()) {
                    uncertain();
                }
                var allowed = new HashSet<Point>();
                if (j == i + 1) {
                    allowed.add(a.curve().end());
                }
                if (closed && i == 0 && j == segments.size() - 1) {
                    allowed.add(a.curve().start());
                }
                if (!conflicts(a.curve(), b.curve(), result.hits(), allowed)) {
                    continue;
                }
                invalid(b.path(), a.path(), GEOMETRY_SELF_INTERSECTION, "Segments intersect within the same curve");
            }
        }
    }

    private HashSet<Point> boundaryContacts(List<Segment> a, List<Segment> b, ValidationCode code) {
        var index = new STRtree();
        for (Segment segment : a) {
            control.visit().run();
            index.insert(segment.curve().envelope(), segment);
        }
        var contacts = new HashSet<Point>();
        for (Segment right : b) {
            control.visit().run();
            for (Object candidate : index.query(right.curve().envelope())) {
                control.visit().run();
                var left = (Segment) candidate;
                var result = CurveIntersections.between(left.curve(), right.curve());
                if (result.undetermined()) {
                    uncertain();
                }
                var allowed = new HashSet<Point>();
                for (var hit : result.hits()) {
                    if (hit.kind() == CurveIntersections.Kind.CONTACT) {
                        allowed.add(hit.point());
                        contacts.add(hit.point());
                    }
                }
                if (conflicts(left.curve(), right.curve(), result.hits(), allowed)) {
                    invalid(right.path(), left.path(), code, "Surface boundaries cross or share a segment");
                }
            }
        }
        return contacts;
    }

    private boolean conflicts(CurveSegment a, CurveSegment b, List<CurveIntersections.Hit> hits,
                              HashSet<Point> allowed) {
        var unexpected = hits.stream().filter(h -> h.kind() != CurveIntersections.Kind.CONTACT
                || !allowed.contains(h.point())).toList();
        if (unexpected.isEmpty()) {
            return false;
        }
        if (hits.size() != 2 || unexpected.size() != 1
                || unexpected.getFirst().kind() != CurveIntersections.Kind.CROSSING) {
            return true;
        }
        Point shared = null;
        for (var hit : hits) {
            if (allowed.contains(hit.point()) && endpoint(a, hit.point()) && endpoint(b, hit.point())) {
                shared = hit.point();
            }
        }
        if (shared == null || !(a instanceof CircularArc || b instanceof CircularArc)) {
            return true;
        }
        if (input.overlap().isEmpty()) {
            if (status == VALID) {
                status = MISSING_PARAMETERS;
            }
            return false;
        }
        double height = sagitta(a, shared, unexpected.getFirst().point())
                + sagitta(b, shared, unexpected.getFirst().point());
        if (!Double.isFinite(height)) {
            uncertain();
            return false;
        }
        return height > input.overlap().orElseThrow().doubleValue();
    }

    private static boolean endpoint(CurveSegment segment, Point point) {
        return segment.start().equals(point) || segment.end().equals(point);
    }

    private static double sagitta(CurveSegment segment, Point shared, Point intersection) {
        if (!(segment instanceof CircularArc arc)) {
            return 0;
        }
        var from = shared.equals(arc.start()) ? arc : arc.reversed();
        double angle = from.angle(intersection) - from.angle(shared);
        angle *= Math.copySign(1, from.sweep());
        if (angle < 0) {
            angle += 2 * Math.PI;
        }
        double sine = Math.sin(angle / 4);
        return arc.radius() * (2 * sine * sine);
    }

    private void compareSurfaces(Surface first, Surface second) {
        control.visit().run();
        for (Ring a : rings(first)) {
            for (Ring b : rings(second)) {
                boundaryContacts(flat(a), flat(b), GEOMETRY_AREA_OVERLAP);
            }
        }
        if (within(first, second) || within(second, first)) {
            invalid(second.shell().path(), first.shell().path(), GEOMETRY_AREA_OVERLAP, "Surface interiors overlap");
        }
    }

    private boolean within(Surface container, Surface other) {
        var shell = flat(container.shell());
        var holes = container.holes().stream().map(CurveValidity::flat).toList();
        for (var segment : flat(other.shell())) {
            var point = midpoint(segment.curve());
            if (locate(shell, point) == Position.INSIDE
                    && holes.stream().noneMatch(hole -> locate(hole, point) != Position.OUTSIDE)) {
                return true;
            }
        }
        return false;
    }

    private static List<Ring> rings(Surface surface) {
        var result = new ArrayList<Ring>();
        result.add(surface.shell());
        result.addAll(surface.holes());
        return result;
    }

    static List<Segment> flat(Ring ring) {
        return ring.parts().stream().flatMap(part -> part.segments().stream()).toList();
    }

    private Position location(List<Segment> container, List<Segment> other, HashSet<Point> contacts) {
        for (Segment segment : other) {
            var start = segment.curve().start();
            if (!contacts.contains(start)) {
                return locate(container, start);
            }
            var mid = midpoint(segment.curve());
            if (!contacts.contains(mid)) {
                return locate(container, mid);
            }
        }
        return Position.BOUNDARY;
    }

    private enum Position { OUTSIDE, INSIDE, BOUNDARY }

    private Position locate(List<Segment> boundary, Point point) {
        int crossings = 0;
        for (Segment segment : boundary) {
            control.visit().run();
            var curve = segment.curve();
            if (endpoint(curve, point)) {
                return Position.BOUNDARY;
            }
            if (curve instanceof CircularArc arc) {
                var result = arcCrossings(arc, point);
                if (result < 0) {
                    return Position.BOUNDARY;
                }
                crossings += result;
            } else {
                Point a = curve.start();
                Point b = curve.end();
                int orientation = Orientation.index(a.coordinate(), b.coordinate(), point.coordinate());
                if (orientation == 0 && curve.envelope().covers(point.x(), point.y())) {
                    return Position.BOUNDARY;
                }
                if ((a.y() > point.y()) != (b.y() > point.y())
                        && (b.y() > a.y() ? orientation > 0 : orientation < 0)) {
                    crossings++;
                }
            }
        }
        return crossings % 2 == 0 ? Position.OUTSIDE : Position.INSIDE;
    }

    private static int arcCrossings(CircularArc arc, Point point) {
        var splits = new ArrayList<Double>();
        splits.add(0.0);
        double start = arc.angle(arc.start());
        double direction = Math.copySign(1, arc.sweep());
        for (double extremum : new double[]{Math.PI / 2, -Math.PI / 2}) {
            double delta = direction * (extremum - start);
            if (delta < 0) {
                delta += 2 * Math.PI;
            }
            if (delta > 0 && delta < Math.abs(arc.sweep())) {
                splits.add(delta);
            }
        }
        splits.add(Math.abs(arc.sweep()));
        splits.sort(Double::compare);
        double normalized = (point.y() - arc.center().y()) / arc.radius();
        if (Math.abs(normalized) > 1) {
            return 0;
        }
        double dx = arc.radius() * Math.sqrt((1 - normalized) * (1 + normalized));
        int count = 0;
        for (int i = 1; i < splits.size(); i++) {
            double a = start + direction * splits.get(i - 1);
            double b = start + direction * splits.get(i);
            double ay = i == 1 ? arc.start().y() : arc.center().y() + arc.radius() * Math.sin(a);
            double by = i == splits.size() - 1 ? arc.end().y() : arc.center().y() + arc.radius() * Math.sin(b);
            double x = arc.center().x() + Math.copySign(dx, Math.cos((a + b) / 2));
            if (x == point.x() && point.y() >= Math.min(ay, by) && point.y() <= Math.max(ay, by)) {
                return -1;
            }
            if ((ay > point.y()) != (by > point.y()) && x > point.x()) {
                count++;
            }
        }
        return count;
    }

    private static Point midpoint(CurveSegment segment) {
        if (segment instanceof CircularArc arc) {
            double angle = arc.angle(arc.start()) + arc.sweep() / 2;
            return new Point(Math.fma(arc.radius(), Math.cos(angle), arc.center().x()),
                    Math.fma(arc.radius(), Math.sin(angle), arc.center().y()));
        }
        return new Point(segment.start().x() / 2 + segment.end().x() / 2,
                segment.start().y() / 2 + segment.end().y() / 2);
    }

    static double signedArea(List<Segment> boundary) {
        if (boundary.isEmpty()) {
            return 0;
        }
        Point origin = boundary.getFirst().curve().start();
        double sum = 0;
        for (var segment : boundary) {
            var curve = segment.curve();
            if (curve instanceof CircularArc arc) {
                sum += arc.signedArea(origin);
            } else {
                sum += Math.fma(curve.start().x() - origin.x(), curve.end().y() - origin.y(),
                        -(curve.start().y() - origin.y()) * (curve.end().x() - origin.x())) / 2;
            }
        }
        return sum;
    }

    private static int root(int[] links, int index) {
        while (links[index] != index) {
            index = links[index];
        }
        return index;
    }

    private void uncertain() {
        if (status == VALID) {
            status = NUMERICALLY_UNDETERMINED;
        }
    }

    private void invalid(List<GeometryComponent> path, List<GeometryComponent> related,
                         ValidationCode code, String detail) {
        var location = input.location();
        var steps = new ArrayList<>(location.path());
        steps.addAll(related);
        var other = new DataLocation(location.source(), location.basket(), location.tid(), location.oid(),
                location.objectType(), steps);
        status = INVALID;
        control.findings().accept(new GeometryEngine.Finding(location, path, code, detail, Optional.of(other)));
    }

    private void invalid(List<GeometryComponent> path, ValidationCode code, String detail) {
        status = INVALID;
        control.findings().accept(new GeometryEngine.Finding(input.location(), path, code, detail));
    }
}
