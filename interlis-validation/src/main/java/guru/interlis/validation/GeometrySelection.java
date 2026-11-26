package guru.interlis.validation;

import guru.interlis.model.FunctionCallExpression;
import guru.interlis.model.GeometryType;
import guru.interlis.model.PathStep;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolValueExpression;
import guru.interlis.model.UndefinedExpression;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferValue;
import guru.interlis.validation.ValidationSession.ValuePath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static guru.interlis.validation.GeometryEngine.Check.*;

record GeometrySelection(List<SymbolId> holders, List<SymbolId> values) {
    static Optional<GeometrySelection> bind(FunctionCallExpression call, ValidationPlan plan) {
        var holders = path(call, 1, plan);
        return holders.flatMap(prefix -> path(call, 2, plan).filter(path -> !path.isEmpty())
                .map(path -> new GeometrySelection(prefix, path)));
    }

    private static Optional<List<SymbolId>> path(FunctionCallExpression call, int argument, ValidationPlan plan) {
        var parsed = call.argumentPaths().get(argument);
        if (parsed != null) {
            return parsed.binding().flatMap(plan::boundPath).flatMap(path -> {
                var steps = path.path().steps();
                if (steps.stream().anyMatch(step -> !(step instanceof PathStep.AttributeStep
                        || step instanceof PathStep.ThisStep))) {
                    return Optional.empty();
                }
                return Optional.of(steps.stream().filter(PathStep.AttributeStep.class::isInstance)
                        .map(PathStep.AttributeStep.class::cast).map(PathStep.AttributeStep::target).toList());
            });
        }
        return plan.expressionNode(call.children().get(argument)).flatMap(node -> {
            if (node instanceof SymbolValueExpression symbol && plan.symbol(symbol.target())
                    .filter(guru.interlis.model.AttributeSymbol.class::isInstance).isPresent()) {
                return Optional.of(List.of(symbol.target()));
            }
            if (argument == 1 && (node instanceof UndefinedExpression
                    || node instanceof guru.interlis.model.LiteralExpression literal
                    && literal.textValue().filter(String::isEmpty).isPresent())) {
                return Optional.of(List.of());
            }
            return Optional.empty();
        });
    }

    List<GeometryEngine.Input> from(DatasetRuleContext dataset, DatasetRuleContext.Occurrence origin, boolean[] missing) {
        var frame = frame(dataset, origin);
        var output = new ArrayList<GeometryEngine.Input>();
        walk(0, frame.current(), null, frame, missing, output::add, null);
        return output;
    }

    GeometryEngine.Check check(DatasetRuleContext dataset, DatasetRuleContext.Occurrence origin,
                               GeometryEngine engine, GeometryEngine.Control control) {
        return check(frame(dataset, origin), engine, control, GeometryEngine.CoveragePurpose.AREA, true);
    }

    private static EvaluationFrame frame(DatasetRuleContext dataset, DatasetRuleContext.Occurrence origin) {
        return new EvaluationFrame(dataset.plan, origin.object().data(), origin.partial(), dataset::visit,
                java.util.OptionalLong.empty(), dataset.cancellation()).withDataset(new DatasetPathContext(dataset, origin));
    }

    GeometryEngine.Check check(EvaluationFrame frame, GeometryEngine engine, GeometryEngine.Control control,
                               GeometryEngine.CoveragePurpose purpose, boolean groupByHolder) {
        var missing = new boolean[] {frame.partial()};
        if (!groupByHolder || holders.isEmpty()) {
            var inputs = new ArrayList<GeometryEngine.Input>();
            walk(0, frame.current(), null, frame, missing, inputs::add, null);
            return check(engine, control, inputs, !missing[0], purpose);
        }
        var result = new GeometryEngine.Check[] {VALID};
        walk(0, frame.current(), null, frame, missing, null,
                (inputs, complete) -> result[0] = combine(result[0], check(engine, control, inputs, complete, purpose)));
        return combine(result[0], missing[0] ? MISSING_PARAMETERS : VALID);
    }

    private static GeometryEngine.Check check(GeometryEngine engine, GeometryEngine.Control control,
                                              List<GeometryEngine.Input> inputs, boolean complete,
                                              GeometryEngine.CoveragePurpose purpose) {
        return engine.checkCoverage(new GeometryEngine.Source() {
            @Override public boolean complete() {
                return complete;
            }

            @Override public boolean reportLocalFindings() {
                return true;
            }

            @Override public GeometryEngine.Cursor open() {
                var iterator = inputs.iterator();
                return new GeometryEngine.Cursor() {
                    @Override public Optional<GeometryEngine.Input> next() {
                        return iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty();
                    }

                    @Override public void close() { }
                };
            }
        }, purpose, control);
    }

    static GeometryEngine.Check combine(GeometryEngine.Check left, GeometryEngine.Check right) {
        if (left == INVALID || right == INVALID) return INVALID;
        if (left == NUMERICALLY_UNDETERMINED || right == NUMERICALLY_UNDETERMINED) return NUMERICALLY_UNDETERMINED;
        if (left == UNSUPPORTED || right == UNSUPPORTED) return UNSUPPORTED;
        if (left == MISSING_PARAMETERS || right == MISSING_PARAMETERS) return MISSING_PARAMETERS;
        return VALID;
    }

    @FunctionalInterface
    private interface GroupSelection {
        void accept(List<GeometryEngine.Input> inputs, boolean complete);
    }

    private void walk(int index, TransferValue value, ValuePath path, EvaluationFrame frame,
                      boolean[] missing, Consumer<GeometryEngine.Input> output, GroupSelection groups) {
        frame.visit().run();
        int total = holders.size() + values.size();
        if (index >= total) {
            return;
        }
        if (value instanceof TransferValue.Repeated repeated) {
            for (int i = 0; i < repeated.values().size(); i++) {
                walk(index, repeated.values().get(i), new ValuePath(path, new DataLocation.Index(i + 1)),
                        frame, missing, output, groups);
            }
            return;
        }
        if (!(value instanceof TransferValue.Structured owner)) {
            missing[0] = true;
            return;
        }
        if (groups != null && index == holders.size()) {
            var inputs = new ArrayList<GeometryEngine.Input>();
            var groupMissing = new boolean[] {frame.partial()};
            walk(index, owner, path, frame, groupMissing, inputs::add, null);
            groups.accept(inputs, !groupMissing[0]);
            return;
        }
        var target = index < holders.size() ? holders.get(index) : values.get(index - holders.size());
        var object = frame.plan().object(owner.type());
        var field = object == null ? null : frame.plan().field(object, target);
        if (field == null) {
            missing[0] = true;
            return;
        }
        var at = new ValuePath(path, new DataLocation.Slot(field.slot().symbol()));
        var evaluated = ExpressionEvaluator.attribute(frame, owner, target);
        if (evaluated instanceof EvaluationResult.UndefinedByData) {
            return;
        }
        if (!(evaluated instanceof EvaluationResult.Known known)) {
            missing[0] = true;
            return;
        }
        if (index + 1 == total) {
            if (!(known.value() instanceof TransferGeometry geometry)
                    || !(frame.effectiveType(field.values().getFirst()) instanceof GeometryType type)
                    || !GeometryRules.standardForms(GeometryRules.forms(frame.plan(), type))) {
                missing[0] = true;
                return;
            }
            var domains = frame.domains();
            output.accept(GeometryParameters.input(geometry, type, frame.geometryLocation(ValuePath.steps(at)),
                    field.slot().attribute().languageVersion(), id -> GeometryParameters.coordinate(frame.plan(), domains, id),
                    frame.partial()));
            return;
        }
        walk(index + 1, known.value(), at, frame.descend(owner, known.value()), missing, output, groups);
    }
}
