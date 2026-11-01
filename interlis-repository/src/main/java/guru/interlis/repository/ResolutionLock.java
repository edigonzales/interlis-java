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
public record ResolutionLock(Map<String, List<ModelPin>> models, Map<SourceId, ContentDigest> indexes,
        int formatVersion, String selectionProfile, List<RepositorySearchStep> steps,
        Map<SourceId, SourceLoadException.Reason> unavailable) {
    public static final String ILI2C_PROFILE = "ili2c-5.6.9";
    /** Existing callers construct legacy v1 locks without changing their replay contract. */
    public ResolutionLock(Map<String,List<ModelPin>> models, Map<SourceId,ContentDigest> indexes) {
        this(models,indexes,1,"explicit-v1",List.of(),Map.of());
    }
    public static ResolutionLock current(Map<String,List<ModelPin>> models, Map<SourceId,ContentDigest> indexes,
            List<RepositorySearchStep> steps, Map<SourceId,SourceLoadException.Reason> unavailable) {
        return new ResolutionLock(models,indexes,2,ILI2C_PROFILE,steps,unavailable);
    }
    private record Identity(String version, SourceId source) { }
    public ResolutionLock {
        Objects.requireNonNull(selectionProfile,"selectionProfile");
        if (formatVersion != 1 && formatVersion != 2) throw new IllegalArgumentException("unsupported lock version");
        if (!selectionProfile.equals(formatVersion == 1 ? "explicit-v1" : ILI2C_PROFILE)) throw new IllegalArgumentException("unsupported selection profile");
        steps = List.copyOf(Objects.requireNonNull(steps,"steps")); unavailable = Map.copyOf(Objects.requireNonNull(unavailable,"unavailable"));
        if (unavailable.keySet().stream().anyMatch(indexes::containsKey)) throw new IllegalArgumentException("input cannot be both available and unavailable");
        if (formatVersion == 1 && (!steps.isEmpty() || !unavailable.isEmpty())) throw new IllegalArgumentException("v1 lock has v2 data");
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
