package guru.interlis.compiler;

import java.util.Objects;

/**
 * Resolver-selected original for one actual source or configured translation.
 * Both locations include source revision, exact version and declaration offset.
 * The compiler rechecks both identities and the declared/configured relationship;
 * this value does not import a namespace or certify language validity.
 */
public record TranslationBinding(ModelLocation translated, ModelLocation original) {
    public TranslationBinding {
        Objects.requireNonNull(translated, "translated");
        Objects.requireNonNull(original, "original");
    }
}
