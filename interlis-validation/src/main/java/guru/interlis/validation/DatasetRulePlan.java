package guru.interlis.validation;

import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.SymbolRef;

import java.util.List;

/** One declaration and its concrete candidate types in transfer plan order. */
record DatasetRulePlan(RuleKey key, ConstraintSymbol declaration, List<SymbolRef> candidateTypes) {
    DatasetRulePlan {
        candidateTypes = List.copyOf(candidateTypes);
    }
}
