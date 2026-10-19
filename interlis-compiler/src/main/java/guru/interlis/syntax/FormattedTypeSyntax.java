package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * FORMAT type.
 *
 * @param id stable identifier
 * @param range source range
 * @param formatName format identifier
 * @param minimum optional lexical minimum
 * @param maximum optional lexical maximum
 */
public record FormattedTypeSyntax(SyntaxNodeId id, SourceRange range, String formatName,
                                  Optional<String> minimum, Optional<String> maximum)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public FormattedTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(formatName, "formatName");
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_FORMATTED;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
