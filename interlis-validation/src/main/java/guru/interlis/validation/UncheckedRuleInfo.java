package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import java.util.Objects;
import java.util.Optional;

/**
 * A rule application outside the plan's capabilities.
 * All references belong to the plan snapshot. The slot is present for
 * constraints on an attribute's domain. Depth zero refers to the attribute
 * value itself, and higher depths refer to collection elements.
 */
public record UncheckedRuleInfo(SymbolRef constraint, SymbolRef objectType,
                                Optional<SymbolRef> slot, int depth, Reason reason) {
    public UncheckedRuleInfo {
        Objects.requireNonNull(constraint, "constraint");
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(reason, "reason");
        if (depth < 0 || depth != 0 && slot.isEmpty()) {
            throw new IllegalArgumentException("Invalid collection depth");
        }
    }

    /** Why an application cannot be checked. */
    public enum Reason {
        OUT_OF_SCOPE_GLOBAL, UNSUPPORTED_EXPRESSION
    }
}
