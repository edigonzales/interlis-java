package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UNIT declaration.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared unit name
 * @param nameRange optional name range
 * @param abstractUnit whether the unit is abstract
 * @param base optional base unit
 * @param definition source definition text
 */
public record UnitDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                    Optional<SourceRange> nameRange, boolean abstractUnit,
                                    Optional<NameSyntax> base, String definition)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public UnitDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        base = Objects.requireNonNull(base, "base");
        Objects.requireNonNull(definition, "definition");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.UNIT;
    }

    @Override
    public List<SyntaxNode> children() {
        return base.<List<SyntaxNode>>map(List::of).orElseGet(List::of);
    }
}
