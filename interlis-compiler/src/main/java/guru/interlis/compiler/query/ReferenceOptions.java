package guru.interlis.compiler.query;

/**
 * Reference search scope.
 *
 * @param includeDeclaration the {@code includeDeclaration} component
 */
public record ReferenceOptions(boolean includeDeclaration) {
    /**
     * Returns the {@code defaults} value.
     *
     * @return the value
     */
    public static ReferenceOptions defaults() {
        return new ReferenceOptions(true);
    }
}
