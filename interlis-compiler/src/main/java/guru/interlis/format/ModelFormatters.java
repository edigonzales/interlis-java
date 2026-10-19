package guru.interlis.format;

/** Public formatter factory. */
public final class ModelFormatters {
    private ModelFormatters() {
    }

    /**
     * Creates the default formatter.
     *
     * @return formatter
     */
    public static ModelFormatter create() {
        return new DefaultModelFormatter();
    }
}
