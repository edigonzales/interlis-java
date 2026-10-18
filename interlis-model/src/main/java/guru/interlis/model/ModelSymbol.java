package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * One model declaration.
 *
 * @param header symbol header
 * @param modelVersion model version
 * @param issuer issuer URI
 * @param imports model imports
 * @param members member identifiers
 */
public record ModelSymbol(SymbolHeader header, String modelVersion, String issuer,
                          List<ModelImport> imports, List<SymbolId> members) implements Symbol {
    /** Creates a validated instance. */
    public ModelSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(issuer, "issuer");
        imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (header.kind() != SymbolKind.MODEL) {
            throw new IllegalArgumentException("ModelSymbol needs MODEL header");
        }
    }
}
