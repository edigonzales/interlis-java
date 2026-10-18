package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Original multi-segment name reference with per-segment targets.
 *
 * @param id stable identifier
 * @param originalScope the {@code originalScope} component
 * @param usage the {@code usage} component
 * @param segments the {@code segments} component
 * @param range source range
 * @param resolution the {@code resolution} component
 */
public record NameReference(ReferenceId id, ScopeId originalScope,
                            ReferenceUsage usage, List<ReferenceSegment> segments,
                            SourceRange range, BindingResolution resolution) {
    /** Creates a validated instance. */
    public NameReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(originalScope, "originalScope");
        Objects.requireNonNull(usage, "usage");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("name reference needs segments");
        }
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(resolution, "resolution");
    }
}
