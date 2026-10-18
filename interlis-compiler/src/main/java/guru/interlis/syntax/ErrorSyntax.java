package guru.interlis.syntax;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Explicit missing or erroneous syntax node.
 *
 * @param id stable identifier
 * @param range source range
 * @param expected the {@code expected} component
 * @param diagnosticId the {@code diagnosticId} component
 */
public record ErrorSyntax(SyntaxNodeId id, SourceRange range, String expected,
                          DiagnosticId diagnosticId) implements SyntaxNode {
    /** Creates a validated instance. */
    public ErrorSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(diagnosticId, "diagnosticId");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.ERROR;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }

    @Override
    public boolean synthetic() {
        return true;
    }
}
