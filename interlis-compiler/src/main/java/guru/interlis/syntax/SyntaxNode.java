package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;

/** Own lossless syntax representation; ANTLR types never cross this boundary. */
public sealed interface SyntaxNode permits DocumentSyntax, ModelSyntax, TopicSyntax,
        ImportSyntax, DeclarationSyntax, TypeSyntax, ExpressionSyntax, NameSyntax,
        ErrorSyntax, RecoveredSyntax, SkippedSyntax {

    SyntaxNodeId id();

    SyntaxKind kind();

    SourceRange range();

    List<SyntaxNode> children();

    /**
     * Executes {@code synthetic}.
     *
     * @return the result
     */
    default boolean synthetic() {
        return false;
    }

    /**
     * Executes {@code width}.
     *
     * @return the result
     */
    default int width() {
        return range().length();
    }
}
