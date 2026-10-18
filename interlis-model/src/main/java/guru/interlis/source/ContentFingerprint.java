package guru.interlis.source;

import java.util.Objects;

/**
 * Algorithm-tagged content digest; never a validity proof.
 *
 * @param algorithm digest algorithm identifier, never blank
 * @param value lower-case hexadecimal digest, never blank
 */
public record ContentFingerprint(String algorithm, String value) {
    /** Creates a validated instance. */
    public ContentFingerprint {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(value, "value");
        if (algorithm.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("fingerprint parts must not be blank");
        }
    }

    /**
     * Executes {@code sha256}.
     *
     * @param hex the {@code hex} argument
     *
     * @return the result
     */
    public static ContentFingerprint sha256(String hex) {
        return new ContentFingerprint("SHA-256/UTF-16BE", hex);
    }
}
