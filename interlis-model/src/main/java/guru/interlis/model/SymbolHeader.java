package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Common immutable symbol head.
 *
 * @param id stable identifier
 * @param kind kind discriminator
 * @param name declared name
 * @param owner owner identifier
 * @param declarationOrder the {@code declarationOrder} component
 * @param origin origin information
 * @param nameRange the {@code nameRange} component
 * @param languageVersion the {@code languageVersion} component
 * @param documentation the {@code documentation} component
 * @param metaAttributes the {@code metaAttributes} component
 * @param modifiers modifiers
 * @param oidRule optional raw OID rule declaration
 */
public record SymbolHeader(SymbolId id, SymbolKind kind, DeclaredName name,
                           Optional<SymbolId> owner, int declarationOrder,
                           Origin origin, Optional<SourceRange> nameRange,
                           LanguageVersion languageVersion, Documentation documentation,
                           MetaAttributeSet metaAttributes, Set<Modifier> modifiers,
                           Optional<String> oidRule) {
    /** Creates a validated instance. */
    public SymbolHeader {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        owner = Objects.requireNonNull(owner, "owner");
        if (declarationOrder < 0) {
            throw new IllegalArgumentException("declarationOrder must be non-negative");
        }
        Objects.requireNonNull(origin, "origin");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(languageVersion, "languageVersion");
        Objects.requireNonNull(documentation, "documentation");
        Objects.requireNonNull(metaAttributes, "metaAttributes");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        oidRule = Objects.requireNonNull(oidRule, "oidRule");
        if (owner.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("symbol cannot own itself");
        }
    }

    /**
     * Executes {@code has}.
     *
     * @param modifier the {@code modifier} argument
     *
     * @return the result
     */
    public boolean has(Modifier modifier) {
        return modifiers.contains(modifier);
    }
}
