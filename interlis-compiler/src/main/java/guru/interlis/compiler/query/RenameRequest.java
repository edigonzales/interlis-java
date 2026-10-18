package guru.interlis.compiler.query;

import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.Map;
import java.util.Objects;

/**
 * Rename request pinned to expected source revisions.
 *
 * @param symbol the {@code symbol} component
 * @param newName the {@code newName} component
 * @param expectedRevisions the {@code expectedRevisions} component
 */
public record RenameRequest(SymbolRef symbol, String newName,
                            Map<SourceId, SourceRevision> expectedRevisions) {
    /** Creates a validated instance. */
    public RenameRequest {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(newName, "newName");
        if (!newName.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid INTERLIS name");
        }
        expectedRevisions = Map.copyOf(
                Objects.requireNonNull(expectedRevisions, "expectedRevisions"));
    }
}
