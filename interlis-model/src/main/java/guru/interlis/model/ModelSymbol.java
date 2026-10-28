package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One model declaration.
 *
 * @param header symbol header
 * @param modelKind model kind
 * @param contracted original CONTRACTED marker (semantically inactive in 2.4)
 * @param modelVersion model version
 * @param issuer issuer URI
 * @param imports model imports
 * @param members member identifiers
 * @param translation optional translation declaration
 * @param options explicit immutable options and their origins
 * @param transferNamespace effective XML transfer namespace, using the canonical original of translations
 */
public record ModelSymbol(SymbolHeader header, ModelKind modelKind, boolean contracted,
                          String modelVersion, String issuer, List<ModelImport> imports,
                          List<SymbolId> members, Optional<ModelTranslation> translation, ModelOptions options, String transferNamespace)
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
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(transferNamespace, "transferNamespace");
        if (header.kind() != SymbolKind.MODEL) {
            throw new IllegalArgumentException("ModelSymbol needs MODEL header");
        }
    }
}
