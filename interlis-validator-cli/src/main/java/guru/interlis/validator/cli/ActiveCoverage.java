package guru.interlis.validator.cli;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;
import guru.interlis.validation.DatasetRuleCoverage;
import guru.interlis.validation.DatasetScope;
import guru.interlis.validation.InputRole;
import guru.interlis.validation.ValidationPlan;
import guru.interlis.validation.UncheckedRuleInfo;
import guru.interlis.validation.ValidationCoverage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Selects gaps that apply to values in this input, using the validator's rule report. */
final class ActiveCoverage {
    private final ValidationPlan validation;
    private final List<UncheckedRuleInfo> planned;
    private final Set<UncheckedRuleInfo> applicable = new LinkedHashSet<>();

    ActiveCoverage(ValidationPlan validation) {
        this.validation = validation;
        this.planned = validation.uncheckedRules();
    }

    TransferReader observe(TransferReader reader, TransferPlan plan, InputRole role,
                           boolean explicitPrimary) {
        boolean requirePrimary = role == InputRole.PRIMARY && explicitPrimary;
        return new TransferReader(plan) {
            private boolean selected;

            @Override protected Optional<TransferEvent> readEvent(CancellationToken token) {
                var event = reader.readNext(token);
                if (requirePrimary && event.orElse(null) instanceof TransferEvent.StartTransfer transfer
                        && transfer.models().stream().noneMatch(model -> validation.selectsPrimaryModel(model.model()))) {
                    throw new PrimaryModelMismatch("No declared transfer model matches the requested primary models");
                }
                if (event.orElse(null) instanceof TransferEvent.StartBasket basket) {
                    selected = role == InputRole.PRIMARY && validation.selectsPrimaryBasket(basket);
                    if (requirePrimary && !selected) {
                        throw new PrimaryModelMismatch("Basket is outside the requested primary models");
                    }
                } else if (selected && !planned.isEmpty() && event.orElse(null) instanceof TransferEvent.ObjectEvent object) {
                    collect(object.object().data(), token);
                }
                return event;
            }

            @Override public Map<String, QualifiedName> stableIdentityNamespaces() {
                return reader.stableIdentityNamespaces();
            }

            @Override public Optional<XtfEncoding> xtfEncoding() {
                return reader.xtfEncoding();
            }

            @Override protected void release() { }
        };
    }

    static final class PrimaryModelMismatch extends IllegalArgumentException {
        PrimaryModelMismatch(String message) {
            super(message);
        }
    }

    private void collect(TransferValue value, CancellationToken token) {
        token.throwIfCancellationRequested();
        switch (value) {
            case TransferValue.Structured structure -> {
                for (var rule : planned) {
                    if (rule.objectType().equals(structure.type()) && rule.slot().map(slot ->
                            hasValue(structure.values().get(slot), rule.depth())).orElse(true)) {
                        applicable.add(rule);
                    }
                }
                structure.values().values().forEach(child -> collect(child, token));
            }
            case TransferValue.Repeated repeated -> repeated.values().forEach(child -> collect(child, token));
            case TransferGeometry.Polyline line -> line.lineAttributes().ifPresent(child -> collect(child, token));
            case TransferGeometry.MultiPolyline multi -> multi.lines().forEach(child -> collect(child, token));
            case TransferGeometry.Surface surface -> {
                surface.shell().lines().forEach(child -> collect(child, token));
                surface.holes().forEach(ring -> ring.lines().forEach(child -> collect(child, token)));
            }
            case TransferGeometry.MultiSurface multi -> multi.surfaces().forEach(child -> collect(child, token));
            default -> { }
        }
    }

    private static boolean hasValue(TransferValue value, int depth) {
        if (depth == 0) return value != null && value != TransferValue.Undefined.INSTANCE;
        return value instanceof TransferValue.Repeated repeated
                && repeated.values().stream().anyMatch(child -> hasValue(child, depth - 1));
    }

    List<String> gaps(ValidationCoverage coverage, DatasetScope scope) {
        var gaps = new ArrayList<String>();
        applicable.forEach(rule -> gaps.add(rule.reason() + " " + rule.constraint().symbol().value()));
        for (var rule : coverage.datasetRules()) {
            if (rule.scopes() == 0) {
                continue;
            }
            if (rule.outcome() == DatasetRuleCoverage.Outcome.UNSUPPORTED) {
                var key = rule.rule();
                var plannedGap = planned.stream().filter(gap ->
                        gap.reason() == UncheckedRuleInfo.Reason.OUT_OF_SCOPE_GLOBAL
                                && gap.constraint().equals(key.declaration())
                                && gap.objectType().equals(key.applicationRoot())
                                && gap.slot().equals(key.slot())).findFirst();
                if (plannedGap.isPresent()) {
                    var gap = plannedGap.orElseThrow();
                    if (!applicable.contains(gap)) {
                        gaps.add(gap.reason() + " " + gap.constraint().symbol().value());
                    }
                    continue;
                }
            }
            if (switch (rule.outcome()) {
                case UNSUPPORTED, UNSUPPORTED_VALUE_SEMANTICS, UNDETERMINED -> true;
                case NO_VIOLATION_IN_AVAILABLE_SCOPE -> scope == DatasetScope.SUPPLIED_DATASET;
                default -> false;
            }) gaps.add(rule.outcome() + " " + rule.rule().declaration().symbol().value());
        }
        for (var constraint : coverage.constraints()) {
            switch (constraint.status()) {
                case CHECKED, UNDEFINED_BY_DATA -> { }
                default -> gaps.add(constraint.status() + " " + constraint.constraint().symbol().value());
            }
        }
        coverage.unsupportedValues().forEach(rule -> gaps.add("Unsupported value in " + rule.family()));
        coverage.missingRuntimeContext().forEach(rule -> gaps.add("Missing runtime context for " + rule.family()));
        coverage.incompleteContext().forEach(rule -> gaps.add("Incomplete context for " + rule.family()));
        return gaps;
    }
}
