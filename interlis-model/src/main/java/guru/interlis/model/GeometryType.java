package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Geometry type definition without a geometry engine.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param geometryKind geometry family
 * @param lineForms bound curve forms in declaration order; empty means unspecified
 * @param vertexDomain optional vertex domain
 * @param lineAttributes optional line-attributes type
 * @param directed whether curve order determines direction
 * @param withoutOverlaps effective overlap restriction, including implicit surfaces
 * @param overlap established exact or symbolic tolerance; empty means none established
 */
public record GeometryType(TypeId id, Origin origin, GeometryKind geometryKind,
                           java.util.List<SymbolId> lineForms, Optional<SymbolId> vertexDomain,
                           Optional<SymbolId> lineAttributes, boolean directed, boolean withoutOverlaps, Optional<Tolerance> overlap) implements TypeDescriptor {
    /** Creates a validated instance. */
    public GeometryType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(geometryKind, "geometryKind");
        lineForms = java.util.List.copyOf(lineForms);
        vertexDomain = Objects.requireNonNull(vertexDomain, "vertexDomain");
        lineAttributes = Objects.requireNonNull(lineAttributes, "lineAttributes");
        overlap = Objects.requireNonNull(overlap);
    }

    @Override
    public TypeKind kind() {
        return TypeKind.GEOMETRY;
    }

    /** Established overlap tolerance. Empty overlap means none has been established,
     * not zero. Explicit values retain exact decimal precision, including erroneous
     * values in a diagnosed partial analysis. Track width is q*sqrt(2)/2 and is never
     * approximated by a decimal; its bound domain determines q, possibly at deferred
     * generic binding time. Inheritance keeps that original domain identity.
     * All values are immutable, null-free and belong to this geometry's graph. */
    public sealed interface Tolerance permits Explicit, TrackWidth { }
    public record Explicit(DecimalLiteral value) implements Tolerance {
        public Explicit { Objects.requireNonNull(value); }
    }
    public record TrackWidth(SymbolId coordinateDomain) implements Tolerance {
        public TrackWidth { Objects.requireNonNull(coordinateDomain); }
    }
}
