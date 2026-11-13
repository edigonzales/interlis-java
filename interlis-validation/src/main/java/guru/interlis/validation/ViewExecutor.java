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
    private int groupRun;
    private int uniqueRun;

    ViewExecutor(DatasetRuleContext dataset) {
        this.dataset = dataset;
    }

    void check(ValidationSession report) {
        for (var view : dataset.plan.views().values()) {
            if (!dataset.selectsView(view.view())) {
                continue;
            }
            if (view.formation() instanceof ViewSymbol.Inspection inspection && inspection.area()) {
                report.recordUnavailableView(view);
                continue;
            }
            var uniqueOrdinals = new LinkedHashMap<SymbolId, Integer>();
            for (var rule : view.constraints()) {
                if (rule.definition() instanceof guru.interlis.model.UniquenessConstraint unique && !unique.keys().isEmpty()) {
                    uniqueOrdinals.put(rule.id(), ++uniqueRun);
                }
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
                    } else if (rule.definition() instanceof guru.interlis.model.UniquenessConstraint unique
                            && uniqueOrdinals.containsKey(rule.id())) {
                        checkUnique(row, frame, rule, unique, uniqueOrdinals.get(rule.id()), report);
                    } else {
                        report.recordViewConstraint(row, rule, EvaluationResult.Unsupported.INSTANCE);
                    }
                }
            }, (row, result) -> {
                for (var declaration : view.constraints()) {
                    report.recordViewConstraint(row, declaration, result);
                }
            }, 0);
            for (var rule : view.constraints()) {
                var ordinal = uniqueOrdinals.get(rule.id());
                if (ordinal != null) {
                    reportUniqueGroups(rule, ordinal, report);
                }
            }
        }
    }

    private void checkUnique(ViewRow row, EvaluationFrame frame, guru.interlis.model.ConstraintSymbol declaration,
                             guru.interlis.model.UniquenessConstraint unique, int ordinal, ValidationSession report) {
        var status = ValidationCoverage.ConstraintStatus.CHECKED;
        if (unique.where().isPresent()) {
            var condition = dataset.program(unique.where().orElseThrow()).map(program -> program.evaluate(frame))
                    .orElse(EvaluationResult.Unsupported.INSTANCE);
            if (!isTrue(condition)) {
                report.recordViewUnique(row, declaration, unknown(condition) ? gap(condition) : status);
                return;
            }
        }
        var origin = row.origins().getFirst();
        var semantics = dataset.semantics(origin);
        var keys = new java.util.ArrayList<CanonicalKey>();
        for (var expression : unique.keys()) {
            var program = dataset.program(expression);
            if (program.isEmpty()) {
                report.recordViewUnique(row, declaration, ValidationCoverage.ConstraintStatus.UNSUPPORTED_EXPRESSION);
                return;
            }
            var value = program.orElseThrow().evaluate(frame);
            if (value instanceof EvaluationResult.UndefinedByData) {
                report.recordViewUnique(row, declaration, status);
                return;
            }
            var type = dataset.plan.effectiveType(program.orElseThrow().expression().typeId()).orElseThrow();
            if (!(semantics.key(value, type) instanceof ValueSemantics.Known key)) {
                report.recordViewUnique(row, declaration, gap(value));
                return;
            }
            keys.add(key.key());
        }
        long scope = unique.basket() ? origin.stored().basket() : 0;
        var key = dataset.indexKey((byte) 9, ordinal, scope, semantics.tuple(keys));
        long handle = origin.handle().ordinal();
        var group = dataset.temporary.get(key).map(java.nio.ByteBuffer::wrap).map(DatasetUnique.Group::read)
                .orElseGet(() -> new DatasetUnique.Group(0, handle, 0, handle));
        long witness = group.second();
        if (group.size() > 0 && (witness == 0 || witness == group.first())) {
            long subject = group.first();
            witness = row.origins().stream().mapToLong(candidate -> candidate.handle().ordinal())
                    .filter(candidate -> candidate != subject).findFirst().orElse(handle);
        }
        var updated = new DatasetUnique.Group(Math.incrementExact(group.size()), group.first(),
                witness, group.selected());
        dataset.temporary.put(key, updated.bytes());
        report.recordViewUnique(row, declaration, status);
    }

    private void reportUniqueGroups(guru.interlis.model.ConstraintSymbol declaration, int ordinal,
                                    ValidationSession report) {
        var lower = java.nio.ByteBuffer.allocate(1 + Integer.BYTES).put((byte) 9).putInt(ordinal).array();
        var upper = java.nio.ByteBuffer.allocate(1 + Integer.BYTES).put((byte) 9)
                .putInt(Math.incrementExact(ordinal)).array();
        try (var cursor = dataset.temporary.scan(lower, java.util.Optional.of(upper))) {
            for (var next = cursor.next(); next.isPresent(); next = cursor.next()) {
                dataset.visit();
                var group = DatasetUnique.Group.read(java.nio.ByteBuffer.wrap(next.orElseThrow().value()));
                if (group.size() < 2) {
                    continue;
                }
                var first = dataset.occurrence(new ObjectHandle(group.first()));
                var witness = dataset.occurrence(new ObjectHandle(group.second()));
                var arguments = new java.util.ArrayList<>(witness.witness());
                arguments.add(Long.toString(group.size()));
                report.datasetViolation(declaration, ValidationCode.DATASET_UNIQUE_DUPLICATE,
                        first.location(List.of()), arguments);
            }
        }
    }

    private static ValidationCoverage.ConstraintStatus gap(EvaluationResult result) {
        return switch (result) {
            case EvaluationResult.MissingContext _ -> ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT;
            case EvaluationResult.BlockedByViolation _ -> ValidationCoverage.ConstraintStatus.INCOMPLETE_CONTEXT;
            default -> ValidationCoverage.ConstraintStatus.UNSUPPORTED_VALUE;
        };
    }

    private void open(ViewPlan view, Consumer<ViewRow> consumer,
                      java.util.function.BiConsumer<ViewRow, EvaluationResult> undecided, int depth) {
        dataset.visit();
        if (depth > 128) {
            throw new ValidationLimitException("View dependency depth exceeds the validation budget");
        }
        Consumer<ViewRow> selected = row -> {
            var result = selection(view, row);
            if (isTrue(result)) {
                consumer.accept(row);
            } else if (unknown(result)) {
                undecided.accept(row, result);
            }
        };
        switch (view.formation()) {
            case ViewSymbol.Projection projection -> base(view, projection.base(), selected, undecided, depth);
            case ViewSymbol.Union union -> union.bases().forEach(base -> base(view, base, selected, undecided, depth));
            case ViewSymbol.Join join -> join(view, join, 0, new ViewRow(view.view().id(), Map.of(), List.of()),
                    row -> {
                        consumer.accept(row);
                        return true;
                    }, undecided, depth, false);
            case ViewSymbol.Aggregation aggregate -> aggregate(view, aggregate, selected, undecided, depth);
            case ViewSymbol.Inspection inspection -> {
                if (!inspection.area()) {
                    inspect(view, inspection, selected, undecided, depth);
                }
            }
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

    private EvaluationResult selection(ViewPlan view, ViewRow row) {
        var frame = frame(row);
        for (var ancestor : view.inheritance()) {
            for (var id : ancestor.selections()) {
                var result = dataset.program(id).map(program -> program.evaluate(frame))
                        .orElse(EvaluationResult.Unsupported.INSTANCE);
                if (!isTrue(result)) {
                    return result;
                }
            }
        }
        return ExpressionEvaluator.bool(true);
    }

    private static boolean isTrue(EvaluationResult result) {
        return result instanceof EvaluationResult.Known known
                && known.value() instanceof TransferValue.BooleanValue value && value.value();
    }

    private static boolean unknown(EvaluationResult result) {
        return !(result instanceof EvaluationResult.Known || result instanceof EvaluationResult.UndefinedByData);
    }

    private JoinOutcome join(ViewPlan view, ViewSymbol.Join join, int position, ViewRow prefix,
                             java.util.function.Predicate<ViewRow> consumer,
                             java.util.function.BiConsumer<ViewRow, EvaluationResult> undecided,
                             int depth, boolean nullPartner) {
        dataset.visit();
        if (position > 128) {
            throw new ValidationLimitException("Join arity exceeds the validation depth budget");
        }
        if (position == join.bases().size()) {
            var result = selection(view, prefix);
            if (isTrue(result) || nullPartner && result instanceof EvaluationResult.UndefinedByData undefined
                    && undefined.nullPartner()) {
                return consumer.test(prefix) ? JoinOutcome.MATCH : JoinOutcome.NO_MATCH;
            }
            if (unknown(result)) {
                undecided.accept(prefix, result);
                return JoinOutcome.UNKNOWN;
            }
            return JoinOutcome.NO_MATCH;
        }
        var base = join.bases().get(position);
        var binding = (ViewSymbol.Base) dataset.plan.symbol(base).orElseThrow();
        var outcome = new JoinOutcome[] {JoinOutcome.NO_MATCH};
        base(view, base, next -> {
            var values = new LinkedHashMap<>(prefix.bases());
            values.putAll(next.bases());
            var origins = new java.util.ArrayList<>(prefix.origins());
            origins.addAll(next.origins());
            var evaluated = join(view, join, position + 1, new ViewRow(view.view().id(), values, origins, List.of(), OptionalLong.empty(),
                            prefix.nullBases()), consumer, undecided, depth, nullPartner);
            outcome[0] = outcome[0].merge(evaluated);
        }, (row, result) -> {
            outcome[0] = outcome[0].merge(JoinOutcome.UNKNOWN);
            undecided.accept(prefix.origins().isEmpty() ? row : prefix, result);
        }, depth);
        if (outcome[0] == JoinOutcome.NO_MATCH && binding.orNull() && !prefix.origins().isEmpty()) {
            if (dataset.populationClosed(dataset.plan.ref(binding.target()))) {
                var nullBases = new java.util.HashSet<>(prefix.nullBases());
                nullBases.add(base);
                var nullable = new ViewRow(prefix.view(), prefix.bases(), prefix.origins(),
                        prefix.parents(), prefix.groupSize(), nullBases);
                return join(view, join, position + 1, nullable, consumer, undecided, depth, true);
            }
            undecided.accept(prefix, EvaluationResult.MissingContext.POPULATION);
            return JoinOutcome.UNKNOWN;
        }
        return outcome[0];
    }

    private enum JoinOutcome {
        NO_MATCH, UNKNOWN, MATCH;

        JoinOutcome merge(JoinOutcome other) {
            return ordinal() >= other.ordinal() ? this : other;
        }
    }

    private void aggregate(ViewPlan view, ViewSymbol.Aggregation aggregate, Consumer<ViewRow> consumer,
                           java.util.function.BiConsumer<ViewRow, EvaluationResult> undecided, int depth) {
        int run = ++groupRun;
        var prefix = java.nio.ByteBuffer.allocate(1 + Integer.BYTES).put((byte) 8).putInt(run).array();
        var upper = java.nio.ByteBuffer.allocate(1 + Integer.BYTES).put((byte) 8).putInt(run + 1).array();
        base(view, aggregate.base(), row -> {
            var origin = row.origins().getFirst();
            var semantics = dataset.semantics(origin);
            var keys = new java.util.ArrayList<CanonicalKey>();
            for (var id : aggregate.equal()) {
                var program = dataset.program(id);
                if (program.isEmpty()) {
                    undecided.accept(row, EvaluationResult.Unsupported.INSTANCE);
                    return;
                }
                var expression = program.orElseThrow();
                var result = expression.evaluate(frame(row));
                if (result instanceof EvaluationResult.UndefinedByData) {
                    keys.add(new CanonicalKey(new byte[0]));
                    continue;
                }
                var type = dataset.plan.effectiveType(expression.expression().typeId()).orElseThrow();
                if (!(semantics.key(result, type) instanceof ValueSemantics.Known key)) {
                    undecided.accept(row, result instanceof EvaluationResult.MissingContext
                            ? result : EvaluationResult.Unsupported.INSTANCE);
                    return;
                }
                keys.add(key.key());
            }
            var encoded = semantics.tuple(keys).bytes();
            var key = java.nio.ByteBuffer.allocate(prefix.length + encoded.length).put(prefix).put(encoded).array();
            var old = dataset.keys.get(key).map(java.nio.ByteBuffer::wrap);
            long count = old.map(java.nio.ByteBuffer::getLong).orElse(0L);
            long representative = old.map(java.nio.ByteBuffer::getLong).orElse(origin.handle().ordinal());
            dataset.keys.put(key, java.nio.ByteBuffer.allocate(2 * Long.BYTES).putLong(Math.addExact(count, 1))
                    .putLong(representative).array());
        }, undecided, depth);
        try (var cursor = dataset.keys.scan(prefix, java.util.Optional.of(upper))) {
            for (var entry = cursor.next(); entry.isPresent(); entry = cursor.next()) {
                dataset.visit();
                var data = java.nio.ByteBuffer.wrap(entry.orElseThrow().value());
                var count = data.getLong();
                var representative = dataset.occurrence(new ObjectHandle(data.getLong()));
                consumer.accept(new ViewRow(view.view().id(), Map.of(aggregate.base(), representative.object().data()),
                        List.of(representative), List.of(), OptionalLong.of(count)));
            }
        }
    }

    private void inspect(ViewPlan view, ViewSymbol.Inspection inspection, Consumer<ViewRow> consumer,
                         java.util.function.BiConsumer<ViewRow, EvaluationResult> undecided, int depth) {
        var path = dataset.plan.boundPath(inspection.path()).orElseThrow();
        base(view, inspection.base(), row -> {
            var origin = row.origins().getFirst();
            inspectPath(row, inspection.base(), row.bases().get(inspection.base()), path.path().steps(),
                    0, List.of(), frame(row).withCurrent(origin.object().data()), consumer, undecided);
        }, undecided, depth);
    }

    private void inspectPath(ViewRow row, SymbolId base, TransferValue current, List<guru.interlis.model.PathStep> steps,
                             int position, List<TransferValue.Structured> parents, EvaluationFrame frame,
                             Consumer<ViewRow> consumer,
                             java.util.function.BiConsumer<ViewRow, EvaluationResult> undecided) {
        dataset.visit();
        if (position > 128 || parents.size() > 128) {
            throw new ValidationLimitException("Inspection path exceeds the validation depth budget");
        }
        if (current instanceof TransferValue.Repeated repeated) {
            for (var member : repeated.values()) {
                inspectPath(row, base, member, steps, position, parents, frame, consumer, undecided);
            }
        } else if (current instanceof TransferValue.Structured structure) {
            if (position == steps.size()) {
                consumer.accept(new ViewRow(row.view(), Map.of(base, structure), row.origins(), parents, OptionalLong.empty()));
            } else if (steps.get(position) instanceof guru.interlis.model.PathStep.AttributeStep attribute) {
                var result = ExpressionEvaluator.attribute(frame.withCurrent(structure), structure, attribute.target());
                if (result instanceof EvaluationResult.Known known) {
                    var next = new java.util.ArrayList<>(parents);
                    next.add(structure);
                    inspectPath(row, base, known.value(), steps, position + 1, next, frame, consumer, undecided);
                } else if (!(result instanceof EvaluationResult.UndefinedByData)) {
                    undecided.accept(row, result);
                }
            } else if (steps.get(position) instanceof guru.interlis.model.PathStep.ThisStep
                    || steps.get(position) instanceof guru.interlis.model.PathStep.ViewBaseStep) {
                inspectPath(row, base, current, steps, position + 1, parents, frame, consumer, undecided);
            } else {
                undecided.accept(row, EvaluationResult.Unsupported.INSTANCE);
            }
        }
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
                dataset.cancellation()).withDataset(new DatasetPathContext(dataset, origin)).withRow(row)
                .withParents(row.parents());
    }
}
