package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One model declaration.
 *
 * @param header symbol header
 * @param kind model kind
 * @param contracted whether the model uses CONTRACTED transfer semantics
 * @param modelVersion model version
 * @param issuer issuer URI
 * @param imports model imports
 * @param members member identifiers
 * @param translation optional translation declaration
 */
public record ModelSymbol(SymbolHeader header, ModelKind modelKind, boolean contracted,
                          String modelVersion, String issuer, List<ModelImport> imports,
                          List<SymbolId> members, Optional<ModelTranslation> translation)
        implements Symbol {
    /** Creates a validated instance. */
    public ModelSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(modelKind, "modelKind");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(issuer, "issuer");
        imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        translation = Objects.requireNonNull(translation, "translation");
        if (header.kind() != SymbolKind.MODEL) {
            throw new IllegalArgumentException("ModelSymbol needs MODEL header");
        }
    }
}
