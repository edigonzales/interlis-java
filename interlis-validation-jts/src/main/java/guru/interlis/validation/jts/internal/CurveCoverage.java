package guru.interlis.validation.jts.internal;

import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.GeometryWitness;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.ValidationLimitException;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.validation.GeometryEngine.Check.*;
import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static guru.interlis.validation.jts.internal.CurveValidity.Span;
import static guru.interlis.validation.jts.internal.ExactCircle.Rational;
import static guru.interlis.validation.jts.internal.Interval.OVERLAPS_ZERO;
import static guru.interlis.validation.jts.internal.LensHeight.Radicals;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapter.*;

final class CurveCoverage {
    private static final int MAX_FACE_PAIRS = 1_000_000;

    private record Face(GeometryEngine.Input source, Surface surface, Envelope bounds) {}
    private record Boundary(GeometryEngine.Input source, Segment segment) {}
    private record BoundaryLoop(List<Boundary> edges, List<Segment> segments, Envelope bounds) {
        static BoundaryLoop of(List<Boundary> edges, List<Segment> segments, Runnable visit) {
            var bounds = new Envelope();
            for (var segment : segments) {
                visit.run();
                bounds.expandToInclude(segment.curve().envelope());
            }
            return new BoundaryLoop(edges, segments, bounds);
        }
    }

    static GeometryEngine.Check check(GeometryEngine.Source source, GeometryEngine.CoveragePurpose purpose,
                                      GeometryEngine.Control control, long maxAreaComponents) {
        var remaining = new long[]{maxAreaComponents};
        var limited = new GeometryEngine.Control(() -> {
            control.visit().run();
            if (remaining[0] == 0) {
                throw new ValidationLimitException("Geometry coverage exceeds its prepared component budget");
            }
            remaining[0]--;
        }, control.findings());
        var origin = coverageOrigin(source, control, limited.visit());
        if (origin == null) {
            origin = new Position(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        // The origin prepass and preparation each stay within the component budget.
        remaining[0] = maxAreaComponents;

        var faces = new ArrayList<Face>();
        var index = new STRtree();
        var status = VALID;
        boolean complete = source.complete();
        Optional<guru.interlis.model.TypeId> domain = null;
        var overlap = Optional.<LensHeight.Tolerance>empty();
        try (var cursor = source.open()) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                var input = next.orElseThrow();
                control.visit().run();
                if (domain != null && !domain.equals(input.coordinateDomain())) {
                    return UNSUPPORTED;
                }
                domain = input.coordinateDomain();
                complete &= !input.partial();
                var inputTolerance = LensHeight.Tolerance.from(input);
                if (inputTolerance.isPresent()) {
                    var limit = inputTolerance.orElseThrow();
                    overlap = Optional.of(overlap.map(limit::max).orElse(limit));
                }

                if (input.partial()) {
                    // An incomplete boundary cannot establish either the interior or its side of an edge.
                    continue;
                }
                var adapted = new TransferGeometryAdapter(input, limited, origin).read();
                var validity = new CurveValidity(input, new GeometryEngine.Control(control.visit(),
                        source.reportLocalFindings() ? control.findings() : ignored -> {}));
                var state = validity.check(adapted);
                if (state != VALID) {
                    status = combine(status, state);
                    continue;
                }
                if (!(input.value() instanceof guru.interlis.transfer.TransferGeometry.Surface
                        || input.value() instanceof guru.interlis.transfer.TransferGeometry.MultiSurface)) {
                    status = combine(status, UNSUPPORTED);
                    continue;
                }
                for (var surface : validity.checkedSurfaces()) {
                    var bounds = new Envelope();
                    for (var segment : CurveValidity.flat(surface.shell())) {
                        control.visit().run();
                        bounds.expandToInclude(segment.curve().envelope());
                    }
                    if (!bounds.isNull()) {
                        index.insert(bounds, faces.size());
                        faces.add(new Face(provenance(input), surface, bounds));
                    }
                }
            }
        }
        if (status == INVALID) {
            return INVALID;
        }
        boolean traceBoundary = purpose == GeometryEngine.CoveragePurpose.AREA_WITHOUT_GAPS;
        var submerged = new IdentityHashMap<Segment, List<Span>>();
        int pairs = 0;
        for (int i = 0; i < faces.size(); i++) {
            var first = faces.get(i);
            for (Object candidate : index.query(first.bounds())) {
                control.visit().run();
                int j = (Integer) candidate;
                if (j <= i) {
                    continue;
                }
                if (++pairs > MAX_FACE_PAIRS) {
                    throw new ValidationLimitException("Geometry coverage exceeds its face pair budget");
                }
                var second = faces.get(j);
                var validity = new CurveValidity(second.source(), control);
                var pair = validity.coveragePair(first.surface(), first.source(), second.surface(), overlap, origin,
                        traceBoundary);
                status = combine(status, pair.check());
                pair.submerged().forEach((segment, spans) ->
                        submerged.computeIfAbsent(segment, ignored -> new ArrayList<>()).addAll(spans));
            }
        }
        if (status != VALID) {
            return status;
        }
        if (!complete || !source.complete()) {
            return MISSING_PARAMETERS;
        }
        return traceBoundary ? gaps(faces, submerged, origin, control) : VALID;
    }

    private static Position coverageOrigin(GeometryEngine.Source source, GeometryEngine.Control control, Runnable visit) {
        Position minimum = null;
        try (var cursor = source.open()) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                control.visit().run();
                var input = next.orElseThrow();
                if (!input.partial()) {
                    minimum = TransferGeometryAdapter.coverageOrigin(input.value(), minimum, visit);
                }
            }
        }
        return minimum;
    }

    private static GeometryEngine.Check combine(GeometryEngine.Check first, GeometryEngine.Check second) {
        if (first == INVALID || second == INVALID) {
            return INVALID;
        }
        if (first == NUMERICALLY_UNDETERMINED || second == NUMERICALLY_UNDETERMINED) {
            return NUMERICALLY_UNDETERMINED;
        }
        if (first == UNSUPPORTED || second == UNSUPPORTED) {
            return UNSUPPORTED;
        }
        return first == MISSING_PARAMETERS || second == MISSING_PARAMETERS ? MISSING_PARAMETERS : VALID;
    }

    private static GeometryEngine.Input provenance(GeometryEngine.Input input) {
        return new GeometryEngine.Input(new guru.interlis.transfer.TransferGeometry.MultiSurface(List.of()),
                input.type(), input.location(), input.language(), input.quantum(), input.overlap(),
                input.partial(), input.coordinateDomain(), input.overlapTrackQuantum(), input.xtfAreaEncoding());
    }

    private static GeometryEngine.Check gaps(List<Face> faces, Map<Segment, List<Span>> submerged,
                                             Position origin, GeometryEngine.Control control) {
        var edges = new LinkedHashMap<Edge, Boundary>();
        for (var face : faces) {
            if (!addEdges(edges, face.source(), face.surface().shell(), false, submerged, origin, control)) {
                return NUMERICALLY_UNDETERMINED;
            }
            for (var hole : face.surface().holes()) {
                if (!addEdges(edges, face.source(), hole, true, submerged, origin, control)) {
                    return NUMERICALLY_UNDETERMINED;
                }
            }
        }
        var outgoing = new LinkedHashMap<Point, List<Boundary>>();
        for (var edge : edges.values()) {
            control.visit().run();
            outgoing.computeIfAbsent(edge.segment().curve().start(), ignored -> new ArrayList<>()).add(edge);
        }
        var status = VALID;
        var gapLoops = new ArrayList<BoundaryLoop>();
        var filledLoops = new ArrayList<BoundaryLoop>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Boundary, Boolean>());
        for (var edge : edges.values()) {
            control.visit().run();
            if (visited.contains(edge)) {
                continue;
            }
            var loop = new ArrayList<Boundary>();
            var current = edge;
            do {
                control.visit().run();
                if (!visited.add(current)) {
                    current = null;
                    break;
                }
                loop.add(current);
                var curve = current.segment().curve();
                current = successor(curve, outgoing.getOrDefault(curve.end(), List.of()), control);
            } while (current != null && current != edge);
            if (current == null) {
                status = combine(status, NUMERICALLY_UNDETERMINED);
                continue;
            }
            var segments = loop.stream().map(Boundary::segment).toList();
            int orientation = CurveValidity.areaOrientation(segments, control.visit());
            if (orientation == 0) {
                status = combine(status, NUMERICALLY_UNDETERMINED);
            } else if (orientation < 0) {
                gapLoops.add(BoundaryLoop.of(loop, segments, control.visit()));
            } else {
                filledLoops.add(BoundaryLoop.of(loop, segments, control.visit()));
            }
        }
        if (gapLoops.isEmpty()) {
            return status;
        }
        var islands = islandsByGap(gapLoops, filledLoops, control.visit());
        if (islands.isEmpty()) {
            return NUMERICALLY_UNDETERMINED;
        }
        var byGap = islands.orElseThrow();
        for (var gap : gapLoops) {
            var first = gap.edges().getFirst();
            var participants = new ArrayList<DataLocation>();
            var seen = Collections.newSetFromMap(new IdentityHashMap<GeometryEngine.Input, Boolean>());
            seen.add(first.source());
            collectParticipants(gap, seen, participants, control.visit());
            for (var island : byGap.getOrDefault(gap, List.of())) {
                collectParticipants(island, seen, participants, control.visit());
            }
            control.findings().accept(new GeometryEngine.Finding(first.source().location(), first.segment().path(),
                    ValidationCode.GEOMETRY_GAP, "Bounded uncovered region", participants,
                    Optional.of(CurveValidity.witness(GeometryWitness.Kind.GAP_BOUNDARY,
                            first.segment().curve().start(), origin))));
        }
        return INVALID;
    }

    private static Optional<Map<BoundaryLoop, List<BoundaryLoop>>> islandsByGap(List<BoundaryLoop> gaps,
                                                                                 List<BoundaryLoop> filled,
                                                                                 Runnable visit) {
        var index = new STRtree();
        for (var gap : gaps) {
            visit.run();
            index.insert(gap.bounds(), gap);
        }
        var islands = new IdentityHashMap<BoundaryLoop, List<BoundaryLoop>>();
        for (var shell : filled) {
            visit.run();
            BoundaryLoop parent = null;
            for (var item : index.query(shell.bounds())) {
                visit.run();
                var gap = (BoundaryLoop) item;
                var position = inside(shell, gap, visit);
                if (position == CurvePointLocation.Position.UNDETERMINED) {
                    return Optional.empty();
                }
                if (position != CurvePointLocation.Position.INSIDE) {
                    continue;
                }
                // Outside each filled shell is the closest containing gap, if any.
                if (parent == null) {
                    parent = gap;
                    continue;
                }
                var order = inside(gap, parent, visit);
                if (order == CurvePointLocation.Position.UNDETERMINED) {
                    return Optional.empty();
                }
                if (order == CurvePointLocation.Position.INSIDE) {
                    parent = gap;
                }
            }
            if (parent != null) {
                islands.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(shell);
            }
        }
        return Optional.of(islands);
    }

    private static CurvePointLocation.Position inside(BoundaryLoop inner, BoundaryLoop outer, Runnable visit) {
        if (!inner.bounds().intersects(outer.bounds())) {
            return CurvePointLocation.Position.OUTSIDE;
        }
        for (var segment : inner.segments()) {
            visit.run();
            var position = CurvePointLocation.locate(outer.segments(), segment.curve().start(), visit);
            if (position == CurvePointLocation.Position.INSIDE || position == CurvePointLocation.Position.OUTSIDE) {
                return position;
            }
        }
        // Distinct boundary walks can touch. Their relative position follows from any point off the contact.
        for (var segment : inner.segments()) {
            visit.run();
            var point = CurvePointLocation.midpoint(segment.curve());
            if (point != null) {
                var position = CurvePointLocation.locate(outer.segments(), point, visit);
                if (position == CurvePointLocation.Position.INSIDE || position == CurvePointLocation.Position.OUTSIDE) {
                    return position;
                }
            }
        }
        return CurvePointLocation.Position.UNDETERMINED;
    }

    private static void collectParticipants(BoundaryLoop loop, Set<GeometryEngine.Input> seen,
                                            List<DataLocation> participants, Runnable visit) {
        for (var edge : loop.edges()) {
            visit.run();
            if (seen.add(edge.source())) {
                participants.add(location(edge));
            }
        }
    }

    private static DataLocation location(Boundary boundary) {
        var data = boundary.source().location();
        var path = new ArrayList<>(data.path());
        path.addAll(boundary.segment().path());
        return new DataLocation(data.source(), data.basket(), data.tid(), data.oid(), data.objectType(), path);
    }

    private static Boundary successor(CurveSegment incoming, List<Boundary> options, GeometryEngine.Control control) {
        if (options.size() == 1) {
            return options.getFirst();
        }
        var back = Ray.along(incoming.reversed());
        Boundary selected = null;
        Ray best = null;
        int bestSector = Integer.MAX_VALUE;
        boolean tied = false;
        for (var option : options) {
            control.visit().run();
            var candidate = Ray.along(option.segment().curve());
            int sector = back.sector(candidate, control.visit());
            if (sector < 0) {
                return null;
            }
            int order = Integer.compare(bestSector, sector);
            if (order == 0) {
                order = candidate.cross(best, control.visit());
                if (order == OVERLAPS_ZERO) {
                    return null;
                }
                if (order == 0) {
                    order = best.compareCurvature(candidate);
                }
            }
            if (order > 0) {
                bestSector = sector;
                best = candidate;
                selected = option;
                tied = false;
            } else if (order == 0) {
                tied = true;
            }
        }
        return tied ? null : selected;
    }

    private record Ray(CurveSegment curve, Radicals x, Radicals y, int curvatureSign, Rational radiusSquared) {
        static Ray along(CurveSegment curve) {
            if (curve instanceof CircularArc arc) {
                var circle = arc.definition();
                var x = new Radicals(Rational.of(arc.start().exactX()).subtract(circle.x()),
                        circle.offsetX().negate(), circle.radicand());
                var y = new Radicals(Rational.of(arc.start().exactY()).subtract(circle.y()),
                        circle.offsetY().negate(), circle.radicand());
                return new Ray(curve, y.scale(Rational.of(arc.arcSide())), x.scale(Rational.of(-arc.arcSide())),
                        -arc.arcSide(), circle.radiusSquared());
            }
            return new Ray(curve, new Radicals(Rational.of(curve.end().exactX().subtract(curve.start().exactX()))),
                    new Radicals(Rational.of(curve.end().exactY().subtract(curve.start().exactY()))),
                    0, Rational.ONE);
        }

        int cross(Ray other, Runnable visit) {
            return exact() && other.exact() ? x.times(other.y).minus(y.times(other.x)).sign(visit)
                    : enclosedSign(other, true, visit);
        }

        private int dot(Ray other, Runnable visit) {
            return exact() && other.exact() ? x.times(other.x).plus(y.times(other.y)).sign(visit)
                    : enclosedSign(other, false, visit);
        }

        private boolean exact() {
            return curve.start().isExact() && (curve instanceof CircularArc || curve.end().isExact());
        }

        private int enclosedSign(Ray other, boolean cross, Runnable visit) {
            for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
                visit.run();
                var a = tangent(digits);
                var b = other.tangent(digits);
                int sign = (cross ? a.x().multiply(b.y()).subtract(a.y().multiply(b.x()))
                        : a.x().multiply(b.x()).add(a.y().multiply(b.y()))).sign();
                if (sign != OVERLAPS_ZERO) {
                    return sign;
                }
            }
            return OVERLAPS_ZERO;
        }

        private EnclosedRay tangent(int digits) {
            var sx = curve.start().xInterval(digits);
            var sy = curve.start().yInterval(digits);
            if (curve instanceof CircularArc arc) {
                var circle = arc.definition();
                var tx = sy.subtract(circle.centreY(digits));
                var ty = circle.centreX(digits).subtract(sx);
                return arc.arcSide() > 0 ? new EnclosedRay(tx, ty) : new EnclosedRay(tx.negate(), ty.negate());
            }
            return new EnclosedRay(curve.end().xInterval(digits).subtract(sx),
                    curve.end().yInterval(digits).subtract(sy));
        }

        int compareCurvature(Ray other) {
            int sign = Integer.compare(curvatureSign, other.curvatureSign);
            return sign == 0 ? curvatureSign * other.radiusSquared.compareTo(radiusSquared) : sign;
        }

        // Interior stays on the left. Scan CCW from the reversed incoming ray across the uncovered sector.
        int sector(Ray candidate, Runnable visit) {
            int turn = cross(candidate, visit);
            if (turn == OVERLAPS_ZERO) {
                return -1;
            }
            if (turn != 0) {
                return turn > 0 ? 1 : 3;
            }
            int alignment = dot(candidate, visit);
            if (Math.abs(alignment) != 1) {
                return -1;
            }
            return alignment < 0 ? 2 : compareCurvature(candidate) > 0 ? 4 : 0;
        }
    }

    private record EnclosedRay(Interval x, Interval y) {}

    private static boolean addEdges(Map<Edge, Boundary> edges, GeometryEngine.Input source, Ring ring, boolean hole,
                                    Map<Segment, List<Span>> submerged, Position origin, GeometryEngine.Control control) {
        var segments = CurveValidity.flat(ring);
        int orientation = CurveValidity.areaOrientation(segments, control.visit());
        if (orientation == 0) {
            return false;
        }
        boolean reverse = (orientation > 0) == hole;
        for (var segment : segments) {
            control.visit().run();
            var spans = submerged.getOrDefault(segment, List.of());
            if (spans.isEmpty()) {
                add(edges, source, reverse ? segment.reversed() : segment);
                continue;
            }
            var curve = segment.curve();
            List<CurveValidity.Fragment> fragments;
            try {
                fragments = CurveValidity.fragments(curve, spans, control.visit());
            } catch (CurveOrder.Unknown uncertainOrder) {
                return false;
            }
            for (var fragment : fragments) {
                control.visit().run();
                if (fragment.withinSpans()) {
                    continue;
                }
                // Split edges retain the original provenance. Use the corresponding position on the common plane.
                var span = fragment.span();
                var subcurve = CurveValidity.subcurve(curve, span.start(), span.end());
                var edge = new Edge(position(span.start(), origin), position(span.end(), origin), segment.edge().circle(),
                        segment.edge().direction());
                var split = new Segment(subcurve, segment.path(), edge);
                add(edges, source, reverse ? split.reversed() : split);
            }
        }
        return true;
    }

    private static Position position(Point point, Position origin) {
        return new Position(origin.x().add(point.exactX()), origin.y().add(point.exactY()));
    }

    private static void add(Map<Edge, Boundary> edges, GeometryEngine.Input source, Segment segment) {
        if (edges.remove(segment.edge().reversed()) == null) {
            edges.put(segment.edge(), new Boundary(source, segment));
        }
    }
}
