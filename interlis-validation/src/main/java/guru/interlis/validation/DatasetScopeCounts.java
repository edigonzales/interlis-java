package guru.interlis.validation;

import static guru.interlis.validation.DatasetRuleCoverage.Completeness.OPEN;
import static guru.interlis.validation.DatasetRuleCoverage.Outcome.*;

final class DatasetScopeCounts {
    long candidates;
    long evaluations;
    long violations;
    long undecided;
    long unsupported;
    long unsupportedValues;
    boolean selected;

    DatasetRuleCoverage coverage(RuleKey rule, DatasetRuleCoverage.ScopeKind kind,
                                 DatasetRuleCoverage.Completeness completeness) {
        var outcome = !selected ? NOT_APPLICABLE
                : violations > 0 ? VIOLATED
                : unsupportedValues > 0 ? UNSUPPORTED_VALUE_SEMANTICS
                : unsupported > 0 && evaluations == 0 ? UNSUPPORTED
                : undecided > 0 ? UNDETERMINED
                : completeness == OPEN ? NO_VIOLATION_IN_AVAILABLE_SCOPE : SATISFIED;
        return new DatasetRuleCoverage(rule, kind, completeness, outcome, 1, candidates, evaluations,
                violations, undecided);
    }
}
