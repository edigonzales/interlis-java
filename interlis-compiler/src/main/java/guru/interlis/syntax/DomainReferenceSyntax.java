package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Reference to a named domain.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 */
public record DomainReferenceSyntax(SyntaxNodeId id, SourceRange range, NameSyntax name)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public DomainReferenceSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_DOMAIN_REFERENCE;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(name);
    }
}
