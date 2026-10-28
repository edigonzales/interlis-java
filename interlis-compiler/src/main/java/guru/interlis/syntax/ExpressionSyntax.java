package guru.interlis.syntax;

/** Closed expression-syntax hierarchy. */
public sealed interface ExpressionSyntax extends SyntaxNode permits LiteralExpressionSyntax,
        AttributeConstantExpressionSyntax, ParameterExpressionSyntax, PathExpressionSyntax, BinaryExpressionSyntax, UnaryExpressionSyntax,
        DefinedExpressionSyntax, ConstantExpressionSyntax, UndefinedExpressionSyntax,
        ParenthesizedExpressionSyntax, FunctionCallExpressionSyntax, BuiltinFunctionExpressionSyntax,
        EnumerationConstantExpressionSyntax, ClassConstantExpressionSyntax, GenericConstantExpressionSyntax,
        ObjectSetExpressionSyntax, InspectionExpressionSyntax, UnsupportedExpressionSyntax {
}
