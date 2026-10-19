package guru.interlis.repository;

import guru.interlis.source.SourceId;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable content whose bytes have been verified against a digest.
 *
 * @param sourceId source identity
 * @param digest verified digest
 * @param bytes exact content bytes
 */
public record VerifiedContent(SourceId sourceId, ContentDigest digest, byte[] bytes) {
    /** Creates a validated instance. */
    public VerifiedContent {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(digest, "digest");
        bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        if (!digest.matches(bytes)) {
            throw new IllegalArgumentException("content digest does not match bytes");
        }
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
