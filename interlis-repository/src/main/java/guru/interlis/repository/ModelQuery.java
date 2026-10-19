package guru.interlis.repository;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit model lookup; no implicit "latest version" rule.
 *
 * @param modelName exact model name
 * @param modelVersion optional exact version
 * @param sourceHint optional expected model file URI
 */
public record ModelQuery(String modelName, Optional<String> modelVersion,
                         Optional<URI> sourceHint) {
    /** Creates a validated instance. */
    public ModelQuery {
        Objects.requireNonNull(modelName, "modelName");
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        modelVersion = Objects.requireNonNull(modelVersion, "modelVersion");
        sourceHint = Objects.requireNonNull(sourceHint, "sourceHint");
    }
}
