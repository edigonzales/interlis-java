package guru.interlis.validation.jts.internal;

import guru.interlis.model.LanguageVersion;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.DataLocation.GeometryComponent;
import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.GeometryWitness;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.jts.internal.CurvePointLocation.Position;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static guru.interlis.validation.GeometryEngine.Check.*;
import static guru.interlis.validation.GeometryWitness.Kind.*;
import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.jts.internal.CurveSegment.Point;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapter.*;

final class CurveValidity {
    private final GeometryEngine.Input input;
    private final GeometryEngine.Control control;
    private GeometryEngine.Input relatedSource;
    private TransferGeometryAdapter.Position origin;
    private Optional<LensHeight.Tolerance> overlap;
    private GeometryEngine.Check status = VALID;
    private final List<Surface> checkedSurfaces = new ArrayList<>();

    /** A directed segment span delimited by the two intersections of a permitted lens. */
    record Span(Point start, Point end) {}
    record Fragment(Span span, boolean withinSpans) {}

    private record Lens(Point shared, Point crossing) {}
    private record Conflict(Optional<CurveIntersections.Hit> hit, String detail, Optional<Lens> lens) {
        static Conflict none() { return new Conflict(Optional.empty(), "", Optional.empty()); }
    }

    private static final class PairBoundaries {
        private final Set<Segment> shared = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Segment, List<Span>> permitted = new IdentityHashMap<>();
        private final Map<Segment, Set<Point>> contacts = new IdentityHashMap<>();

        void contact(Segment a, Segment b, Point point) {
            contacts.computeIfAbsent(a, ignored -> new HashSet<>()).add(point);
            contacts.computeIfAbsent(b, ignored -> new HashSet<>()).add(point);
        }

        void add(Segment segment, Lens lens) {
            var curve = segment.curve();
            var span = curve.start().equals(lens.shared())
                    ? new Span(lens.shared(), lens.crossing()) : new Span(lens.crossing(), lens.shared());
            permitted.computeIfAbsent(segment, ignored -> new ArrayList<>()).add(span);
        }
    }

    List<Surface> checkedSurfaces() {
        return checkedSurfaces;
    }

    CurveValidity(GeometryEngine.Input input, GeometryEngine.Control control) {
        this.input = input;
        this.control = control;
        this.relatedSource = input;
        this.overlap = LensHeight.Tolerance.from(input);
    }

    GeometryEngine.Check check(Result value) {
        return check(value, null);
    }

    GeometryEngine.Check check(Result value, Map<Ring, Integer> orientations) {
        control.visit().run();
        origin = value.origin();
        if (value.check() != VALID) {
            return value.check();
        }
        for (Part part : value.lines()) {
            control.visit().run();
            if (part.segments().isEmpty() && !input.partial()) {
                invalid(part.path(), GEOMETRY_DEGENERATE_SEGMENT, "Line has no segments", part.start());
            }
            if (input.type().withoutOverlaps()) {
                var segments = part.segments();
                boolean loop = !segments.isEmpty()
                        && part.start().equals(segments.getLast().edge().end());
                selfIntersections(segments, loop, true);
            }
        }
        if (!input.partial()) {
            for (Surface surface : value.surfaces()) {
                checkedSurfaces.add(surface(surface, orientations));
            }
            for (int i = 0; i < value.surfaces().size(); i++) {
                for (int j = 0; j < i; j++) {
                    compareSurfaces(checkedSurfaces.get(j), checkedSurfaces.get(i));
                }
            }
        }
        return input.partial() && status == VALID ? MISSING_PARAMETERS : status;
    }

    private Surface surface(Surface surface, Map<Ring, Integer> orientations) {
        var rings = rings(surface);
        List<List<Segment>> segments = new ArrayList<>();
        for (Ring ring : rings) {
            var boundary = closed(ring);
            if (boundary.isEmpty()) {
                return surface;
            }
            segments.add(boundary);
        }
        if (input.language() == LanguageVersion.ILI23) {
            var decomposed = decompose(segments);
            if (decomposed.size() > segments.size()) {
                surface = normalizedSurface(decomposed);
                rings = rings(surface);
                segments = decomposed;
            }
        }
        for (int i = 0; i < rings.size(); i++) {
            var ring = rings.get(i);
            var boundary = segments.get(i);
            if (!repeatedVertex(boundary)) {
                selfIntersections(boundary, true, false);
            }
            int orientation = areaOrientation(boundary, control.visit());
            if (orientations != null) {
                orientations.put(ring, orientation);
            }
            if (orientation == 0 && boundary.stream().anyMatch(segment -> segment.curve() instanceof CircularArc)) {
                uncertain();
            } else if (orientation == 0 && status != INVALID) {
                invalid(ring.path(), GEOMETRY_DEGENERATE_SEGMENT, "Ring encloses no area",
                        boundary.getFirst().edge().start());
            }
        }
        if (status != VALID || rings.size() == 1) {
            return surface;
        }

        var holeBounds = new ArrayList<Envelope>(rings.size() - 1);
        var holeIndex = new STRtree();
        for (int i = 1; i < rings.size(); i++) {
            var bounds = new Envelope();
            for (var segment : segments.get(i)) {
                control.visit().run();
                bounds.expandToInclude(segment.curve().envelope());
            }
            holeBounds.add(bounds);
            holeIndex.insert(bounds, i);
        }
        var junctions = new LinkedHashMap<Point, Set<Integer>>();
        for (int i = 1; i < rings.size(); i++) {
            var hole = segments.get(i);
            var candidates = new ArrayList<Integer>();
            candidates.add(0); // Even a hole disjoint from the shell must be checked against it.
            for (var candidate : holeIndex.query(holeBounds.get(i - 1))) {
                control.visit().run();
                int earlier = (Integer) candidate;
                if (earlier < i) {
                    candidates.add(earlier);
                }
            }
            candidates.sort(Integer::compareTo);
            for (int j : candidates) {
                var other = segments.get(j);
                var contactsAndLenses = new PairBoundaries();
                var contacts = boundaryContacts(other, hole, GEOMETRY_SELF_INTERSECTION, contactsAndLenses, false,
                        Optional.empty());
                for (var point : contacts) {
                    control.visit().run();
                    var incident = junctions.computeIfAbsent(point, ignored -> new LinkedHashSet<>());
                    incident.add(j);
                    incident.add(i);
                }
                if (status == INVALID) {
                    continue;
                }
                var probe = location(other, hole, contacts, contactsAndLenses.permitted);
                if (j == 0 && probe.position() == Position.OUTSIDE) {
                    invalid(rings.get(i).path(), rings.get(j).path(), GEOMETRY_HOLE_OUTSIDE_SHELL,
                            "Hole is outside its shell", OUTSIDE_BOUNDARY, probe.point());
                }
                if (j > 0) {
                    boolean currentInside = probe.position() == Position.INSIDE;
                    var inside = currentInside ? probe : location(hole, other, contacts, contactsAndLenses.permitted);
                    if (inside.position() == Position.INSIDE) {
                        int contained = currentInside ? i : j;
                        int container = currentInside ? j : i;
                        invalid(rings.get(contained).path(), rings.get(container).path(), GEOMETRY_HOLE_OUTSIDE_SHELL,
                                "Holes are nested", CONTAINED_BOUNDARY, inside.point());
                    }
                }
            }
        }
        if (status == VALID) {
            checkContactCycles(segments, junctions);
        }
        return surface;
    }

    private void checkContactCycles(List<List<Segment>> rings, Map<Point, Set<Integer>> junctions) {
        if (junctions.isEmpty()) {
            return;
        }
        // One node per ring and one per contact point. A junction of three rings is a star, not a cycle.
        var points = new ArrayList<>(junctions.keySet());
        int firstPoint = rings.size();
        var parents = new int[firstPoint + points.size()];
        var forest = new ArrayList<List<Integer>>(parents.length);
        for (int i = 0; i < parents.length; i++) {
            control.visit().run();
            parents[i] = i;
            forest.add(new ArrayList<>());
        }
        for (int i = 0; i < points.size(); i++) {
            int node = firstPoint + i;
            for (int ring : junctions.get(points.get(i))) {
                control.visit().run();
                int ringRoot = root(parents, ring);
                int pointRoot = root(parents, node);
                if (ringRoot == pointRoot) {
                    reportContactCycle(rings, points, forest, ring, node);
                    return;
                }
                parents[ringRoot] = pointRoot;
                forest.get(node).add(ring);
                forest.get(ring).add(node);
            }
        }
    }

    private void reportContactCycle(List<List<Segment>> rings, List<Point> points, List<List<Integer>> forest,
                                    int ring, int junction) {
        var predecessors = new int[forest.size()];
        Arrays.fill(predecessors, -1);
        predecessors[ring] = ring;
        var queue = new ArrayDeque<Integer>();
        queue.add(ring);
        while (predecessors[junction] == -1) {
            int node = queue.removeFirst();
            for (int neighbour : forest.get(node)) {
                control.visit().run();
                if (predecessors[neighbour] == -1) {
                    predecessors[neighbour] = node;
                    queue.add(neighbour);
                }
            }
        }
        var related = new ArrayList<DataLocation>();
        int point = junction;
        for (int node = predecessors[point]; node != ring; node = predecessors[point]) {
            related.add(dataPath(input, contactPath(rings.get(node), points.get(point - rings.size()))));
            point = predecessors[node];
        }
        var witness = points.get(junction - rings.size());
        invalid(contactPath(rings.get(ring), witness), GEOMETRY_INTERIOR_DISCONNECTED,
                "Boundary contacts form a cycle that separates the interior", related, VERTEX, witness);
    }

    private List<GeometryComponent> contactPath(List<Segment> boundary, Point point) {
        for (var segment : boundary) {
            control.visit().run();
            if (segment.curve().start().equals(point)) {
                return segment.path();
            }
        }
        throw new IllegalStateException("Certified boundary contact has no incident segment");
    }

    private List<List<Segment>> decompose(List<List<Segment>> boundaries) {
        var result = new ArrayList<List<Segment>>();
        for (var boundary : boundaries) {
            var cycles = splitRing(boundary);
            if (cycles.stream().mapToLong(List::size).sum() != boundary.size()
                    || cycles.stream().anyMatch(cycle -> cycle.size() < 2
                    || cycle.size() == 2 && cycle.stream().noneMatch(segment -> segment.curve() instanceof CircularArc))) {
                return boundaries;
            }
            result.addAll(cycles);
        }
        return result;
    }

    private static Surface normalizedSurface(List<List<Segment>> cycles) {
        var normalized = cycles.stream().map(cycle -> new Ring(List.of(), cycle.getFirst().path().stream()
                .takeWhile(component -> component.kind() != GeometryComponent.Kind.LINE).toList(), cycle))
                .toList();
        return new Surface(normalized.getFirst(), normalized.subList(1, normalized.size()));
    }

    private List<List<Segment>> splitRing(List<Segment> boundary) {
        var cycles = new ArrayList<List<Segment>>();
        var open = new ArrayList<Segment>();
        var positions = new HashMap<TransferGeometryAdapter.Position, Integer>();
        for (var segment : boundary) {
            control.visit().run();
            positions.put(segment.edge().start(), open.size());
            open.add(segment);
            var start = positions.get(segment.edge().end());
            if (start != null) {
                var cycle = open.subList(start, open.size());
                cycles.add(List.copyOf(cycle));
                for (var edge : cycle) {
                    positions.remove(edge.edge().start());
                }
                cycle.clear();
            }
        }
        // The outer cycle has the largest area. Keep the comparison certified before checking containment.
        int largest = 0;
        for (int i = 1; i < cycles.size(); i++) {
            control.visit().run();
            var candidate = cycles.get(i);
            var current = cycles.get(largest);
            int order = compareUnsignedArea(candidate, current);
            if (order > 0 || order == 0 && location(candidate, current, Set.of(), Map.of()).position() == Position.INSIDE) {
                largest = i;
            }
        }
        if (largest != 0) {
            java.util.Collections.swap(cycles, 0, largest);
        }
        return cycles;
    }

    private int compareUnsignedArea(List<Segment> first, List<Segment> second) {
        int firstSign = areaOrientation(first, control.visit());
        int secondSign = areaOrientation(second, control.visit());
        if (firstSign == 0 || secondSign == 0) {
            return 0;
        }
        // Linearity allows the exact chord terms and coincident circular corrections to cancel.
        var difference = new ArrayList<Segment>(first.size() + second.size());
        first.forEach(segment -> difference.add(firstSign > 0 ? segment : segment.reversed()));
        second.forEach(segment -> difference.add(secondSign < 0 ? segment : segment.reversed()));
        return areaOrientation(difference, control.visit());
    }

    private boolean repeatedVertex(List<Segment> boundary) {
        var points = new HashMap<TransferGeometryAdapter.Position, Segment>();
        for (var segment : boundary) {
            control.visit().run();
            var previous = points.putIfAbsent(segment.edge().start(), segment);
            if (previous != null) {
                invalid(segment.path(), previous.path(), GEOMETRY_SELF_INTERSECTION, "Ring visits a vertex more than once",
                        VERTEX, segment.curve().start());
                return true;
            }
        }
        return false;
    }

    private List<Segment> closed(Ring ring) {
        var boundary = new ArrayList<Segment>();
        Coordinate end = null;
        for (Part part : ring.parts()) {
            control.visit().run();
            if (part.segments().isEmpty() || end != null && !sameDeliveredPosition(end, part.first())) {
                invalid(part.path(), GEOMETRY_RING_NOT_CLOSED, "Ring parts do not form a continuous boundary", part.start());
                return List.of();
            }
            boundary.addAll(part.segments());
            end = part.last();
        }
        if (boundary.isEmpty() || !sameDeliveredPosition(ring.parts().getFirst().first(), end)) {
            invalid(ring.path(), GEOMETRY_RING_NOT_CLOSED, "Ring is open",
                    boundary.isEmpty() ? null : boundary.getLast().edge().end());
            return List.of();
        }
        return boundary;
    }

    private static boolean sameDeliveredPosition(Coordinate first, Coordinate second) {
        var left = first.ordinates();
        var right = second.ordinates();
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i).exactValue().compareTo(right.get(i).exactValue()) != 0) {
                return false;
            }
        }
        return true;
    }

    private void selfIntersections(List<Segment> segments, boolean closed, boolean line) {
        var index = new STRtree();
        var contacts = new LinkedHashMap<Point, Set<List<GeometryComponent>>>();
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
                var result = intersections(a, b);
                if (result.undetermined()) {
                    uncertain();
                }
                if (line && result.hits().stream().allMatch(hit -> sharedContact(a, b, hit))) {
                    continue;
                }
                var allowed = new HashSet<Point>();
                if (j == i + 1) {
                    allowed.add(a.curve().end());
                }
                if (closed && i == 0 && j == segments.size() - 1) {
                    allowed.add(a.curve().start());
                }
                if (!line || input.language() == LanguageVersion.ILI24) {
                    result.hits().stream().filter(hit -> sharedContact(a, b, hit))
                            .map(CurveIntersections.Hit::point).forEach(allowed::add);
                }
                var conflict = conflicts(a.curve(), b.curve(), result.hits(), allowed);
                conflict.hit().ifPresent(hit -> {
                    if (hit.kind() == CurveIntersections.Kind.CONTACT && hit.point().isExact()) {
                        var paths = contacts.computeIfAbsent(hit.point(), ignored -> new LinkedHashSet<>());
                        paths.add(b.path());
                        paths.add(a.path());
                    } else {
                        invalid(b.path(), a.path(), GEOMETRY_SELF_INTERSECTION,
                                conflict.detail(), kind(hit), hit.point());
                    }
                });
            }
        }
        contacts.forEach((point, paths) -> {
            var locations = paths.iterator();
            var first = locations.next();
            var related = new ArrayList<DataLocation>();
            locations.forEachRemaining(path -> related.add(dataPath(input, path)));
            invalid(first, GEOMETRY_SELF_INTERSECTION, "Segments meet away from a common support position",
                    related, INTERSECTION, point);
        });
    }

    private CurveIntersections.Result intersections(Segment a, Segment b) {
        return CurveIntersections.between(a.curve(), b.curve(), control.visit());
    }

    private Set<Point> boundaryContacts(List<Segment> a, List<Segment> b, ValidationCode code,
                                        PairBoundaries pair, boolean allowShared, Optional<Boolean> sameInteriorSide) {
        var index = new STRtree();
        for (Segment segment : a) {
            control.visit().run();
            index.insert(segment.curve().envelope(), segment);
        }
        var contacts = new LinkedHashSet<Point>();
        for (Segment right : b) {
            control.visit().run();
            for (Object candidate : index.query(right.curve().envelope())) {
                control.visit().run();
                var left = (Segment) candidate;
                if (allowShared) {
                    boolean same = left.edge().equals(right.edge());
                    if (same || left.edge().equals(right.edge().reversed())) {
                        if (sameInteriorSide.isPresent() && same == sameInteriorSide.orElseThrow()) {
                            invalid(right.path(), left.path(), GEOMETRY_AREA_OVERLAP,
                                    "Faces have interior on the same side of a shared boundary",
                                    SHARED_BOUNDARY, right.curve().start());
                        } else if (sameInteriorSide.isPresent()) {
                            right.transferIdentityMismatch(left, same).ifPresent(mismatch ->
                                    invalid(right.path(), left.path(), GEOMETRY_BOUNDARY_MISMATCH,
                                            mismatch.detail(), SHARED_BOUNDARY, mismatch.witness()));
                        }
                        pair.shared.add(left);
                        pair.shared.add(right);
                        continue;
                    }
                }
                var result = intersections(left, right);
                if (result.undetermined()) {
                    uncertain();
                }
                var allowed = new HashSet<Point>();
                for (var hit : result.hits()) {
                    if (sharedContact(left, right, hit)) {
                        allowed.add(hit.point());
                        contacts.add(hit.point());
                        if (pair != null) {
                            pair.contact(left, right, hit.point());
                        }
                    }
                }
                var conflict = conflicts(left.curve(), right.curve(), result.hits(), allowed);
                if (conflict.hit().isPresent()) {
                    var hit = conflict.hit().orElseThrow();
                    boolean mismatch = hit.kind() == CurveIntersections.Kind.OVERLAP
                            || hit.kind() == CurveIntersections.Kind.CONTACT;
                    var finding = code == GEOMETRY_AREA_OVERLAP && mismatch ? GEOMETRY_BOUNDARY_MISMATCH : code;
                    invalid(right.path(), left.path(), finding, conflict.detail(), kind(hit), hit.point());
                }
                if (pair != null) {
                    conflict.lens().ifPresent(lens -> {
                        pair.add(left, lens);
                        pair.add(right, lens);
                        pair.contact(left, right, lens.crossing());
                    });
                }
            }
        }
        return contacts;
    }

    private static GeometryWitness.Kind kind(CurveIntersections.Hit hit) {
        return hit.kind() == CurveIntersections.Kind.OVERLAP ? SHARED_BOUNDARY : INTERSECTION;
    }

    private static boolean sharedContact(Segment a, Segment b, CurveIntersections.Hit hit) {
        return hit.kind() == CurveIntersections.Kind.CONTACT
                && endpoint(a.curve(), hit.point()) && endpoint(b.curve(), hit.point());
    }

    private Conflict conflicts(CurveSegment a, CurveSegment b, List<CurveIntersections.Hit> hits,
                               Set<Point> allowed) {
        var unexpected = hits.stream().filter(h -> h.kind() != CurveIntersections.Kind.CONTACT
                || !allowed.contains(h.point())).toList();
        if (unexpected.isEmpty()) {
            return Conflict.none();
        }
        var hit = unexpected.getFirst();
        var shared = hits.stream().map(CurveIntersections.Hit::point)
                .filter(point -> allowed.contains(point) && endpoint(a, point) && endpoint(b, point))
                .findFirst();
        if (hits.size() != 2 || unexpected.size() != 1 || hit.kind() != CurveIntersections.Kind.CROSSING
                || shared.isEmpty() || !(a instanceof CircularArc || b instanceof CircularArc)) {
            var detail = switch (hit.kind()) {
                case CONTACT -> "Segments meet away from a common support position";
                case CROSSING -> "Segments cross";
                case OVERLAP -> "Segments share a boundary interval";
            };
            return new Conflict(Optional.of(hit), detail, Optional.empty());
        }
        if (overlap.isEmpty()) {
            if (status == VALID) {
                status = MISSING_PARAMETERS;
            }
            return Conflict.none();
        }
        var start = shared.orElseThrow();
        var tolerance = overlap.orElseThrow();
        var assessment = LensHeight.compare(a, b, start, tolerance, control.visit());
        if (assessment == null) {
            uncertain();
            return Conflict.none();
        }
        if (!assessment.permitted()) {
            return new Conflict(Optional.of(hit), "Overlap height " + assessment.height().stripTrailingZeros().toPlainString()
                    + " exceeds tolerance " + tolerance.description(), Optional.empty());
        }
        return new Conflict(Optional.empty(), "", Optional.of(new Lens(start, hit.point())));
    }

    private static boolean endpoint(CurveSegment segment, Point point) {
        return segment.start().equals(point) || segment.end().equals(point);
    }

    private void compareSurfaces(Surface first, Surface second) {
        statusAfterCoverage(new SurfaceLocator(first), new SurfaceLocator(second), null, false);
    }

    private PairBoundaries compareSurfaces(Surface first, Surface second, Map<Ring, Integer> orientations) {
        control.visit().run();
        var left = rings(first);
        var right = rings(second);
        var pair = new PairBoundaries();
        boolean allowShared = orientations != null;
        for (int i = 0; i < left.size(); i++) {
            var a = flat(left.get(i));
            for (int j = 0; j < right.size(); j++) {
                control.visit().run();
                var b = flat(right.get(j));
                Optional<Boolean> sameSide = Optional.empty();
                if (allowShared) {
                    int leftOrientation = orientations.get(left.get(i));
                    int rightOrientation = orientations.get(right.get(j));
                    if (leftOrientation == 0 || rightOrientation == 0) {
                        uncertain();
                    } else {
                        sameSide = Optional.of((leftOrientation > 0 ^ i > 0) == (rightOrientation > 0 ^ j > 0));
                    }
                }
                boundaryContacts(a, b, GEOMETRY_AREA_OVERLAP, pair, allowShared, sameSide);
            }
        }
        return pair;
    }

    record CoveragePair(GeometryEngine.Check check, Map<Segment, List<Span>> submerged) {}

    CoveragePair coveragePair(SurfaceLocator first, GeometryEngine.Input firstSource, SurfaceLocator second,
                              Map<Ring, Integer> orientations, Optional<LensHeight.Tolerance> tolerance,
                              TransferGeometryAdapter.Position projectionOrigin, boolean traceBoundary) {
        relatedSource = firstSource;
        overlap = tolerance;
        origin = projectionOrigin;
        return new CoveragePair(statusAfterCoverage(first, second, orientations, traceBoundary), submerged);
    }

    private final Map<Segment, List<Span>> submerged = new IdentityHashMap<>();

    private GeometryEngine.Check statusAfterCoverage(SurfaceLocator first, SurfaceLocator second,
                                                     Map<Ring, Integer> orientations, boolean traceBoundary) {
        var pair = compareSurfaces(first.surface, second.surface, orientations);
        if (status != INVALID) {
            within(first, second.surface, pair, traceBoundary, false);
        }
        if (status != INVALID) {
            within(second, first.surface, pair, traceBoundary, true);
        }
        return status;
    }

    private void within(SurfaceLocator locator, Surface other, PairBoundaries pair, boolean traceBoundary, boolean reverse) {
        var container = locator.surface;
        for (var ring : rings(other)) {
            for (var segment : flat(ring)) {
                control.visit().run();
                if (pair.shared.contains(segment)) {
                    continue;
                }
                var curve = segment.curve();
                var lenses = pair.permitted.getOrDefault(segment, List.of());
                var contacts = pair.contacts.getOrDefault(segment, Set.of());
                List<Fragment> fragments;
                try {
                    fragments = fragments(curve, lenses, control.visit());
                } catch (CurveOrder.Unknown uncertainOrder) {
                    uncertain();
                    continue;
                }
                for (var fragment : fragments) {
                    control.visit().run();
                    var found = probe(locator, curve, fragment.span(), contacts);
                    if (found == null || found.position() != Position.INSIDE) {
                        continue;
                    }
                    var point = found.point();
                    if (!fragment.withinSpans()) {
                        var primary = reverse ? container.shell().path() : segment.path();
                        var related = reverse ? segment.path() : container.shell().path();
                        invalid(primary, related, GEOMETRY_AREA_OVERLAP, "Surface interiors overlap",
                                CONTAINED_BOUNDARY, point);
                        return;
                    }
                    if (traceBoundary) {
                        submerged.computeIfAbsent(segment, ignored -> new ArrayList<>()).add(fragment.span());
                    }
                }
            }
        }
    }

    private record Containment(Point point, Position position) {}

    private Containment probe(SurfaceLocator locator, CurveSegment curve, Span fragment, Set<Point> contacts) {
        for (var endpoint : List.of(fragment.start(), fragment.end())) {
            if (!contacts.contains(endpoint)) {
                var position = locate(locator, endpoint);
                if (position == Position.INSIDE || position == Position.OUTSIDE) {
                    return new Containment(endpoint, position);
                }
            }
        }
        if (!(curve instanceof CircularArc)) {
            if (fragment.start().equals(curve.start()) && fragment.end().equals(curve.end())) {
                var middle = CurvePointLocation.midpoint(curve);
                return new Containment(middle, locate(locator, middle));
            }
        }
        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            control.visit().run();
            var x = fragment.start().xInterval(digits);
            var y = fragment.start().yInterval(digits);
            var dx = fragment.end().xInterval(digits).subtract(x);
            var dy = fragment.end().yInterval(digits).subtract(y);
            if (curve instanceof CircularArc arc) {
                var circle = arc.definition();
                var chord = dx.square().add(dy.square()).sqrt();
                if (chord.low().signum() == 0) {
                    continue;
                }
                // From the centre, follow the perpendicular to this chord into the selected arc.
                var normalScale = circle.radiusSquared().interval(digits).sqrt().divide(chord)
                        .multiply(Interval.exact(BigDecimal.valueOf(arc.arcSide()), digits));
                x = circle.centreX(digits).subtract(dy.multiply(normalScale));
                y = circle.centreY(digits).add(dx.multiply(normalScale));
            } else {
                var start = curve.start();
                var vx = Interval.exact(curve.end().exactX().subtract(start.exactX()), digits);
                var vy = Interval.exact(curve.end().exactY().subtract(start.exactY()), digits);
                var half = Interval.exact(new BigDecimal("0.5"), digits);
                var px = x.add(dx.multiply(half)).subtract(Interval.exact(start.exactX(), digits));
                var py = y.add(dy.multiply(half)).subtract(Interval.exact(start.exactY(), digits));
                var fraction = px.multiply(vx).add(py.multiply(vy)).divide(vx.square().add(vy.square()));
                x = Interval.exact(start.exactX(), digits).add(vx.multiply(fraction));
                y = Interval.exact(start.exactY(), digits).add(vy.multiply(fraction));
            }
            if (separated(locator.surface, x, y)) {
                var middle = new Point(x.middle(), y.middle());
                return new Containment(middle, locate(locator, middle));
            }
        }
        uncertain();
        return null;
    }

    private boolean separated(Surface container, Interval x, Interval y) {
        int digits = x.digits();
        var box = new org.locationtech.jts.geom.Envelope(CurveSegment.lower(x.low()), CurveSegment.upper(x.high()),
                CurveSegment.lower(y.low()), CurveSegment.upper(y.high()));
        for (var ring : rings(container)) {
            for (var segment : flat(ring)) {
                control.visit().run();
                var curve = segment.curve();
                if (!curve.envelope().intersects(box)) {
                    continue;
                }
                if (curve instanceof CircularArc arc) {
                    var circle = arc.definition();
                    var radial = x.subtract(circle.centreX(digits)).square()
                            .add(y.subtract(circle.centreY(digits)).square())
                            .subtract(circle.radiusSquared().interval(digits));
                    if (Math.abs(radial.sign()) == 1
                            || CircularArc.side(curve.start(), curve.end(), x, y).sign() == -arc.arcSide()) {
                        continue;
                    }
                } else if (Math.abs(CircularArc.side(curve.start(), curve.end(), x, y).sign()) == 1) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private Position locate(SurfaceLocator locator, Point point) {
        var position = locator.locate(point, control.visit());
        if (position == Position.UNDETERMINED) {
            uncertain();
        }
        return position;
    }

    static final class SurfaceLocator {
        private final Surface surface;
        private final CurvePointLocation.PreparedRing shell;
        private final List<CurvePointLocation.PreparedRing> holes;

        SurfaceLocator(Surface surface) {
            this.surface = surface;
            shell = new CurvePointLocation.PreparedRing(flat(surface.shell()));
            holes = surface.holes().stream()
                    .map(ring -> new CurvePointLocation.PreparedRing(flat(ring))).toList();
        }

        Position locate(Point point, Runnable visit) {
            var outer = shell.locate(point, visit);
            if (outer != Position.INSIDE) {
                return outer;
            }
            for (var hole : holes) {
                var position = hole.locate(point, visit);
                if (position != Position.OUTSIDE) {
                    return position == Position.INSIDE ? Position.OUTSIDE : position;
                }
            }
            return Position.INSIDE;
        }

    }

    static List<Fragment> fragments(CurveSegment curve, List<Span> spans, Runnable visit) {
        if (spans.isEmpty()) {
            return List.of(new Fragment(new Span(curve.start(), curve.end()), false));
        }
        var positions = new TreeMap<Point, Integer>(new CurveOrder(curve, visit));
        positions.put(curve.start(), 0);
        positions.put(curve.end(), 0);
        for (var span : spans) {
            positions.merge(span.start(), 1, Integer::sum);
            positions.merge(span.end(), -1, Integer::sum);
        }
        var fragments = new ArrayList<Fragment>();
        Point previous = null;
        int count = 0;
        for (var entry : positions.entrySet()) {
            if (previous != null) {
                fragments.add(new Fragment(new Span(previous, entry.getKey()), count > 0));
            }
            previous = entry.getKey();
            count += entry.getValue();
        }
        return fragments;
    }

    static CurveSegment subcurve(CurveSegment curve, Point start, Point end) {
        if (start.equals(curve.start()) && end.equals(curve.end())) {
            return curve;
        }
        return curve instanceof CircularArc arc ? arc.subArc(start, end) : new CurveSegment.Straight(start, end);
    }

    private static List<Ring> rings(Surface surface) {
        var result = new ArrayList<Ring>();
        result.add(surface.shell());
        result.addAll(surface.holes());
        return result;
    }

    static List<Segment> flat(Ring ring) {
        return ring.segments();
    }

    private Containment location(List<Segment> container, List<Segment> other, Set<Point> contacts,
                                 Map<Segment, List<Span>> lenses) {
        for (Segment segment : other) {
            var start = segment.curve().start();
            if (!contacts.contains(start)) {
                var position = CurvePointLocation.locate(container, start, control.visit());
                if (position == Position.INSIDE || position == Position.OUTSIDE) {
                    return new Containment(start, position);
                }
            }
        }
        // All support points can be contacts, for example when a hole has two curved sides.
        for (var segment : other) {
            var curve = segment.curve();
            List<Fragment> fragments;
            try {
                fragments = fragments(curve, lenses.getOrDefault(segment, List.of()), control.visit());
            } catch (CurveOrder.Unknown uncertainOrder) {
                continue;
            }
            for (var fragment : fragments) {
                control.visit().run();
                if (fragment.withinSpans()) {
                    continue;
                }
                var span = fragment.span();
                var point = CurvePointLocation.midpoint(subcurve(curve, span.start(), span.end()));
                if (point != null) {
                    var position = CurvePointLocation.locate(container, point, control.visit());
                    if (position == Position.INSIDE || position == Position.OUTSIDE) {
                        return new Containment(point, position);
                    }
                }
            }
        }
        uncertain();
        return new Containment(null, Position.UNDETERMINED);
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

    static int areaOrientation(List<Segment> boundary, Runnable visit) {
        return CurveAreaOrientation.sign(boundary, visit);
    }

    private static int root(int[] links, int index) {
        while (links[index] != index) {
            links[index] = links[links[index]];
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
                         ValidationCode code, String detail, GeometryWitness.Kind kind, Point point) {
        invalid(path, code, detail, List.of(dataPath(relatedSource, related)), kind, point);
    }

    private static DataLocation dataPath(GeometryEngine.Input source, List<GeometryComponent> path) {
        var location = source.location();
        var steps = new ArrayList<>(location.path());
        steps.addAll(path);
        return new DataLocation(location.source(), location.basket(), location.tid(), location.oid(),
                location.objectType(), steps);
    }

    private void invalid(List<GeometryComponent> path, ValidationCode code, String detail,
                         List<DataLocation> related, GeometryWitness.Kind kind, Point point) {
        status = INVALID;
        control.findings().accept(new GeometryEngine.Finding(input.location(), path, code, detail, related,
                point == null ? Optional.empty() : Optional.of(witness(kind, point, origin))));
    }

    private void invalid(List<GeometryComponent> path, ValidationCode code, String detail,
                         TransferGeometryAdapter.Position point) {
        invalid(path, code, detail, point == null ? Optional.empty()
                : Optional.of(new GeometryWitness(VERTEX, point.x(), point.y())));
    }

    private void invalid(List<GeometryComponent> path, ValidationCode code, String detail,
                         GeometryWitness.Kind kind, Point point) {
        invalid(path, code, detail, point == null ? Optional.empty() : Optional.of(witness(kind, point, origin)));
    }

    private void invalid(List<GeometryComponent> path, ValidationCode code, String detail,
                         Optional<GeometryWitness> witness) {
        status = INVALID;
        control.findings().accept(new GeometryEngine.Finding(input.location(), path, code, detail,
                Optional.empty(), witness));
    }

    static GeometryWitness witness(GeometryWitness.Kind kind, Point point, TransferGeometryAdapter.Position origin) {
        return new GeometryWitness(kind, origin.x().add(point.exactX()), origin.y().add(point.exactY()));
    }
}
