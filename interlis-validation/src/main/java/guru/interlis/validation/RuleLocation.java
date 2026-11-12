package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/** Snapshot-bound declaration and its model source range if known. */
public record RuleLocation(SymbolRef declaration, Optional<SourceRange> range) {
    public RuleLocation {
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(range, "range");
    }
}
