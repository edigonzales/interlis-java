package guru.interlis.syntax;

import guru.interlis.model.Modifier;
import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * CLASS declaration.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param nameRange the {@code nameRange} component
 * @param modifiers modifiers
 * @param baseType optional base type
 * @param members member identifiers
 * @param oidRule optional raw OID rule
 * @param endName optional name after END
 */
public record ClassDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                     Optional<SourceRange> nameRange, Set<Modifier> modifiers,
                                     Optional<NameSyntax> baseType, List<SyntaxNode> members,
                                     Optional<String> oidRule, Optional<String> endName)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public ClassDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        baseType = Objects.requireNonNull(baseType, "baseType");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        oidRule = Objects.requireNonNull(oidRule, "oidRule");
        endName = Objects.requireNonNull(endName, "endName");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.CLASS;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>();
        baseType.ifPresent(result::add);
        result.addAll(members);
        return result;
    }
}
