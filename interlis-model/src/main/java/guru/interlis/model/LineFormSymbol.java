package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/** Declared curve form. A missing target occurs only in an erroneous analysis, never in a compiled schema. */
public record LineFormSymbol(SymbolHeader header, Optional<SymbolId> segmentStructure) implements Symbol {
    public LineFormSymbol {
        Objects.requireNonNull(header); Objects.requireNonNull(segmentStructure);
        if (header.kind() != SymbolKind.LINE_FORM) throw new IllegalArgumentException("LINE_FORM header required");
    }
}
