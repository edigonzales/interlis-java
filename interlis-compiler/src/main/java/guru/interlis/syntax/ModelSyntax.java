package guru.interlis.syntax;

import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One model declaration inside a source document.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param modelVersion model version
 * @param issuer issuer URI
 * @param languageVersion the {@code languageVersion} component
 * @param endName optional name after END
 * @param imports model imports
 * @param members member identifiers
 */
public record ModelSyntax(SyntaxNodeId id, SourceRange range, String name,
                          String modelVersion, String issuer, LanguageVersion languageVersion,
                          java.util.Optional<String> endName,
                          List<ImportSyntax> imports, List<SyntaxNode> members)
        implements SyntaxNode {
    /** Creates a validated instance. */
    public ModelSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(languageVersion, "languageVersion");
        endName = Objects.requireNonNull(endName, "endName");
        imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.MODEL;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>(imports);
        result.addAll(members);
        return result;
    }
}
