package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Enumeration tree syntax with ordered/circular flags.
 *
 * @param id stable identifier
 * @param range source range
 * @param values the {@code values} component
 * @param ordered the {@code ordered} component
 * @param circular the {@code circular} component
 */
public record EnumerationTypeSyntax(SyntaxNodeId id, SourceRange range,
                                    List<EnumerationValueSyntax> values,
                                    boolean ordered, boolean circular) implements TypeSyntax {
    /** Creates a validated instance. */
    public EnumerationTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_ENUMERATION;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }

    /**
     * Immutable value.
     *
     * @param path name path
     * @param range source range
     */
    public record EnumerationValueSyntax(String path, SourceRange range) {
        /** Creates a validated instance. */
        public EnumerationValueSyntax {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(range, "range");
        }
    }
}
