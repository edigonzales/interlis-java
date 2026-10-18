package guru.interlis.projection;

/** Public factory for the standard projector. */
public final class DiagramProjectors {
    /**
     * Returns the {@code DiagramProjectors} value.
     *
     * @return the value
     */
    private DiagramProjectors() {
    }

    /**
     * Returns the {@code create} value.
     *
     * @return the value
     */
    public static DiagramProjector create() {
        /**
         * Returns the {@code DefaultDiagramProjector} value.
         *
         * @return the value
         */
        return new DefaultDiagramProjector();
    }
}
