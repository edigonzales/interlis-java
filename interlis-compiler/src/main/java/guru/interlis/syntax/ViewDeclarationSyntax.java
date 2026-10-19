package guru.interlis.syntax;

import guru.interlis.model.Modifier;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * VIEW declaration with preserved formation and body text.
 *
 * @param id stable identifier
 * @param range source range
 * @param name view name
 * @param nameRange optional name range
 * @param modifiers modifiers
 * @param baseView optional base view
 * @param formation optional raw formation text
 * @param body optional raw body text
 * @param baseExtensions declared base extensions
 * @param allOf viewables included with ALL OF
 * @param attributeNames explicitly declared view attribute names
 * @param endName optional name after END
 */
public record ViewDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                    Optional<SourceRange> nameRange, Set<Modifier> modifiers,
                                    Optional<NameSyntax> baseView, Optional<String> formation,
                                    Optional<String> body,
                                    List<BaseExtension> baseExtensions,
                                    List<NameSyntax> allOf,
                                    List<String> attributeNames,
                                    Optional<String> endName)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public ViewDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        baseView = Objects.requireNonNull(baseView, "baseView");
        formation = Objects.requireNonNull(formation, "formation");
        body = Objects.requireNonNull(body, "body");
        baseExtensions = List.copyOf(Objects.requireNonNull(baseExtensions, "baseExtensions"));
        allOf = List.copyOf(Objects.requireNonNull(allOf, "allOf"));
        attributeNames = List.copyOf(Objects.requireNonNull(attributeNames, "attributeNames"));
        endName = Objects.requireNonNull(endName, "endName");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.VIEW;
    }

    /**
     * One BASE … EXTENDED BY … declaration.
     *
     * @param base base class
     * @param extensions extension classes
     */
    public record BaseExtension(NameSyntax base, List<NameSyntax> extensions) {
        /** Creates a validated instance. */
        public BaseExtension {
            Objects.requireNonNull(base, "base");
            extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions"));
        }
    }

    @Override
    public List<SyntaxNode> children() {
        return baseView.<List<SyntaxNode>>map(List::of).orElseGet(List::of);
    }
}
