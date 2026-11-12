package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferIdentity.BasketId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Selects one snapshot-bound type hierarchy across explicit inputs.
 * An optional basket ID is scoped to the sole selected input.
 */
public record ExtentSelector(SymbolRef root, Set<InputId> inputs, Optional<BasketId> basket) {
    public ExtentSelector {
        Objects.requireNonNull(root, "root");
        inputs = Set.copyOf(inputs);
        Objects.requireNonNull(basket, "basket");
        if (inputs.isEmpty() || basket.isPresent() && inputs.size() != 1) {
            throw new IllegalArgumentException("Select inputs explicitly and qualify a basket with exactly one input");
        }
    }
}
