package guru.interlis.syntax;

/** Closed type-syntax hierarchy. */
public sealed interface TypeSyntax extends SyntaxNode permits TextTypeSyntax,
        NumericTypeSyntax, BooleanTypeSyntax, EnumerationTypeSyntax,
        DomainReferenceSyntax, ReferenceTypeSyntax, CollectionTypeSyntax,
        CoordinateTypeSyntax, UnsupportedTypeSyntax {
}
