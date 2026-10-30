package guru.interlis.repository;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.source.SourceId;
import java.util.Objects;
import java.util.Optional;

/** Exact model identity and original-byte digest; version strings are opaque. */
public record ModelPin(String modelName, String modelVersion, SourceId source, ContentDigest digest) {
    public ModelPin {
        Objects.requireNonNull(modelName, "modelName"); Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(source, "source"); Objects.requireNonNull(digest, "digest");
        source = new SourceId(SourceUris.canonicalFile(source.value()));
        if (modelName.isBlank()) throw new IllegalArgumentException("blank model name");
    }
    public ModelSelector.ByName selector() {
        return new ModelSelector.ByName(modelName, Optional.of(modelVersion), Optional.of(source));
    }
}
