package guru.interlis.model;

import java.util.Objects;

/**
 * Public snapshot-bound symbol handle.
 *
 * @param snapshot snapshot identifier
 * @param symbol the {@code symbol} component
 */
public record SymbolRef(SnapshotId snapshot, SymbolId symbol) {
    /** Creates a validated instance. */
    public SymbolRef {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(symbol, "symbol");
    }
}
