package guru.interlis.compiler;

import java.util.Objects;
import java.util.Set;

/**
 * Versioned normative semantic profile.
 *
 * @param value wrapped value
 */
public record SemanticProfileId(String value) {
    private static final Set<String> KNOWN = Set.of("interlis-2.3-2.4/v1");
    public static final SemanticProfileId ILI_23_24_V1 = new SemanticProfileId("interlis-2.3-2.4/v1");

    /** Creates a validated instance. */
    public SemanticProfileId {
        Objects.requireNonNull(value, "value");
        if (!KNOWN.contains(value)) {
            throw new IllegalArgumentException("unknown semantic profile: " + value);
        }
    }
}
