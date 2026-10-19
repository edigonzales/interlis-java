package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Translation declaration of a model.
 *
 * @param sourceModel canonical source model name
 * @param sourceVersion optional canonical source version
 * @param language optional translation language tag
 */
public record ModelTranslation(String sourceModel, Optional<String> sourceVersion,
                               Optional<String> language) {
    /** Creates a validated instance. */
    public ModelTranslation {
        Objects.requireNonNull(sourceModel, "sourceModel");
        if (sourceModel.isBlank()) {
            throw new IllegalArgumentException("sourceModel must not be blank");
        }
        sourceVersion = Objects.requireNonNull(sourceVersion, "sourceVersion");
        language = Objects.requireNonNull(language, "language");
    }
}
