package guru.interlis.workspace.resolution;

import java.util.Objects;

/**
 * Deterministic selection policy for the pure resolver.
 *
 * @param preferWorkspaceSource the {@code preferWorkspaceSource} component
 */
public record ResolutionPolicy(boolean preferWorkspaceSource) {
    /**
     * Returns the {@code defaults} value.
     *
     * @return the value
     */
    public static ResolutionPolicy defaults() {
        return new ResolutionPolicy(true);
    }

    /** Creates a validated instance. */
    public ResolutionPolicy {
        Objects.requireNonNull(preferWorkspaceSource, "preferWorkspaceSource");
    }
}
