package guru.interlis.validation;

import java.util.OptionalLong;

/** Immutable scope of repeatable object-set queries. */
@FunctionalInterface
interface RuntimeObjectSet {
    OptionalLong count();

    default java.util.Optional<GeometryEngine.Source> geometries(GeometrySelection paths) {
        return java.util.Optional.empty();
    }

    default GeometryEngine.Check checkAreaGroups(GeometrySelection paths, GeometryEngine engine,
                                                 GeometryEngine.Control control) {
        return GeometryEngine.Check.MISSING_PARAMETERS;
    }
}
