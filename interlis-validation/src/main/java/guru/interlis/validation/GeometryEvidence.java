package guru.interlis.validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A geometry finding with a spatial witness and snapshot-bound data paths.
 *
 * @param code the geometry finding code, which may differ from the enclosing constraint code
 * @param detail the geometric reason for the finding
 * @param location the first participating occurrence, including its full component path
 * @param relatedLocations the other participating occurrences with their paths, in traversal order
 * @param witness the point that supports the finding
 */
public record GeometryEvidence(ValidationCode code, String detail, DataLocation location,
                               List<DataLocation> relatedLocations, GeometryWitness witness) {
    public GeometryEvidence {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(location, "location");
        relatedLocations = List.copyOf(relatedLocations);
        Objects.requireNonNull(witness, "witness");
    }

    /** Returns the first related location, when present. */
    public Optional<DataLocation> relatedData() {
        return relatedLocations.stream().findFirst();
    }
}
