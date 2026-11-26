package guru.interlis.validation;

import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferReader.XtfEncoding;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Optional operations on bound, immutable transfer geometry occurrences. */
public interface GeometryEngine {
    Check checkValue(Input input, Control control);

    Measurement measure(Measure operation, Input input, Control control);

    Check checkCoverage(Source source, CoveragePurpose purpose, Control control);

    /**
     * A value with its effective model, exact coordinate quantum and resolved overlap in coordinate units.
     * For an implicit overlap, {@code overlap} is a decimal estimate and {@code overlapTrackQuantum}
     * supplies the exact positive quantum of its defining coordinate domain. The limit is that quantum
     * divided by the square root of two. The other overlap values are exact decimals.
     * {@code xtfAreaEncoding} is supplied for transferred native AREA values whose encoding is known.
     * When present, coverage also checks the XTF identity of shared boundaries.
     */
    record Input(TransferGeometry value, GeometryType type, DataLocation location, LanguageVersion language,
                 Optional<BigDecimal> quantum, Optional<BigDecimal> overlap, boolean partial,
                 Optional<guru.interlis.model.TypeId> coordinateDomain, Optional<BigDecimal> overlapTrackQuantum,
                 Optional<XtfEncoding> xtfAreaEncoding) {
        public Input {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(quantum, "quantum");
            Objects.requireNonNull(overlap, "overlap");
            Objects.requireNonNull(coordinateDomain, "coordinateDomain");
            Objects.requireNonNull(overlapTrackQuantum, "overlapTrackQuantum");
            Objects.requireNonNull(xtfAreaEncoding, "xtfAreaEncoding");
        }
        public Input(TransferGeometry value, GeometryType type, DataLocation location, LanguageVersion language,
                     Optional<BigDecimal> quantum, Optional<BigDecimal> overlap, boolean partial,
                     Optional<guru.interlis.model.TypeId> coordinateDomain, Optional<BigDecimal> overlapTrackQuantum) {
            this(value, type, location, language, quantum, overlap, partial, coordinateDomain, overlapTrackQuantum,
                    Optional.empty());
        }
        public Input(TransferGeometry value, GeometryType type, DataLocation location, LanguageVersion language,
                     Optional<BigDecimal> quantum, Optional<BigDecimal> overlap, boolean partial,
                     Optional<guru.interlis.model.TypeId> coordinateDomain) {
            this(value, type, location, language, quantum, overlap, partial, coordinateDomain, Optional.empty());
        }
        public Input(TransferGeometry value, GeometryType type, DataLocation location, LanguageVersion language,
                     Optional<BigDecimal> quantum, Optional<BigDecimal> overlap, boolean partial) {
            this(value, type, location, language, quantum, overlap, partial, Optional.empty());
        }

        Input withXtfAreaEncoding(Optional<XtfEncoding> encoding) {
            return new Input(value, type, location, language, quantum, overlap, partial, coordinateDomain,
                    overlapTrackQuantum, encoding);
        }
    }

    /** The caller supplies a cancellation-aware budget and a bounded finding sink. */
    record Control(Runnable visit, Consumer<Finding> findings) {
        public Control {
            Objects.requireNonNull(visit, "visit");
            Objects.requireNonNull(findings, "findings");
        }
    }

    /**
     * A finding at a data occurrence, with an immutable path relative to that occurrence.
     * Related locations include their full paths in traversal order. A witness uses model coordinates.
     */
    record Finding(DataLocation location, List<DataLocation.GeometryComponent> path, ValidationCode code,
                   String detail, List<DataLocation> relatedLocations, Optional<GeometryWitness> witness) {
        public Finding {
            Objects.requireNonNull(location, "location");
            path = List.copyOf(path);
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
            relatedLocations = List.copyOf(relatedLocations);
            Objects.requireNonNull(witness, "witness");
        }
        public Finding(DataLocation location, List<DataLocation.GeometryComponent> path, ValidationCode code,
                       String detail, Optional<DataLocation> relatedData, Optional<GeometryWitness> witness) {
            this(location, path, code, detail, relatedData.stream().toList(), witness);
        }
        public Finding(DataLocation location, List<DataLocation.GeometryComponent> path, ValidationCode code,
                       String detail, Optional<DataLocation> relatedData) {
            this(location, path, code, detail, relatedData, Optional.empty());
        }
        public Finding(DataLocation location, List<DataLocation.GeometryComponent> path, ValidationCode code,
                       String detail) {
            this(location, path, code, detail, Optional.empty());
        }

        /** Returns the first related location, when present. */
        public Optional<DataLocation> relatedData() {
            return relatedLocations.stream().findFirst();
        }

        /** Returns structured evidence with full data paths when the finding has a spatial witness. */
        public Optional<GeometryEvidence> geometryEvidence() {
            return witness.map(point -> new GeometryEvidence(code, detail, diagnosticLocation(), relatedLocations, point));
        }

        DataLocation diagnosticLocation() {
            var steps = new ArrayList<>(location.path());
            steps.addAll(path);
            return new DataLocation(location.source(), location.basket(), location.tid(), location.oid(),
                    location.objectType(), steps);
        }
    }

    enum Check { VALID, INVALID, MISSING_PARAMETERS, UNSUPPORTED, NUMERICALLY_UNDETERMINED }
    enum Measure { LENGTH, AREA }
    enum CoveragePurpose { AREA, AREA_WITHOUT_GAPS }

    sealed interface Measurement {
        record Value(double value) implements Measurement {
            public Value {
                if (!Double.isFinite(value) || value < 0) {
                    throw new IllegalArgumentException("Expected a finite nonnegative measurement");
                }
            }
        }

        record Unavailable(Check reason) implements Measurement {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
                if (reason == Check.VALID) {
                    throw new IllegalArgumentException("A valid measurement must have a value");
                }
            }
        }
    }

    /** Opens a new cursor over the same selection on every call. */
    interface Source {
        Cursor open();
        boolean complete();

        /** Whether coverage reports findings about the geometry of an individual input. */
        default boolean reportLocalFindings() {
            return false;
        }
    }

    /** The engine closes the cursor it opens. Empty denotes the end of the selection. */
    interface Cursor extends AutoCloseable {
        Optional<Input> next();
        @Override void close();
    }
}
