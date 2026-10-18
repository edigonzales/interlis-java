package guru.interlis.workspace.resolution;

import java.util.List;
import java.util.Objects;

/**
 * Ordered, non-secret explanation of resolver decisions.
 *
 * @param decisions the {@code decisions} component
 */
public record ResolutionTrace(List<String> decisions) {
    /** Creates a validated instance. */
    public ResolutionTrace {
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
    }

    /**
     * Returns the {@code empty} value.
     *
     * @return the value
     */
    public static ResolutionTrace empty() {
        return new ResolutionTrace(List.of());
    }
}
