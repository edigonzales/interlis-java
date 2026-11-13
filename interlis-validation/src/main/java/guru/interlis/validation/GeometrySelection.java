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
        var root = origin.object().data();
        var frame = new EvaluationFrame(dataset.plan, root, origin.partial(), dataset::visit,
                java.util.OptionalLong.empty(), dataset.cancellation()).withDataset(new DatasetPathContext(dataset, origin));
        var output = new ArrayList<GeometryEngine.Input>();
        walk(0, root, null, frame, origin, missing, output);
        return output;
    }

    private void walk(int index, TransferValue value, ValuePath path, EvaluationFrame frame,
                      DatasetRuleContext.Occurrence origin, boolean[] missing, List<GeometryEngine.Input> output) {
        frame.visit().run();
        int total = holders.size() + values.size();
        if (index >= total) {
            return;
        }
        if (value instanceof TransferValue.Repeated repeated) {
            for (int i = 0; i < repeated.values().size(); i++) {
                walk(index, repeated.values().get(i), new ValuePath(path, new DataLocation.Index(i + 1)),
                        frame, origin, missing, output);
            }
            return;
        }
        if (!(value instanceof TransferValue.Structured owner)) {
            missing[0] = true;
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
            var context = ValidationSession.BasketContext.of(origin.basket(), frame.plan());
            var vertex = type.vertexDomain().map(id -> GeometryParameters.coordinate(frame.plan(), context, id));
            output.add(new GeometryEngine.Input(geometry, type, origin.location(ValuePath.steps(at)),
                    field.slot().attribute().languageVersion(), GeometryParameters.quantum(vertex.orElse(null)),
                    GeometryParameters.overlap(type, id -> GeometryParameters.coordinate(frame.plan(), context, id)),
                    origin.partial(), vertex.map(guru.interlis.model.CoordinateType::id)));
            return;
        }
        walk(index + 1, known.value(), at, frame.descend(owner, known.value()), origin, missing, output);
    }
}
