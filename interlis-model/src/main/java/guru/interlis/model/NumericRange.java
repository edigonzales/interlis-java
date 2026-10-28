package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Exact numeric interval with optional bound unit and reference-system/axis identity.
 *
 * @param minimum the {@code minimum} component
 * @param maximum the {@code maximum} component
 * @param circular the {@code circular} component
 * @param direction the {@code direction} component
 * @param unit the {@code unit} component
 * @param referenceSystem the {@code referenceSystem} component
 */
public record NumericRange(Optional<DecimalLiteral> minimum, Optional<DecimalLiteral> maximum,
                           boolean circular, NumericDirection direction,
                           Optional<SymbolId> unit, Optional<ReferenceSystem> referenceSystem) {
    /** Creates a validated instance. */
    public NumericRange {
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(direction, "direction");
        unit = Objects.requireNonNull(unit, "unit");
        referenceSystem = Objects.requireNonNull(referenceSystem, "referenceSystem");
        if (minimum.isPresent() && maximum.isPresent()
                && minimum.get().exactValue().compareTo(maximum.get().exactValue()) > 0) {
            throw new IllegalArgumentException("numeric minimum exceeds maximum");
        }
    }

    /**
     * Executes {@code of}.
     *
     * @param minimum the {@code minimum} argument
     * @param maximum the {@code maximum} argument
     *
     * @return the result
     */
    public static NumericRange of(DecimalLiteral minimum, DecimalLiteral maximum) {
        return new NumericRange(Optional.of(minimum), Optional.of(maximum), false,
                NumericDirection.NONE, Optional.empty(), Optional.empty());
    }

    /**
     * Returns the {@code unbounded} value.
     *
     * @return the value
     */
    public static NumericRange unbounded() {
        return new NumericRange(Optional.empty(), Optional.empty(), false,
                NumericDirection.NONE, Optional.empty(), Optional.empty());
    }
    /** Reference to a declared metadata object or numeric/coordinate domain; axis numbers are one-based. */
    public record ReferenceSystem(SymbolId target, boolean domain, java.util.OptionalInt axis) {
        public ReferenceSystem {
            Objects.requireNonNull(target); Objects.requireNonNull(axis);
            if (axis.isPresent() && axis.getAsInt() < 1) throw new IllegalArgumentException("axis must be positive");
        }
    }
}
