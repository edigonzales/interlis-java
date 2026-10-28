package guru.interlis.model;

import java.util.Objects;

/** A bound original/translation correspondence within one semantic snapshot.
 * Declaration identities and names remain distinct. A link records structural
 * correspondence, not a transfer name or a global replacement of either ID. */
public record TranslationLink(SymbolId original, SymbolId translated) {
    public TranslationLink { Objects.requireNonNull(original); Objects.requireNonNull(translated); }
}
