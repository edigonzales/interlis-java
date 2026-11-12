package guru.interlis.validation;

import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Objects;

/** Immutable rule counts and declared coverage gaps for one completed run. */
public record ValidationCoverage(List<RuleCount> checked, List<UncheckedRuleInfo> notChecked,
                                 List<RuleCount> incompleteContext, List<RuleCount> missingRuntimeContext,
                                 List<RuleCount> unsupportedValues,
                                 List<RuleFamily> unavailableFamilies) {
    public ValidationCoverage {
        checked = List.copyOf(checked);
        notChecked = List.copyOf(notChecked);
        incompleteContext = List.copyOf(incompleteContext);
        missingRuntimeContext = List.copyOf(missingRuntimeContext);
        unsupportedValues = List.copyOf(unsupportedValues);
        unavailableFamilies = List.copyOf(unavailableFamilies);
    }

    /** A model rule family outside this version's executable scope. */
    public enum RuleFamily {
        DEFINED_VALUE, CARDINALITY, VALUE_FAMILY, VALUE_RANGE, STRUCTURE, IDENTITY, GEOMETRY_VALUES,
        LOCAL_CONSTRAINTS, LOCAL_UNIQUENESS, REFERENTIAL_INTEGRITY, TOPOLOGY
    }

    /** Count for one effective slot, owned by the plan snapshot. */
    public record RuleCount(SymbolRef objectType, SymbolRef slot, RuleFamily family, long occurrences) {
        public RuleCount {
            Objects.requireNonNull(objectType, "objectType");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(family, "family");
            if (occurrences < 1) {
                throw new IllegalArgumentException("A rule count must be positive");
            }
        }
    }
}
