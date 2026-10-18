package guru.interlis.compiler;

import java.util.Objects;

/**
 * Resolver provenance; never a substitute for source-content identity.
 *
 * @param value wrapped value
 */
public record ResolutionFingerprint(String value) {
    /** Creates a validated instance. */
    public ResolutionFingerprint {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ResolutionFingerprint must not be blank");
        }
    }

    /**
     * Returns the {@code none} value.
     *
     * @return the value
     */
    public static ResolutionFingerprint none() {
        return new ResolutionFingerprint("resolution:none");
    }
}
