package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Translation declaration of a model.
 *
 * @param sourceModel canonical source model name
 * @param sourceVersion optional canonical source version
 * @param language optional translation language tag
 * @param sourceTarget bound original model; empty only in an erroneous analysis
 * @param origin optional declaration/configuration provenance
 * @param correspondences immutable structural correspondences in declaration order,
 *        followed by actually used import correspondences; all IDs belong to the
 *        owning graph. Empty if structural binding failed. A checked schema also
 *        requires semantic comparison of these structures to have succeeded.
 */
public record ModelTranslation(String sourceModel, Optional<String> sourceVersion,
                               Optional<String> language, Optional<SymbolId> sourceTarget,
                               java.util.List<TranslationLink> correspondences,Optional<Origin> origin) {
    /** Compatibility constructor without external configuration provenance. */
    public ModelTranslation(String sourceModel,Optional<String> sourceVersion,Optional<String> language,
            Optional<SymbolId> sourceTarget,java.util.List<TranslationLink> correspondences) {
        this(sourceModel,sourceVersion,language,sourceTarget,correspondences,Optional.empty());
    }
    /** Creates a validated instance. */
    public ModelTranslation {
        Objects.requireNonNull(sourceModel, "sourceModel");
        if (sourceModel.isBlank()) {
            throw new IllegalArgumentException("sourceModel must not be blank");
        }
        sourceVersion = Objects.requireNonNull(sourceVersion, "sourceVersion");
        language = Objects.requireNonNull(language, "language");
        sourceTarget = Objects.requireNonNull(sourceTarget, "sourceTarget");
        correspondences = java.util.List.copyOf(correspondences);
        origin=Objects.requireNonNull(origin,"origin");
    }
}
