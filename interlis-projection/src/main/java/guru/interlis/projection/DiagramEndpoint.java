package guru.interlis.projection;

import java.util.Objects;
import java.util.Optional;

/**
 * Existing node/container endpoint.
 *
 * @param elementId the {@code elementId} component
 * @param memberId the {@code memberId} component
 */
public record DiagramEndpoint(String elementId, Optional<String> memberId) {
    /** Creates a validated instance. */
    public DiagramEndpoint {
        Objects.requireNonNull(elementId, "elementId");
        memberId = Objects.requireNonNull(memberId, "memberId");
    }
}
