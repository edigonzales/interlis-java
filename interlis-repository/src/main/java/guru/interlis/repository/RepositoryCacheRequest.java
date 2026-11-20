package guru.interlis.repository;

import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, null-free application-cache policy. Age starts at the last full
 * download, never the last read. Zero means unconditional online loading.
 * Optional index MD5 does not replace SHA-256 integrity or explicit pins/locks.
 */
public record RepositoryCacheRequest(Duration maxAge, Optional<String> expectedMd5) {
    public RepositoryCacheRequest {
        Objects.requireNonNull(maxAge, "maxAge"); Objects.requireNonNull(expectedMd5, "expectedMd5");
        if (maxAge.isNegative()) throw new IllegalArgumentException("negative cache age");
    }
    /** Reuse repository metadata for 24 hours. */
    public static RepositoryCacheRequest metadata() { return new RepositoryCacheRequest(Duration.ofDays(1), Optional.empty()); }
    /** Reuse models for seven days subject to the optional published MD5. */
    public static RepositoryCacheRequest model(Optional<String> md5) { return new RepositoryCacheRequest(Duration.ofDays(7), md5); }
    /** Always download online; offline access still uses verified local contents. */
    public static RepositoryCacheRequest unconditional() { return new RepositoryCacheRequest(Duration.ZERO, Optional.empty()); }

    boolean matchesExpectedMd5(byte[] bytes) {
        if (expectedMd5.isEmpty()) return true;
        try {
            var actual = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
            return expectedMd5.orElseThrow().equalsIgnoreCase(actual);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
