package guru.interlis.validation;

import guru.interlis.model.BinaryOperator;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.SetConstraint;
import guru.interlis.transfer.TransferValue;
import guru.interlis.validation.DatasetScopeStore.Scope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static guru.interlis.validation.DatasetScopeStore.Completeness.OPEN;
import static guru.interlis.validation.DatasetRuleCoverage.Outcome.*;
import static guru.interlis.validation.DatasetRuleCoverage.ScopeKind.*;

final class DatasetAggregate {
    private final DatasetRuleContext context;
    private final ValidationSession report;
    private final DatasetRulePlan rule;
    private final DatasetScopeStore scopes;

    DatasetAggregate(DatasetRuleContext context, ValidationSession report, DatasetRulePlan rule, int ordinal) {
        this.context = context;
        this.report = report;
        this.rule = rule;
        this.scopes = new DatasetScopeStore(context.temporary, ordinal);
    }

    List<DatasetRuleCoverage> check(SetConstraint set) {
        boolean basket = set.scope() == SetConstraint.SetConstraintScope.BASKET;
        initialize(basket);
        var where = set.where().flatMap(context::program);
        var condition = context.program(set.condition());
        boolean supported = where.isPresent() == set.where().isPresent() && condition.isPresent();
        context.forObjects(occurrence -> {
            if (!applicable(occurrence)) {
                return;
            }
            long ordinal = basket ? occurrence.stored().basket() : 0;
            var entry = scopes.get(ordinal);
            if (entry.isEmpty()) {
                return;
            }
            var scope = entry.orElseThrow();
            var counts = scope.counts();
            if (!supported) {
                counts.undecided++;
            } else if (where.isEmpty()) {
                counts.candidates++;
            } else {
                switch (context.evaluate(where.orElseThrow(), occurrence)) {
                    case EvaluationResult.Known known when known.value() instanceof TransferValue.BooleanValue value -> {
                        if (value.value()) {
                            counts.candidates++;
                        }
                    }
                    case EvaluationResult.UndefinedByData ignored -> { }
                    default -> counts.undecided++;
                }
            }
            scopes.put(ordinal, scope);
        });
        var result = new DatasetScopeCounts.Coverage();
        scopes.forScopes((ordinal, scope) -> {
            context.visit();
            result.add(supported ? evaluateSet(ordinal, scope, basket, condition.orElseThrow(), where)
                    : setCoverage(scope, basket, UNSUPPORTED, 0, 0));
        });
        var reports = result.reports();
        return reports.isEmpty() ? List.of(empty(basket)) : reports;
    }

    List<DatasetRuleCoverage> check(PlausibilityConstraint plausibility) {
        initialize(false);
        var entry = scopes.get(0);
        if (entry.isEmpty()) {
            return List.of(empty(false));
        }
        var population = entry.orElseThrow();
        var counts = population.counts();
        var condition = context.program(plausibility.condition());
        var evidence = new ArrayList<GeometryEvidence>(1);
        boolean excess = plausibility.direction() == BinaryOperator.LTE;
        context.forObjects(occurrence -> {
            if (!applicable(occurrence)) {
                return;
            }
            counts.candidates++;
            if (condition.isEmpty()) {
                counts.undecided++;
                return;
            }
            switch (context.evaluate(condition.orElseThrow(), occurrence)) {
                case EvaluationResult.Known known when known.value() instanceof TransferValue.BooleanValue value -> {
                    if (value.value()) {
                        counts.satisfied++;
                    }
                    if (value.value() == excess && evidence.isEmpty()) {
                        known.geometryEvidence().ifPresent(evidence::add);
                    }
                }
                case EvaluationResult.UndefinedByData ignored -> counts.satisfied++;
                default -> counts.undecided++;
            }
        });
        return List.of(condition.isPresent() ? evaluatePercentage(population, plausibility, evidence)
                : coverage(population, DATASET, UNSUPPORTED, counts.candidates, 0, 0));
    }

    private DatasetRuleCoverage evaluateSet(long ordinal, Scope population, boolean basket,
                                            ExpressionProgram condition, java.util.Optional<ExpressionProgram> where) {
        var count = complete(population) ? OptionalLong.of(population.counts().candidates) : OptionalLong.empty();
        var result = condition.evaluate(new EvaluationFrame(context.plan, TransferValue.Undefined.INSTANCE,
                !complete(population), context::visit,
                condition.requirements().populationRequired() ? count : OptionalLong.empty(), context.cancellation())
                .withPopulations((type, explicit) -> new RuntimeObjectSet() {
                    private final List<guru.interlis.model.SymbolId> roots =
                            type.restrictions().isEmpty() ? type.base().stream().toList() : type.restrictions();

                    @Override public OptionalLong count() {
                        return explicit ? context.countPopulation(ordinal, type) : count;
                    }

                    @Override public java.util.Optional<GeometryEngine.Source> geometries(GeometrySelection paths) {
                        var incomplete = new boolean[]{!complete(population)};
                        return java.util.Optional.of(new GeometryEngine.Source() {
                            @Override public boolean complete() {
                                return !incomplete[0];
                            }

                            @Override public boolean reportLocalFindings() {
                                return true;
                            }

                            @Override public GeometryEngine.Cursor open() {
                                return context.geometryCursor(occurrence -> selects(occurrence, incomplete)
                                        ? paths.from(context, occurrence, incomplete) : List.of());
                            }
                        });
                    }

                    @Override public GeometryEngine.Check checkAreaGroups(GeometrySelection paths, GeometryEngine engine,
                                                                          GeometryEngine.Control control) {
                        var incomplete = new boolean[]{!complete(population)};
                        var check = new GeometryEngine.Check[]{GeometryEngine.Check.VALID};
                        context.forObjects(occurrence -> {
                            if (check[0] != GeometryEngine.Check.INVALID && selects(occurrence, incomplete)) {
                                check[0] = GeometrySelection.combine(check[0], paths.check(context, occurrence, engine, control));
                            }
                        });
                        return GeometrySelection.combine(check[0], incomplete[0]
                                ? GeometryEngine.Check.MISSING_PARAMETERS : GeometryEngine.Check.VALID);
                    }

                    private boolean selects(DatasetRuleContext.Occurrence occurrence, boolean[] incomplete) {
                        if (occurrence.input().descriptor().role() != InputRole.PRIMARY || !occurrence.selected()
                                || ordinal != 0 && occurrence.stored().basket() != ordinal
                                || (explicit ? !roots.isEmpty() && roots.stream().noneMatch(root ->
                                        context.target(context.plan.ref(root), occurrence)) : !applicable(occurrence))) {
                            return false;
                        }
                        if (explicit || where.isEmpty()) {
                            return true;
                        }
                        var filter = context.evaluate(where.orElseThrow(), occurrence);
                        if (filter instanceof EvaluationResult.Known known
                                && known.value() instanceof TransferValue.BooleanValue value) {
                            return value.value();
                        }
                        if (!(filter instanceof EvaluationResult.UndefinedByData)) {
                            incomplete[0] = true;
                        }
                        return false;
                    }
                }));
        if (result instanceof EvaluationResult.Known known && known.value() instanceof TransferValue.BooleanValue value) {
            if (!value.value()) {
                report.constraintViolation(rule.declaration(), ValidationCode.SET_CONSTRAINT_FALSE,
                        context.scopeLocation(rule, ordinal),
                        count.isPresent() ? List.of(Long.toString(count.getAsLong())) : List.of(),
                        known.geometryEvidence());
                return setCoverage(population, basket, VIOLATED, 1, 1);
            }
            return setCoverage(population, basket, SATISFIED, 1, 0);
        }
        return setCoverage(population, basket, result == EvaluationResult.Unsupported.INSTANCE
                ? UNSUPPORTED : UNDETERMINED, 0, 0);
    }

    private DatasetRuleCoverage evaluatePercentage(Scope population, PlausibilityConstraint definition,
                                                    List<GeometryEvidence> evidence) {
        long denominator = population.counts().candidates;
        long satisfied = population.counts().satisfied;
        if (!complete(population)) {
            return coverage(population, DATASET, UNDETERMINED, denominator, 0, 0);
        }
        if (denominator == 0) {
            return coverage(population, DATASET, NOT_APPLICABLE, 0, 0, 0);
        }
        ScalarRules.requireNumericBudget(definition.percentage());
        var numerator = BigDecimal.valueOf(satisfied).multiply(BigDecimal.valueOf(100));
        var required = definition.percentage().multiply(BigDecimal.valueOf(denominator));
        int comparison = numerator.compareTo(required);
        boolean passes = switch (definition.direction()) {
            case GTE -> comparison >= 0;
            case LTE -> comparison <= 0;
            default -> throw new IllegalStateException("Unsupported plausibility direction");
        };
        if (!passes) {
            report.constraintViolation(rule.declaration(), ValidationCode.PLAUSIBILITY_CONSTRAINT_FALSE,
                    context.scopeLocation(rule, 0), List.of(Long.toString(satisfied), Long.toString(denominator),
                            definition.direction().name(), definition.percentage().toPlainString()),
                    evidence.stream().findFirst());
        }
        return coverage(population, DATASET, passes ? SATISFIED : VIOLATED, denominator, 1, passes ? 0 : 1);
    }

    private void initialize(boolean basket) {
        context.initializeScopes(rule, basket, (ordinal, completeness) ->
                scopes.put(ordinal, new Scope(completeness, new DatasetScopeCounts())));
    }

    private boolean applicable(DatasetRuleContext.Occurrence occurrence) {
        return occurrence.input().descriptor().role() == InputRole.PRIMARY && context.candidate(rule, occurrence);
    }

    private boolean complete(Scope scope) {
        return scope.completeness() != OPEN && scope.counts().undecided == 0;
    }

    private DatasetRuleCoverage setCoverage(Scope scope, boolean basket, DatasetRuleCoverage.Outcome outcome,
                                            long evaluations, long violations) {
        long candidates = Math.addExact(scope.counts().candidates, scope.counts().undecided);
        return coverage(scope, basket ? BASKET : DATASET, outcome, candidates, evaluations, violations);
    }

    private DatasetRuleCoverage coverage(Scope scope, DatasetRuleCoverage.ScopeKind kind,
                                         DatasetRuleCoverage.Outcome outcome, long candidates,
                                         long evaluations, long violations) {
        return new DatasetRuleCoverage(rule.key(), kind, outcome, 1, candidates,
                evaluations, violations, scope.counts().undecided);
    }

    private DatasetRuleCoverage empty(boolean basket) {
        return new DatasetRuleCoverage(rule.key(), basket ? BASKET : DATASET, NOT_APPLICABLE, 0, 0, 0, 0, 0);
    }
}
