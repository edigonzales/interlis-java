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
    private static final int MAX_SEGMENTS = 200_000;

    private record Face(GeometryEngine.Input source, Surface surface, Envelope bounds) {}
    private record Boundary(GeometryEngine.Input source, Segment segment) {}
    private record Key(CurveSegment.Point start, CurveSegment.Point end,
                       CurveSegment.Point center, double radius, double sweep) {}

    static GeometryEngine.Check check(GeometryEngine.Source source, GeometryEngine.CoveragePurpose purpose,
                                      GeometryEngine.Control control) {
        var faces = new ArrayList<Face>();
        var index = new STRtree();
        var status = VALID;
        var count = new int[]{0};
        boolean complete = source.complete();
        Optional<guru.interlis.model.TypeId> domain = null;
        BigDecimal x = null;
        BigDecimal y = null;
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
                final var findingSink = control.findings();
                var limited = new GeometryEngine.Control(() -> {
                    control.visit().run();
                    if (++count[0] > MAX_SEGMENTS * 3) {
                        throw new ValidationLimitException("Geometry coverage exceeds its prepared component budget");
                    }
                }, findingSink);
                var adapted = new TransferGeometryAdapter(input, limited, x, y).read();
                var state = new CurveValidity(input, new GeometryEngine.Control(control.visit(), ignored -> {})).check(adapted);
                if (state == INVALID) {
                    status = INVALID;
                    continue;
                }
                if (state != VALID && !input.partial()) {
                    return state;
                }
                if (adapted.check() != VALID || adapted.surfaces().isEmpty()) {
                    if (!input.partial()) {
                        return UNSUPPORTED;
                    }
                    continue;
                }
                if (x == null) {
                    x = adapted.originX();
                    y = adapted.originY();
                }
                for (var surface : adapted.surfaces()) {
                    var bounds = new Envelope();
                    for (var segment : CurveValidity.flat(surface.shell())) {
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
        for (int i = 0; i < faces.size(); i++) {
            var first = faces.get(i);
            for (Object candidate : index.query(first.bounds())) {
                control.visit().run();
                int j = (Integer) candidate;
                if (j <= i) {
                    continue;
                }
                var second = faces.get(j);
                var validity = new CurveValidity(second.source(), control);
                var state = validity.coveragePair(first.surface(), first.source(), second.surface(), overlap);
                if (state == INVALID) {
                    status = INVALID;
                } else if (status == VALID && state != VALID) {
                    status = state;
                }
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

    private static GeometryEngine.Input provenance(GeometryEngine.Input input) {
        return new GeometryEngine.Input(new guru.interlis.transfer.TransferGeometry.MultiSurface(List.of()),
                input.type(), input.location(), input.language(), input.quantum(), input.overlap(),
                input.partial(), input.coordinateDomain());
    }

    private static GeometryEngine.Check gaps(List<Face> faces, GeometryEngine.Control control) {
        var edges = new LinkedHashMap<Key, Boundary>();
        for (var face : faces) {
            addEdges(edges, face.source(), face.surface().shell(), false, control);
            for (var hole : face.surface().holes()) {
                addEdges(edges, face.source(), hole, true, control);
            }
        }
        var outgoing = new LinkedHashMap<CurveSegment.Point, List<Boundary>>();
        for (var edge : edges.values()) {
            control.visit().run();
            outgoing.computeIfAbsent(edge.segment().curve().start(), ignored -> new ArrayList<>()).add(edge);
        }
        boolean gap = false;
        var visited = new HashSet<Key>();
        for (var edge : edges.values()) {
            control.visit().run();
            if (visited.contains(key(edge.segment().curve()))) {
                continue;
            }
            var loop = new ArrayList<Segment>();
            var current = edge;
            do {
                control.visit().run();
                var segment = current.segment();
                if (!visited.add(key(segment.curve()))) {
                    return NUMERICALLY_UNDETERMINED;
                }
                loop.add(segment);
                var options = outgoing.get(segment.curve().end());
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

    private static void addEdges(LinkedHashMap<Key, Boundary> edges, GeometryEngine.Input source, Ring ring, boolean hole,
                                 GeometryEngine.Control control) {
        var segments = CurveValidity.flat(ring);
        boolean reverse = (CurveValidity.signedArea(segments) > 0) == hole;
        for (var segment : segments) {
            control.visit().run();
            var curve = reverse ? segment.curve().reversed() : segment.curve();
            if (edges.remove(key(curve.reversed())) == null) {
                edges.put(key(curve), new Boundary(source, new Segment(curve, segment.path())));
            }
        }
    }

    private static Key key(CurveSegment segment) {
        return segment instanceof CircularArc arc
                ? new Key(arc.start(), arc.end(), arc.center(), arc.radius(), arc.sweep())
                : new Key(segment.start(), segment.end(), null, 0, 0);
    }

}
