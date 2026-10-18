package guru.interlis.syntax;

import java.util.Objects;

/**
 * Syntax identity: text, grammar, profile, options and mode.
 *
 * @param algorithm algorithm identifier
 * @param value wrapped value
 */
public record SyntaxFingerprint(String algorithm, String value) {
    /** Creates a validated instance. */
    public SyntaxFingerprint {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(value, "value");
        if (algorithm.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("fingerprint parts must not be blank");
        }
    }
}
