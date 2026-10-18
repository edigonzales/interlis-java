package guru.interlis.model;

import java.util.Objects;

/**
 * Real source name or explicitly synthetic display name.
 *
 * @param value wrapped value
 * @param synthetic the {@code synthetic} component
 */
public record DeclaredName(String value, boolean synthetic) {
    /** Creates a validated instance. */
    public DeclaredName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("DeclaredName must not be blank");
        }
    }

    /**
     * Executes {@code declared}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public static DeclaredName declared(String value) {
        return new DeclaredName(value, false);
    }

    /**
     * Executes {@code synthetic}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public static DeclaredName synthetic(String value) {
        return new DeclaredName(value, true);
    }

    @Override
    public String toString() {
        return value;
    }
}
