package guru.interlis.syntax;

/** Closed declaration syntax hierarchy. */
public sealed interface DeclarationSyntax extends SyntaxNode permits ClassDeclarationSyntax,
        StructureDeclarationSyntax, DomainDeclarationSyntax, AssociationDeclarationSyntax,
        AttributeDeclarationSyntax, RoleDeclarationSyntax, ConstraintDeclarationSyntax,
        UnsupportedDeclarationSyntax {
}
