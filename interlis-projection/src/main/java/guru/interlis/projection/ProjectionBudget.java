package guru.interlis.projection;

/**
 * Explicit projection limits; exceeding them yields LIMITED.
 *
 * @param maxNodes the {@code maxNodes} component
 * @param maxEdges the {@code maxEdges} component
 * @param maxMembersPerNode the {@code maxMembersPerNode} component
 */
public record ProjectionBudget(int maxNodes, int maxEdges, int maxMembersPerNode) {
    /** Creates a validated instance. */
    public ProjectionBudget {
        if (maxNodes <= 0 || maxEdges <= 0 || maxMembersPerNode <= 0) {
            throw new IllegalArgumentException("projection bounds must be positive");
        }
    }

    /**
     * Returns the {@code defaults} value.
     *
     * @return the value
     */
    public static ProjectionBudget defaults() {
        return new ProjectionBudget(2_000, 4_000, 500);
    }
}
