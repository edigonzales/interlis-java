package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * INTERLIS built-in function call.
 *
 * @param id stable identifier
 * @param range source range
 * @param functionName built-in name
 * @param arguments ordered arguments
 */
public record BuiltinFunctionExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                              NameSyntax functionName,
                                              List<ExpressionSyntax> arguments)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public BuiltinFunctionExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(functionName, "functionName");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_BUILTIN;
    }

    @Override
    public List<SyntaxNode> children() {
        java.util.ArrayList<SyntaxNode> children=new java.util.ArrayList<>();
        children.add(functionName); children.addAll(arguments); return List.copyOf(children);
    }
}
