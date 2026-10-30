package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Null-free, deeply immutable host policy; pins are exact, offline uses verified cache only. */
public record RepositoryResolveOptions(RepositoryConfig repositories, Map<String, ModelPin> modelPins,
                                       Map<SourceId, ContentDigest> sourcePins, Optional<ResolutionLock> lock) {
    public RepositoryResolveOptions {
        Objects.requireNonNull(repositories, "repositories");
        modelPins = Map.copyOf(Objects.requireNonNull(modelPins, "modelPins"));
        sourcePins = SourceUris.pins(Objects.requireNonNull(sourcePins, "sourcePins"));
        lock = Objects.requireNonNull(lock, "lock");
        modelPins.forEach((name, pin) -> {
            if (!name.equals(pin.modelName())) throw new IllegalArgumentException("pin model key mismatch");
        });
    }
    public static RepositoryResolveOptions defaults(RepositoryConfig config) {
        return new RepositoryResolveOptions(config, Map.of(), Map.of(), Optional.empty());
    }
}
