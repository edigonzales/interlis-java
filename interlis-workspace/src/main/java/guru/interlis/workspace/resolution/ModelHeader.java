package guru.interlis.workspace.resolution;

import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.syntax.ImportSyntax;
import guru.interlis.syntax.ModelSyntax;

import java.util.List;
import java.util.Objects;

/**
 * Parsed model header with exact source provenance.
 *
 * @param source source identifier
 * @param revision source revision
 * @param modelName the {@code modelName} component
 * @param modelVersion model version
 * @param languageVersion the {@code languageVersion} component
 * @param declarationOffset the {@code declarationOffset} component
 * @param imports model imports
 */
public record ModelHeader(SourceId source, SourceRevision revision, String modelName,
                          String modelVersion, LanguageVersion languageVersion,
                          int declarationOffset, List<ImportSyntax> imports) {
    /** Creates a validated instance. */
    public ModelHeader {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(languageVersion, "languageVersion");
        imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
    }

    static ModelHeader of(SourceId source, SourceRevision revision, ModelSyntax model) {
        return new ModelHeader(source, revision, model.name(), model.modelVersion(),
                model.languageVersion(), model.range().startOffset(), model.imports());
    }
}
