package guru.interlis.validation;

import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.transfer.TransferGeometry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Optional operations on bound, immutable transfer geometry occurrences. */
public interface GeometryEngine {
    Check checkValue(Input input, Control control);

    Measurement measure(Measure operation, Input input, Control control);

    Check checkCoverage(Source source, CoveragePurpose purpose, Control control);

    /** A value with its effective model, exact coordinate quantum and resolved overlap in coordinate units. */
    record Input(TransferGeometry value, GeometryType type, DataLocation location, LanguageVersion language,
                 Optional<BigDecimal> quantum, Optional<BigDecimal> overlap, boolean partial) {
        public Input {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(quantum, "quantum");
            Objects.requireNonNull(overlap, "overlap");
        }
    }

    /** The caller supplies a cancellation-aware budget and a bounded finding sink. */
    record Control(Runnable visit, Consumer<Finding> findings) {
        public Control {
            Objects.requireNonNull(visit, "visit");
            Objects.requireNonNull(findings, "findings");
        }
    }

    /** A finding at a data occurrence, with an immutable path relative to that occurrence. */
    record Finding(DataLocation location, List<DataLocation.GeometryComponent> path, ValidationCode code,
                   String detail) {
        public Finding {
            Objects.requireNonNull(location, "location");
            path = List.copyOf(path);
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
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
    }

    /** The engine closes the cursor it opens. Empty denotes the end of the selection. */
    interface Cursor extends AutoCloseable {
        Optional<Input> next();
        @Override void close();
    }
}
