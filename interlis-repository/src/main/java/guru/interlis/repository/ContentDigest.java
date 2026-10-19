package guru.interlis.repository;

import java.util.HexFormat;
import java.util.Objects;

/**
 * Digest identifying exact loaded bytes.
 *
 * @param algorithm digest algorithm; currently only {@code sha256}
 * @param value lowercase hexadecimal digest
 */
public record ContentDigest(String algorithm, String value) {
    /** Creates a validated instance. */
    public ContentDigest {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(value, "value");
        if (!"sha256".equals(algorithm)) {
            throw new IllegalArgumentException("unsupported digest algorithm");
        }
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid sha256 digest");
        }
    }

    /**
     * Computes the digest of exact bytes.
     *
     * @param bytes content bytes
     * @return sha256 digest
     */
    public static ContentDigest sha256(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new ContentDigest("sha256",
                HexFormat.of().formatHex(guru.interlis.source.Sha256.digest(bytes)));
    }

    /**
     * Checks exact bytes against this digest.
     *
     * @param bytes content bytes
     * @return true if the bytes match
     */
    public boolean matches(byte[] bytes) {
        return equals(sha256(Objects.requireNonNull(bytes, "bytes")));
    }
}
