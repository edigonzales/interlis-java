package guru.interlis.syntax;
import guru.interlis.source.SourceRange;
import guru.interlis.model.MetadataBasketSymbol;
import guru.interlis.model.Modifier;
import java.util.*;

/** Metadata basket with exact references and locally declared objects, all in source order. */
public record MetadataBasketDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
        Optional<SourceRange> nameRange, Set<Modifier> modifiers, MetadataBasketSymbol.Kind basketKind,
        NameSyntax topic, Optional<NameSyntax> baseBasket, List<ObjectDeclaration> objects) implements DeclarationSyntax {
    public MetadataBasketDeclarationSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(name); Objects.requireNonNull(nameRange);
        modifiers = Set.copyOf(modifiers); Objects.requireNonNull(basketKind); Objects.requireNonNull(topic);
        Objects.requireNonNull(baseBasket); objects = List.copyOf(objects);
    }
    public record ObjectDeclaration(SyntaxNodeId id, SourceRange range, String name, NameSyntax objectClass) implements DeclarationSyntax {
        public ObjectDeclaration { Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(name); Objects.requireNonNull(objectClass); }
        @Override public SyntaxKind kind() { return SyntaxKind.METAOBJECT; }
        @Override public List<SyntaxNode> children() { return List.of(objectClass); }
    }
    @Override public SyntaxKind kind() { return SyntaxKind.METADATA_BASKET; }
    @Override public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>(); baseBasket.ifPresent(result::add); result.add(topic); result.addAll(objects); return List.copyOf(result);
    }
}
