package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Function call expression.
 *
 * @param id stable identifier
 * @param range source range
 * @param functionName called function name
 * @param arguments ordered arguments
 */
public record FunctionCallExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                           String functionName,
                                           List<ExpressionSyntax> arguments)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public FunctionCallExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(functionName, "functionName");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_FUNCTION_CALL;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.copyOf(arguments);
    }
}
