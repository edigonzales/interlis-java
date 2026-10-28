package guru.interlis.syntax;

/** Closed declaration syntax hierarchy. */
public sealed interface DeclarationSyntax extends SyntaxNode permits GraphicDeclarationSyntax, GraphicDeclarationSyntax.Rule, ClassDeclarationSyntax,
        StructureDeclarationSyntax, DomainDeclarationSyntax, AssociationDeclarationSyntax,
        AttributeDeclarationSyntax, RoleDeclarationSyntax, ConstraintDeclarationSyntax,
        MetadataBasketDeclarationSyntax, MetadataBasketDeclarationSyntax.ObjectDeclaration, LineFormDeclarationSyntax, ContextDeclarationSyntax, ParameterDeclarationSyntax, UnitDeclarationSyntax, FunctionDeclarationSyntax, ViewDeclarationSyntax, ExternalConstraintSyntax,
        UnsupportedDeclarationSyntax {
}
