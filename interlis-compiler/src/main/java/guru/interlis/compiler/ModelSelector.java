package guru.interlis.compiler;

import guru.interlis.source.SourceId;

import java.util.Objects;
import java.util.Optional;

/** Explicit root model selection. */
public sealed interface ModelSelector permits ModelSelector.ByName, ModelSelector.BySource {
    record ByName(String modelName, Optional<String> modelVersion,
                  Optional<SourceId> sourceHint) implements ModelSelector {
        /** Creates a validated instance. */
        public ByName {
            Objects.requireNonNull(modelName, "modelName");
            if (modelName.isBlank()) {
                throw new IllegalArgumentException("modelName must not be blank");
            }
            modelVersion = Objects.requireNonNull(modelVersion, "modelVersion");
            sourceHint = Objects.requireNonNull(sourceHint, "sourceHint");
        }
    }

    record BySource(SourceId source) implements ModelSelector {
        /** Creates a validated instance. */
        public BySource {
            Objects.requireNonNull(source, "source");
        }
    }
}
