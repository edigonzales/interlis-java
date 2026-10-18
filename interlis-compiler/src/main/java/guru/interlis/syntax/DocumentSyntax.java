package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Root of one parsed source document.
 *
 * @param id stable identifier
 * @param range source range
 * @param models model identifiers
 */
public record DocumentSyntax(SyntaxNodeId id, SourceRange range, List<ModelSyntax> models)
        implements SyntaxNode {
    /** Creates a validated instance. */
    public DocumentSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        models = List.copyOf(Objects.requireNonNull(models, "models"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.DOCUMENT;
    }

    @Override
    public List<SyntaxNode> children() {
        return new ArrayList<>(models);
    }
}
