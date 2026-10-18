package guru.interlis.workspace;

import java.util.Objects;

/**
 * Workspace capture fingerprint; distinct from compiler fingerprint types.
 *
 * @param algorithm algorithm identifier
 * @param value wrapped value
 */
public record InputFingerprint(String algorithm, String value) {
    /** Creates a validated instance. */
    public InputFingerprint {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(value, "value");
        if (algorithm.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("fingerprint parts must not be blank");
        }
    }
}
