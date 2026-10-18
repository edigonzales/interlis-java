package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import guru.interlis.model.Modifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Attribute declaration with its direct modifiers.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param nameRange the {@code nameRange} component
 * @param mandatory mandatory marker
 * @param transientAttribute transient marker
 * @param assigned whether an ASSIGN expression is present
 * @param modifiers modifiers
 * @param type type reference
 */
public record AttributeDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                         Optional<SourceRange> nameRange, boolean mandatory,
                                         boolean transientAttribute, boolean assigned,
                                         Set<Modifier> modifiers, TypeSyntax type)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public AttributeDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        Objects.requireNonNull(type, "type");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.ATTRIBUTE;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(type);
    }
}
