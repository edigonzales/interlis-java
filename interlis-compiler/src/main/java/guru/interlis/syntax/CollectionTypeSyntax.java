package guru.interlis.syntax;

import guru.interlis.model.Cardinality;
import guru.interlis.model.CollectionKind;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * BAG/LIST OF element type.
 *
 * @param id stable identifier
 * @param range source range
 * @param collectionKind the {@code collectionKind} component
 * @param cardinality cardinality
 * @param elementType the {@code elementType} component
 */
public record CollectionTypeSyntax(SyntaxNodeId id, SourceRange range,
                                   CollectionKind collectionKind, Cardinality cardinality,
                                   TypeSyntax elementType) implements TypeSyntax {
    /** Creates a validated instance. */
    public CollectionTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(collectionKind, "collectionKind");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(elementType, "elementType");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_COLLECTION;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(elementType);
    }
}
