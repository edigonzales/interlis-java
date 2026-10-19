package guru.interlis.compiler.internal.antlr;

import guru.interlis.model.LanguageVersion;

import java.util.Optional;

/** Explicit version profile for grammar constructs whose availability differs by INTERLIS version. */
final class LanguageProfile {
    enum Feature {
        MULTICOORD("MULTICOORD"),
        GENERIC_DOMAIN("GENERIC domain"),
        DEFERRED_GENERICS("DEFERRED GENERICS"),
        CONTEXT_DEFINITION("CONTEXT"),
        INLINE_ENUM_COLLECTION("inline enumeration collection");

        private final String label;

        Feature(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private LanguageProfile() {
    }

    static boolean supports(LanguageVersion version, Feature feature) {
        return version == LanguageVersion.ILI24;
    }

    static Optional<Feature> featureForToken(int tokenType) {
        return switch (tokenType) {
            case InterlisLexer.MULTICOORD -> Optional.of(Feature.MULTICOORD);
            case InterlisLexer.GENERIC -> Optional.of(Feature.GENERIC_DOMAIN);
            case InterlisLexer.DEFERRED -> Optional.of(Feature.DEFERRED_GENERICS);
            case InterlisLexer.CONTEXT -> Optional.of(Feature.CONTEXT_DEFINITION);
            default -> Optional.empty();
        };
    }
}
