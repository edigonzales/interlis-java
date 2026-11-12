package guru.interlis.validation;

import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable rule counts and declared coverage gaps for one completed run. */
public record ValidationCoverage(List<RuleCount> checked, List<UncheckedRuleInfo> notChecked,
                                 List<RuleCount> incompleteContext, List<RuleCount> missingRuntimeContext,
                                 List<RuleCount> unsupportedValues,
                                 List<RuleFamily> unavailableFamilies, List<ConstraintCount> constraints,
                                 List<DatasetRuleCoverage> datasetRules) {
    public ValidationCoverage {
        checked = List.copyOf(checked);
        notChecked = List.copyOf(notChecked);
        incompleteContext = List.copyOf(incompleteContext);
        missingRuntimeContext = List.copyOf(missingRuntimeContext);
        unsupportedValues = List.copyOf(unsupportedValues);
        unavailableFamilies = List.copyOf(unavailableFamilies);
        constraints = List.copyOf(constraints);
        datasetRules = List.copyOf(datasetRules);
    }

    /** Creates coverage for a local validation run with no dataset executions. */
    public ValidationCoverage(List<RuleCount> checked, List<UncheckedRuleInfo> notChecked,
                              List<RuleCount> incompleteContext, List<RuleCount> missingRuntimeContext,
                              List<RuleCount> unsupportedValues, List<RuleFamily> unavailableFamilies,
                              List<ConstraintCount> constraints) {
        this(checked, notChecked, incompleteContext, missingRuntimeContext, unsupportedValues,
                unavailableFamilies, constraints, List.of());
    }

    /** A model rule family outside this version's executable scope. */
    public enum RuleFamily {
        DEFINED_VALUE, CARDINALITY, VALUE_FAMILY, VALUE_RANGE, STRUCTURE, IDENTITY, GEOMETRY_VALUES,
        LOCAL_CONSTRAINTS, LOCAL_UNIQUENESS, REFERENTIAL_INTEGRITY, TOPOLOGY
    }

    /** Counts one application and outcome of a constraint, without retaining data objects. */
    public record ConstraintCount(SymbolRef objectType, Optional<SymbolRef> slot, SymbolRef constraint,
                                  ConstraintStatus status, long occurrences) {
        public ConstraintCount {
            Objects.requireNonNull(objectType, "objectType");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(constraint, "constraint");
            Objects.requireNonNull(status, "status");
            if (occurrences < 1) {
                throw new IllegalArgumentException("A constraint count must be positive");
            }
        }
    }

    /** Distinguishes complete evaluation, normative undefined, and skipped applications. */
    public enum ConstraintStatus {
        CHECKED, UNDEFINED_BY_DATA, MISSING_RUNTIME_CONTEXT, UNSUPPORTED_VALUE, INCOMPLETE_CONTEXT,
        UNSUPPORTED_EXPRESSION
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
