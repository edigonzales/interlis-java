package guru.interlis.projection;

import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Objects;

/**
 * Explicit nonempty selection of semantic elements.
 *
 * @param symbols symbol references
 */
public record DiagramSelection(List<SymbolRef> symbols) {
    /** Creates a validated instance. */
    public DiagramSelection {
        symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("diagram selection must not be empty");
        }
    }
}
