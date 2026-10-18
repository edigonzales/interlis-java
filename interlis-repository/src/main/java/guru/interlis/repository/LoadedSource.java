package guru.interlis.repository;

import guru.interlis.source.SourceId;

import java.util.Objects;

/**
 * Loaded material without a source revision; the workspace assigns that.
 *
 * @param sourceId the {@code sourceId} component
 * @param text the {@code text} component
 * @param byteSha256 the {@code byteSha256} component
 * @param origin origin information
 */
public record LoadedSource(SourceId sourceId, String text, String byteSha256, String origin) {
    /** Creates a validated instance. */
    public LoadedSource {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(byteSha256, "byteSha256");
        Objects.requireNonNull(origin, "origin");
    }
}
