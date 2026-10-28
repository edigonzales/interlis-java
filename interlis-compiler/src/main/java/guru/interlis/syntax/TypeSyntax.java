package guru.interlis.syntax;

/** Closed type-syntax hierarchy. */
public sealed interface TypeSyntax extends SyntaxNode permits EnumArgumentTypeSyntax, MetaObjectTypeSyntax, TextTypeSyntax,
        NumericTypeSyntax, BooleanTypeSyntax, EnumerationTypeSyntax,
        DomainReferenceSyntax, ReferenceTypeSyntax, StructureValueTypeSyntax, CollectionTypeSyntax,
        CoordinateTypeSyntax, TemporalTypeSyntax, OidTypeSyntax, BlackboxTypeSyntax,
        AlignmentTypeSyntax, FormattedTypeSyntax, GenericTypeSyntax, EnumTreeValueSyntax, LineTypeSyntax, ClassValueTypeSyntax, AttributePathTypeSyntax, ObjectArgumentTypeSyntax, UnsupportedTypeSyntax {
}
