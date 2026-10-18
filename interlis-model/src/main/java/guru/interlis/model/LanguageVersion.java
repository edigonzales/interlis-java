package guru.interlis.model;

/** The only supported INTERLIS language versions. */
public enum LanguageVersion {
    /** The {@code ILI23} constant. */
    ILI23("2.3"),
    /** The {@code ILI24} constant. */
    ILI24("2.4");

    private final String literal;

    LanguageVersion(String literal) {
        this.literal = literal;
    }

    /**
     * Returns the {@code literal} value.
     *
     * @return the value
     */
    public String literal() {
        return literal;
    }

    /**
     * Executes {@code fromLiteral}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public static java.util.Optional<LanguageVersion> fromLiteral(String value) {
        for (LanguageVersion version : values()) {
            if (version.literal.equals(value)) {
                return java.util.Optional.of(version);
            }
        }
        return java.util.Optional.empty();
    }
}
