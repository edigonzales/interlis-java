package guru.interlis.syntax;

import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * Detected/assumed language version with its header range.
 *
 * @param version the {@code version} component
 * @param headerRange the {@code headerRange} component
 * @param state the {@code state} component
 * @param rawVersion the {@code rawVersion} component
 */
public record LanguageDetection(Optional<LanguageVersion> version,
                                Optional<SourceRange> headerRange,
                                DetectionState state,
                                Optional<String> rawVersion) {
    /** Creates a validated instance. */
    public LanguageDetection {
        version = Objects.requireNonNull(version, "version");
        headerRange = Objects.requireNonNull(headerRange, "headerRange");
        Objects.requireNonNull(state, "state");
        rawVersion = Objects.requireNonNull(rawVersion, "rawVersion");
        if (state == DetectionState.DETECTED && version.isEmpty()) {
            throw new IllegalArgumentException("detected language needs a version");
        }
    }

    /**
     * Executes {@code absent}.
     *
     * @return the result
     */
    public static LanguageDetection absent() {
        return new LanguageDetection(Optional.empty(), Optional.empty(),
                DetectionState.ABSENT, Optional.empty());
    }
}
