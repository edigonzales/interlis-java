package guru.interlis.compiler;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.Objects;
import java.util.Optional;

/**
 * Concrete model declaration location in a source revision.
 *
 * @param source source identifier
 * @param revision source revision
 * @param modelName the {@code modelName} component
 * @param modelVersion model version
 * @param declarationOffset the {@code declarationOffset} component
 */
public record ModelLocation(SourceId source, SourceRevision revision, String modelName,
                            Optional<String> modelVersion, int declarationOffset) {
    /** Creates a validated instance. */
    public ModelLocation {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(modelName, "modelName");
        modelVersion = Objects.requireNonNull(modelVersion, "modelVersion");
        if (declarationOffset < 0) {
            throw new IllegalArgumentException("declarationOffset must be non-negative");
        }
    }
}
