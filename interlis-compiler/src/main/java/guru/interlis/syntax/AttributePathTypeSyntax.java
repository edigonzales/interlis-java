package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** ATTRIBUTE with an optional OF path or function-argument name and ordered type restrictions. */
public record AttributePathTypeSyntax(SyntaxNodeId id, SourceRange range, Optional<ExpressionSyntax> ownerClass,
        Optional<NameSyntax> argument, List<TypeSyntax> restrictions) implements TypeSyntax {
    public AttributePathTypeSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(ownerClass); Objects.requireNonNull(argument);
        restrictions = List.copyOf(restrictions);
        if (ownerClass.isPresent() && argument.isPresent()) throw new IllegalArgumentException("two OF sources");
    }
    @Override public SyntaxKind kind() { return SyntaxKind.TYPE_ATTRIBUTE_PATH; }
    @Override public List<SyntaxNode> children() {
        List<SyntaxNode> nodes = new ArrayList<>(); ownerClass.ifPresent(nodes::add); argument.ifPresent(nodes::add);
        nodes.addAll(restrictions); return List.copyOf(nodes);
    }
}
