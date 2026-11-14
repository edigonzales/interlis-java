package guru.interlis.validation.jts.internal;

import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.ValidationCode;
import guru.interlis.validation.ValidationLimitException;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.GeometryEngine.Check.*;
import static guru.interlis.validation.jts.internal.TransferGeometryAdapter.*;

final class CurveCoverage {
    private static final int MAX_COMPONENT_VISITS = 600_000;
    private static final int MAX_FACE_PAIRS = 1_000_000;

    private record Face(GeometryEngine.Input source, Surface surface, Envelope bounds) {}
    private record Boundary(GeometryEngine.Input source, Segment segment) {}

    static GeometryEngine.Check check(GeometryEngine.Source source, GeometryEngine.CoveragePurpose purpose,
                                      GeometryEngine.Control control) {
        var count = new int[]{0};
        var limited = new GeometryEngine.Control(() -> {
            control.visit().run();
            if (++count[0] > MAX_COMPONENT_VISITS) {
                throw new ValidationLimitException("Geometry coverage exceeds its prepared component budget");
            }
        }, control.findings());
        var origin = coverageOrigin(source, control, limited.visit());
        if (origin == null) {
            origin = new Position(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        // The origin prepass and preparation each stay within the component budget.
        count[0] = 0;

        var faces = new ArrayList<Face>();
        var index = new STRtree();
        var status = VALID;
        boolean complete = source.complete();
        Optional<guru.interlis.model.TypeId> domain = null;
        var overlap = Optional.<BigDecimal>empty();
        try (var cursor = source.open()) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                var input = next.orElseThrow();
                control.visit().run();
                if (domain != null && !domain.equals(input.coordinateDomain())) {
                    return UNSUPPORTED;
                }
                domain = input.coordinateDomain();
                complete &= !input.partial();
                if (input.overlap().isPresent()) {
                    overlap = Optional.of(overlap.map(input.overlap().orElseThrow()::max).orElse(input.overlap().orElseThrow()));
                }

                if (input.partial()) {
                    // An incomplete boundary cannot establish either the interior or its side of an edge.
                    continue;
                }
                var adapted = new TransferGeometryAdapter(input, limited, origin).read();
                var state = new CurveValidity(input, new GeometryEngine.Control(control.visit(), ignored -> {})).check(adapted);
                if (state != VALID) {
                    status = combine(status, state);
                    continue;
                }
                if (!(input.value() instanceof guru.interlis.transfer.TransferGeometry.Surface
                        || input.value() instanceof guru.interlis.transfer.TransferGeometry.MultiSurface)) {
                    status = combine(status, UNSUPPORTED);
                    continue;
                }
                for (var surface : adapted.surfaces()) {
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
                var state = validity.coveragePair(first.surface(), first.source(), second.surface(), overlap);
                status = combine(status, state);
            }
        }
        if (status != VALID) {
            return status;
        }
        if (!complete || !source.complete()) {
            return MISSING_PARAMETERS;
        }
        return purpose == GeometryEngine.CoveragePurpose.AREA_WITHOUT_GAPS ? gaps(faces, control) : VALID;
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
                input.partial(), input.coordinateDomain());
    }

    private static GeometryEngine.Check gaps(List<Face> faces, GeometryEngine.Control control) {
        var edges = new LinkedHashMap<Edge, Boundary>();
        for (var face : faces) {
            addEdges(edges, face.source(), face.surface().shell(), false, control);
            for (var hole : face.surface().holes()) {
                addEdges(edges, face.source(), hole, true, control);
            }
        }
        var outgoing = new LinkedHashMap<Position, List<Boundary>>();
        for (var edge : edges.values()) {
            control.visit().run();
            outgoing.computeIfAbsent(edge.segment().edge().start(), ignored -> new ArrayList<>()).add(edge);
        }
        boolean gap = false;
        var visited = new HashSet<Edge>();
        for (var edge : edges.values()) {
            control.visit().run();
            if (visited.contains(edge.segment().edge())) {
                continue;
            }
            var loop = new ArrayList<Segment>();
            var current = edge;
            do {
                control.visit().run();
                var segment = current.segment();
                if (!visited.add(segment.edge())) {
                    return NUMERICALLY_UNDETERMINED;
                }
                loop.add(segment);
                var options = outgoing.get(segment.edge().end());
                if (options == null) {
                    return NUMERICALLY_UNDETERMINED;
                }
                current = successor(segment.curve(), options, control);
                if (current == null) {
                    return NUMERICALLY_UNDETERMINED;
                }
            } while (current != edge);
            double area = CurveValidity.signedArea(loop);
            if (!Double.isFinite(area) || area == 0) {
                return NUMERICALLY_UNDETERMINED;
            }
            if (area < 0) {
                gap = true;
                control.findings().accept(new GeometryEngine.Finding(edge.source().location(), edge.segment().path(),
                        ValidationCode.GEOMETRY_GAP, "Bounded uncovered region"));
            }
        }
        return gap ? INVALID : VALID;
    }

    private static Boundary successor(CurveSegment incoming, List<Boundary> options, GeometryEngine.Control control) {
        double back = startTangent(incoming.reversed());
        Boundary selected = null;
        double best = Double.POSITIVE_INFINITY;
        for (var option : options) {
            control.visit().run();
            double turn = Math.IEEEremainder(back - startTangent(option.segment().curve()), 2 * Math.PI);
            if (turn < 0) {
                turn += 2 * Math.PI;
            }
            if (turn < best) {
                best = turn;
                selected = option;
            } else if (turn == best) {
                selected = null;
            }
        }
        return selected;
    }

    private static double startTangent(CurveSegment curve) {
        return switch (curve) {
            case CircularArc arc -> arc.angle(arc.start()) + Math.copySign(Math.PI / 2, arc.sweep());
            case CurveSegment.Straight line ->
                    Math.atan2(line.end().y() - line.start().y(), line.end().x() - line.start().x());
        };
    }

    private static void addEdges(LinkedHashMap<Edge, Boundary> edges, GeometryEngine.Input source, Ring ring, boolean hole,
                                 GeometryEngine.Control control) {
        var segments = CurveValidity.flat(ring);
        boolean reverse = (CurveValidity.signedArea(segments) > 0) == hole;
        for (var segment : segments) {
            control.visit().run();
            var directed = reverse ? segment.reversed() : segment;
            if (edges.remove(directed.edge().reversed()) == null) {
                edges.put(directed.edge(), new Boundary(source, directed));
            }
        }
    }
}
