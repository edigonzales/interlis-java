package guru.interlis.workspace.resolution;

/** Provides an already frozen source inventory; never performs IO. */
@FunctionalInterface
public interface SourceProvider {
    /**
     * Returns the {@code snapshot} value.
     *
     * @return the value
     */
    SourceInventory snapshot();
}
