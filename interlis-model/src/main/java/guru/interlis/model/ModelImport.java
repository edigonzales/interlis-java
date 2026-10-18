package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * One source import declaration with its optional explicit resolver target.
 *
 * @param importedModel the {@code importedModel} component
 * @param unqualified the {@code unqualified} component
 * @param range source range
 * @param targetModel the {@code targetModel} component
 */
public record ModelImport(String importedModel, boolean unqualified,
                          SourceRange range, Optional<SymbolId> targetModel) {
    /** Creates a validated instance. */
    public ModelImport {
        Objects.requireNonNull(importedModel, "importedModel");
        Objects.requireNonNull(range, "range");
        targetModel = Objects.requireNonNull(targetModel, "targetModel");
    }
}
