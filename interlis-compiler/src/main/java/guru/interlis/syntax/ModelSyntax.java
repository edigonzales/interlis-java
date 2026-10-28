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
 * @param modelKind model kind
 * @param contracted whether the CONTRACTED header flag is set
 * @param modelVersion model version
 * @param issuer issuer URI
 * @param languageVersion the {@code languageVersion} component
 * @param translationSource optional canonical source model
 * @param translationVersion optional canonical source version
 * @param endName optional name after END
 * @param imports model imports
 * @param members member identifiers
 * @param options explicit source options; absence remains distinguishable from defaults
 */
public record ModelSyntax(SyntaxNodeId id, SourceRange range, String name,
                          guru.interlis.model.ModelKind modelKind, boolean contracted,
                          String modelVersion, String issuer, LanguageVersion languageVersion,
                          java.util.Optional<NameSyntax> translationSource,
                          java.util.Optional<String> translationVersion,
                          java.util.Optional<String> endName,
                          List<ImportSyntax> imports, List<SyntaxNode> members, guru.interlis.model.ModelOptions options)
        implements SyntaxNode {
    /** Creates a validated instance. */
    public ModelSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(languageVersion, "languageVersion");
        translationSource = Objects.requireNonNull(translationSource, "translationSource");
        translationVersion = Objects.requireNonNull(translationVersion, "translationVersion");
        endName = Objects.requireNonNull(endName, "endName");
        imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
        Objects.requireNonNull(options, "options");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
    }

    /** The original documentary language; no ISO language restriction is inferred. */
    public java.util.Optional<String> language() { return options.language().map(guru.interlis.model.ModelOptions.TextValue::value); }

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
