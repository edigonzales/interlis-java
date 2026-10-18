package guru.interlis.compiler;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable, typed semantic overlay input.
 *
 * @param overlays the {@code overlays} component
 */
public record SemanticOverlays(List<SemanticOverlay> overlays) {
    /** Creates a validated instance. */
    public SemanticOverlays {
        overlays = List.copyOf(Objects.requireNonNull(overlays, "overlays"));
    }

    /**
     * Returns the {@code empty} value.
     *
     * @return the value
     */
    public static SemanticOverlays empty() {
        return new SemanticOverlays(List.of());
    }

    /**
     * Returns the {@code fingerprint} value.
     *
     * @return the value
     */
    public String fingerprint() {
        return overlays.stream()
                .map(o -> o.kind() + ":" + o.fingerprint())
                .sorted()
                .collect(Collectors.joining("|", "overlays[", "]"));
    }

    public sealed interface SemanticOverlay permits SemanticOverlay.TranslationOverlay, SemanticOverlay.CrsOverlay,
            SemanticOverlay.MetaAttributeOverlay {
        /**
         * Returns the {@code kind} value.
         *
         * @return the value
         */
        String kind();

        /**
         * Returns the {@code fingerprint} value.
         *
         * @return the value
         */
        String fingerprint();

        record TranslationOverlay(String fingerprint) implements SemanticOverlay {
            /** Creates a validated instance. */
            public TranslationOverlay {
                Objects.requireNonNull(fingerprint, "fingerprint");
            }

            @Override
            public String kind() {
                return "translation";
            }
        }

        record CrsOverlay(String fingerprint) implements SemanticOverlay {
            /** Creates a validated instance. */
            public CrsOverlay {
                Objects.requireNonNull(fingerprint, "fingerprint");
            }

            @Override
            public String kind() {
                return "crs";
            }
        }

        record MetaAttributeOverlay(String fingerprint) implements SemanticOverlay {
            /** Creates a validated instance. */
            public MetaAttributeOverlay {
                Objects.requireNonNull(fingerprint, "fingerprint");
            }

            @Override
            public String kind() {
                return "meta-attribute";
            }
        }
    }
}
