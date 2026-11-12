package guru.interlis.validation;

import guru.interlis.model.SymbolRef;

import java.util.Objects;
import java.util.Optional;

/** Declaration, bound application root, and optional effective slot of one rule. */
public record RuleKey(SymbolRef declaration, SymbolRef applicationRoot, Optional<SymbolRef> slot) {
    public RuleKey {
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(applicationRoot, "applicationRoot");
        Objects.requireNonNull(slot, "slot");
        if (!declaration.snapshot().equals(applicationRoot.snapshot())
                || slot.filter(ref -> !declaration.snapshot().equals(ref.snapshot())).isPresent()) {
            throw new IllegalArgumentException("Rule references must belong to the same snapshot");
        }
    }
}
