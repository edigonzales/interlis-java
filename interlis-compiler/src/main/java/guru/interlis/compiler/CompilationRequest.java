package guru.interlis.compiler;

import java.util.List;
import java.util.Objects;

/**
 * Complete immutable input of one compiler run.
 *
 * @param roots the {@code roots} component
 * @param sources source identifiers
 * @param options options
 */
public record CompilationRequest(List<ModelSelector> roots, SourceSet sources,
                                 CompilerOptions options) {
    /** Creates a validated instance. */
    public CompilationRequest {
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("at least one root selector is required");
        }
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(options, "options");
    }
}
