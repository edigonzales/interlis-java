package guru.interlis.syntax;

import guru.interlis.model.TextKind;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * TEXT/MTEXT/NAME/URI with optional length.
 *
 * @param id stable identifier
 * @param range source range
 * @param textKind the {@code textKind} component
 * @param maxLength the {@code maxLength} component
 */
public record TextTypeSyntax(SyntaxNodeId id, SourceRange range, TextKind textKind,
                             OptionalInt maxLength) implements TypeSyntax {
    /** Creates a validated instance. */
    public TextTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(textKind, "textKind");
        Objects.requireNonNull(maxLength, "maxLength");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_TEXT;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
