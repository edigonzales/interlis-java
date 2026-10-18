package guru.interlis.compiler;

import java.util.Objects;

/**
 * Actual input identity computed from texts, bindings and options.
 *
 * @param algorithm algorithm identifier
 * @param value wrapped value
 */
public record CompilationFingerprint(String algorithm, String value) {
    /** Creates a validated instance. */
    public CompilationFingerprint {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(value, "value");
        if (algorithm.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("fingerprint parts must not be blank");
        }
    }
}
