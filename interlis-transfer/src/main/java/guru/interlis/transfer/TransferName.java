package guru.interlis.transfer;

import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;

import java.util.Objects;

/**
 * Names and canonical identity of a declaration within one schema snapshot.
 *
 * @param declared qualified name of the projected declaration
 * @param transfer qualified model name chosen for the neutral transfer vocabulary
 * @param canonical original declaration identity, following checked translations
 */
public record TransferName(QualifiedName declared, QualifiedName transfer, SymbolRef canonical) {
    public TransferName {
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(transfer, "transfer");
        Objects.requireNonNull(canonical, "canonical");
    }
}
