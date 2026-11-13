package guru.interlis.validation;

import java.util.LinkedHashMap;
import java.util.List;

import static guru.interlis.validation.DatasetScopeStore.Completeness.OPEN;
import static guru.interlis.validation.DatasetRuleCoverage.Outcome.*;

final class DatasetScopeCounts {
    long candidates;
    long evaluations;
    long satisfied;
    long violations;
    long undecided;
    long unsupported;
    long unsupportedValues;
    boolean selected;

    DatasetRuleCoverage coverage(RuleKey rule, DatasetRuleCoverage.ScopeKind kind,
                                 DatasetScopeStore.Completeness completeness) {
        var outcome = !selected ? NOT_APPLICABLE
                : violations > 0 ? VIOLATED
                : unsupportedValues > 0 ? UNSUPPORTED_VALUE_SEMANTICS
                : unsupported > 0 && evaluations == 0 ? UNSUPPORTED
                : undecided > 0 ? UNDETERMINED
                : completeness == OPEN ? NO_VIOLATION_IN_AVAILABLE_SCOPE : SATISFIED;
        return new DatasetRuleCoverage(rule, kind, outcome, 1, candidates, evaluations,
                violations, undecided);
    }
    static final class Coverage {
        private final LinkedHashMap<List<?>, DatasetRuleCoverage> groups = new LinkedHashMap<>();

        void add(DatasetRuleCoverage item) {
            groups.merge(List.of(item.scopeKind(), item.outcome()), item, (a, b) ->
                    new DatasetRuleCoverage(a.rule(), a.scopeKind(), a.outcome(),
                            Math.addExact(a.scopes(), b.scopes()), Math.addExact(a.candidates(), b.candidates()),
                            Math.addExact(a.evaluations(), b.evaluations()), Math.addExact(a.violations(), b.violations()),
                            Math.addExact(a.undecidedCandidates(), b.undecidedCandidates())));
        }

        List<DatasetRuleCoverage> reports() {
            return List.copyOf(groups.values());
        }
    }
}
