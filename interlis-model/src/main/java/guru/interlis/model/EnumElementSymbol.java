package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * One enumeration tree node.
 *
 * @param header symbol header
 * @param path name path
 * @param ordinal ordinal
 */
public record EnumElementSymbol(SymbolHeader header, List<String> path, int ordinal)
        implements Symbol {
    /** Creates a validated instance. */
    public EnumElementSymbol {
        Objects.requireNonNull(header, "header");
        path = List.copyOf(Objects.requireNonNull(path, "path"));
        if (path.isEmpty() || ordinal < 0) {
            throw new IllegalArgumentException("enum element needs path and non-negative ordinal");
        }
        if (header.kind() != SymbolKind.ENUM_ELEMENT) {
            throw new IllegalArgumentException("EnumElementSymbol needs ENUM_ELEMENT header");
        }
    }
}
