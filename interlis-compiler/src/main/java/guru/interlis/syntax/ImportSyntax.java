package guru.interlis.syntax;

import guru.interlis.model.SymbolId;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One IMPORTS entry with its original range.
 *
 * @param id stable identifier
 * @param range source range
 * @param modelName the {@code modelName} component
 * @param unqualified the {@code unqualified} component
 * @param targetModel the {@code targetModel} component
 */
public record ImportSyntax(SyntaxNodeId id, SourceRange range, String modelName,
                           boolean unqualified, Optional<SymbolId> targetModel)
        implements SyntaxNode {
    /** Creates a validated instance. */
    public ImportSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(modelName, "modelName");
        targetModel = Objects.requireNonNull(targetModel, "targetModel");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.IMPORT;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
