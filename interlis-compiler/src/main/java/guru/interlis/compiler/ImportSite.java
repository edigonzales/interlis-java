package guru.interlis.compiler;

import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * Original import position and the names involved.
 *
 * @param nameRange the {@code nameRange} component
 * @param importingModel the {@code importingModel} component
 * @param importedModel the {@code importedModel} component
 */
public record ImportSite(SourceRange nameRange, String importingModel, String importedModel) {
    /** Creates a validated instance. */
    public ImportSite {
        Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(importingModel, "importingModel");
        Objects.requireNonNull(importedModel, "importedModel");
    }
}
