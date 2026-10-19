package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Geometry type definition without a geometry engine.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param geometryKind geometry family
 * @param lineForm declared line form text, if any
 * @param vertexDomain optional vertex domain
 * @param lineAttributes optional line-attributes type
 */
public record GeometryType(TypeId id, Origin origin, GeometryKind geometryKind,
                           Optional<String> lineForm, Optional<SymbolId> vertexDomain,
                           Optional<String> lineAttributes) implements TypeDescriptor {
    /** Creates a validated instance. */
    public GeometryType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(geometryKind, "geometryKind");
        lineForm = Objects.requireNonNull(lineForm, "lineForm");
        vertexDomain = Objects.requireNonNull(vertexDomain, "vertexDomain");
        lineAttributes = Objects.requireNonNull(lineAttributes, "lineAttributes");
    }

    @Override
    public TypeKind kind() {
        return TypeKind.GEOMETRY;
    }
}
