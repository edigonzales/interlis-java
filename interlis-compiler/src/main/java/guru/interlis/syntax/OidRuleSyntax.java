package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.Objects;
import java.util.Optional;

/** One declared OID rule. Empty domain means explicit NO OID (never a basket rule).
 * The name and complete rule range retain their source revision; all values are immutable. */
public record OidRuleSyntax(boolean basket, Optional<NameSyntax> domain, SourceRange range) {
    public OidRuleSyntax {
        Objects.requireNonNull(domain); Objects.requireNonNull(range);
        if (basket && domain.isEmpty()) throw new IllegalArgumentException("basket rule requires a domain");
    }
}
