package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.util.*;

/**
 * Immutable reproducible remote closure. Each model-name group contains every
 * actually selected version/source identity, sorted by raw version then URI.
 * Sorting is only serialization order, never a latest-version selection rule.
 * Distinct translations can require different versions or sources of one name.
 * Every used index has an exact byte digest; replay cannot add remote identities.
 */
public record ResolutionLock(Map<String, List<ModelPin>> models, Map<SourceId, ContentDigest> indexes) {
    private record Identity(String version, SourceId source) { }
    public ResolutionLock {
        Objects.requireNonNull(models, "models");
        var copy = new LinkedHashMap<String, List<ModelPin>>();
        models.forEach((name, pins) -> {
            Objects.requireNonNull(name, "model name");
            var values = List.copyOf(Objects.requireNonNull(pins, "model pins"));
            if (values.isEmpty()) throw new IllegalArgumentException("empty model lock group");
            var identities = new HashSet<Identity>();
            for (var pin : values) {
                if (!name.equals(pin.modelName())) throw new IllegalArgumentException("lock model key mismatch");
                if (!identities.add(new Identity(pin.modelVersion(), pin.source()))) throw new IllegalArgumentException("duplicate locked model identity");
            }
            copy.put(name, values.stream().sorted(Comparator.comparing(ModelPin::modelVersion)
                    .thenComparing(pin -> pin.source().value())).toList());
        });
        models = Map.copyOf(copy);
        indexes = SourceUris.pins(Objects.requireNonNull(indexes, "indexes"));
    }
}
