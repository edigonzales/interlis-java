package guru.interlis.compiler.query;

import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Planned, non-overlapping source edits; never applied by the compiler.
 *
 * @param edits the {@code edits} component
 * @param symbols symbol references
 * @param complete the {@code complete} component
 * @param reason the {@code reason} component
 */
public record RenamePlan(List<DocumentEdit> edits, List<SymbolRef> symbols,
                         boolean complete, Optional<String> reason) {
    /** Creates a validated instance. */
    public RenamePlan {
        edits = List.copyOf(Objects.requireNonNull(edits, "edits"));
        symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));
        reason = Objects.requireNonNull(reason, "reason");
    }
}
