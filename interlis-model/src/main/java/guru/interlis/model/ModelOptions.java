package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/** Immutable explicit model options. Empty characterSet means the version's normative
 * appendix-D repertoire, not an encoding or a platform charset. Empty xmlNamespace
 * uses the versioned model-name rule. Origins retain original source revisions;
 * absence never invents a source location. Values contain decoded source strings. */
public record ModelOptions(Optional<TextValue> language, Optional<Origin> noIncrementalTransfer,
                           Optional<TextValue> characterSet, Optional<TextValue> xmlNamespace) {
    public ModelOptions {
        language = Objects.requireNonNull(language, "language");
        noIncrementalTransfer = Objects.requireNonNull(noIncrementalTransfer, "noIncrementalTransfer");
        characterSet = Objects.requireNonNull(characterSet, "characterSet");
        xmlNamespace = Objects.requireNonNull(xmlNamespace, "xmlNamespace");
    }
    public static ModelOptions defaults() { return new ModelOptions(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()); }
    public boolean incrementalTransferAllowed() { return noIncrementalTransfer.isEmpty(); }
    public boolean usesDefaultCharacterRepertoire() { return characterSet.isEmpty(); }

    /** One explicit decoded string and its provenance; null-free, immutable. */
    public record TextValue(String value, Origin origin) {
        public TextValue { Objects.requireNonNull(value, "value"); Objects.requireNonNull(origin, "origin"); }
    }
}
