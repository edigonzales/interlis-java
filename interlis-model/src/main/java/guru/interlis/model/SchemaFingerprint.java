package guru.interlis.model;

import java.util.Objects;

/**
 * Algorithm-tagged semantic fingerprint; excludes run metadata and wall time.
 *
 * @param algorithm algorithm identifier
 * @param value wrapped value
 */
public record SchemaFingerprint(String algorithm, String value) {
    /** Creates a validated instance. */
    public SchemaFingerprint {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(value, "value");
        if (algorithm.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("fingerprint parts must not be blank");
        }
    }
}
