package guru.interlis.validation;

import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.SymbolId;
import guru.interlis.model.ViewSymbol;
import guru.interlis.transfer.TransferValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;

final class ViewExecutor {
    private final DatasetRuleContext dataset;

    ViewExecutor(DatasetRuleContext dataset) {
        this.dataset = dataset;
    }

    void check(ValidationSession report) {
        for (var view : dataset.plan.views().values()) {
            if (!dataset.selectsView(view.view())) {
                continue;
            }
            open(view, row -> {
                var frame = frame(row);
                for (var attribute : view.attributes()) {
                    if (!attribute.derivations().isEmpty()) {
                        DerivedValueResolver.resolve(attribute, frame);
                    }
                }
                for (var rule : view.constraints()) {
                    if (rule.definition() instanceof MandatoryConstraint mandatory) {
                        var result = dataset.program(mandatory.condition()).map(program -> program.evaluate(frame))
                                .orElse(EvaluationResult.Unsupported.INSTANCE);
                        report.recordViewConstraint(row, rule, result);
                    }
                }
            }, (row, result) -> {
                for (var declaration : view.constraints()) {
                    report.recordViewConstraint(row, declaration, result);
                }
            }, 0);
        }
    }

    private void open(ViewPlan view, Consumer<ViewRow> consumer,
                      java.util.function.BiConsumer<ViewRow, EvaluationResult> undecided, int depth) {
        dataset.visit();
        if (depth > 128) {
            throw new ValidationLimitException("View dependency depth exceeds the validation budget");
        }
        Consumer<ViewRow> selected = row -> {
            var frame = frame(row);
            for (var ancestor : view.inheritance()) {
                for (var id : ancestor.selections()) {
                    var result = dataset.program(id).map(program -> program.evaluate(frame))
                            .orElse(EvaluationResult.Unsupported.INSTANCE);
                    if (result instanceof EvaluationResult.Known known
                            && known.value() instanceof TransferValue.BooleanValue value) {
                        if (!value.value()) {
                            return;
                        }
                    } else {
                        if (!(result instanceof EvaluationResult.UndefinedByData)) {
                            undecided.accept(row, result);
                        }
                        return;
                    }
                }
            }
            consumer.accept(row);
        };
        switch (view.formation()) {
            case ViewSymbol.Projection projection -> base(view, projection.base(), selected, undecided, depth);
            case ViewSymbol.Union union -> union.bases().forEach(base -> base(view, base, selected, undecided, depth));
            default -> { }
        }
    }

    private void base(ViewPlan view, SymbolId baseId, Consumer<ViewRow> consumer,
                      java.util.function.BiConsumer<ViewRow, EvaluationResult> undecided, int depth) {
        var base = (ViewSymbol.Base) dataset.plan.symbol(baseId).orElseThrow();
        var virtual = dataset.plan.views().get(base.target());
        if (virtual != null) {
            open(virtual, parent -> {
                var values = new LinkedHashMap<>(parent.bases());
                values.put(baseId, materialize(virtual, parent));
                consumer.accept(new ViewRow(view.view().id(), values, parent.origins()));
            }, undecided, depth + 1);
            return;
        }
        dataset.forObjects(occurrence -> {
            if (occurrence.input().descriptor().role() == InputRole.PRIMARY
                    && dataset.target(dataset.plan.ref(base.target()), occurrence)) {
                var data = occurrence.object().data();
                var values = new LinkedHashMap<SymbolId, TransferValue.Structured>();
                values.put(baseId, data);
                for (var ancestor : view.inheritance()) {
                    for (var extension : ancestor.baseExtensions()) {
                        if (!extension.base().equals(baseId)) {
                            continue;
                        }
                        for (var id : extension.extensions()) {
                            var alias = (ViewSymbol.Base) dataset.plan.symbol(id).orElseThrow();
                            if (dataset.target(dataset.plan.ref(alias.target()), occurrence)) {
                                values.put(id, data);
                            }
                        }
                    }
                }
                consumer.accept(new ViewRow(view.view().id(), values, List.of(occurrence)));
            }
        });
    }

    private TransferValue.Structured materialize(ViewPlan view, ViewRow row) {
        var result = new LinkedHashMap<guru.interlis.model.SymbolRef, TransferValue>();
        var frame = frame(row);
        for (var attribute : view.attributes()) {
            var computed = DerivedValueResolver.resolve(attribute, frame);
            if (computed instanceof EvaluationResult.Known known) {
                result.put(dataset.plan.ref(attribute.id()), known.value());
            } else if (computed instanceof EvaluationResult.KnownNumber number) {
                number.value().decimal().ifPresent(value -> result.put(dataset.plan.ref(attribute.id()),
                        new TransferValue.NumberValue(value.toString(), value)));
            }
        }
        return new TransferValue.Structured(dataset.plan.ref(view.view().id()), result);
    }

    private EvaluationFrame frame(ViewRow row) {
        var origin = row.origins().getFirst();
        var data = new TransferValue.Structured(dataset.plan.ref(row.view()), Map.of());
        return new EvaluationFrame(dataset.plan, data, origin.partial(), dataset::visit, OptionalLong.empty(),
                dataset.cancellation()).withDataset(new DatasetPathContext(dataset, origin)).withRow(row);
    }
}
