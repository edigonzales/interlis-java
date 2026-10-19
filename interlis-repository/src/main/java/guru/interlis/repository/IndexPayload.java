package guru.interlis.repository;

import java.util.Objects;

/**
 * Raw repository index document; parsing is a host-side operation.
 *
 * @param content index document text
 */
public record IndexPayload(String content) {
    /** Creates a validated instance. */
    public IndexPayload {
        Objects.requireNonNull(content, "content");
    }
}
