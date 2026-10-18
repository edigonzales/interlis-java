package guru.interlis.syntax;

/** Closed expression-syntax hierarchy. */
public sealed interface ExpressionSyntax extends SyntaxNode permits LiteralExpressionSyntax,
        PathExpressionSyntax, BinaryExpressionSyntax, UnaryExpressionSyntax,
        DefinedExpressionSyntax, ConstantExpressionSyntax, UndefinedExpressionSyntax,
        ParenthesizedExpressionSyntax, UnsupportedExpressionSyntax {
}
