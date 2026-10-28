package guru.interlis.syntax;

import guru.interlis.model.OidKind;
import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * Object identifier type.
 *
 * @param id stable identifier
 * @param range source range
 * @param oidKind the {@code oidKind} component
 */
public record OidTypeSyntax(SyntaxNodeId id, SourceRange range, OidKind oidKind, java.util.Optional<TypeSyntax> valueType)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public OidTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(oidKind, "oidKind");
        Objects.requireNonNull(valueType, "valueType");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_OID;
    }

    @Override
    public java.util.List<SyntaxNode> children() {
        return valueType.stream().map(SyntaxNode.class::cast).toList();
    }
}
