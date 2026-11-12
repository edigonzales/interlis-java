package guru.interlis.validation;

import guru.interlis.model.CollectionType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathStep;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypeKind;
import guru.interlis.model.TypeRef;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.transfer.TransferValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import guru.interlis.validation.ValidationCoverage.ConstraintStatus;
import guru.interlis.validation.ValidationSession.ValuePath;

import static guru.interlis.validation.ValidationCoverage.ConstraintStatus.*;

final class LocalUnique {
    private static final int MAX_PREFIX_STEPS = 128;

    private LocalUnique() { }

    static boolean supports(UniquenessConstraint rule, SemanticGraph graph, TypeRelations relations,
                            Function<ExpressionId, LocalExpressions.Program> programs) {
        Function<TypeId, TypeDescriptor> effective = id -> relations.resolveAliases(new TypeRef(graph.snapshotId(), id)).known()
                .flatMap(resolved -> graph.type(resolved.effectiveType())).orElse(null);
        for (var key : rule.localKeys()) {
            if (!(programs.apply(key.prefix()).expression() instanceof PathExpression path)
                    || path.path().steps().isEmpty() || path.path().steps().size() > MAX_PREFIX_STEPS
                    || path.path().steps().stream().anyMatch(step -> !(step instanceof PathStep.AttributeStep))
                    || !(effective.apply(path.typeId()) instanceof CollectionType collection)
                    || !(effective.apply(collection.elementType()) instanceof StructureValueType)
                    || key.fields().isEmpty()) {
                return false;
            }
            for (var field : key.fields()) {
                if (!(programs.apply(field).expression() instanceof PathExpression fieldPath)
                        || !scalarPath(fieldPath, effective)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean scalarPath(PathExpression path, Function<TypeId, TypeDescriptor> effective) {
        var type = effective.apply(path.typeId());
        return path.path().steps().size() == 1 && path.path().steps().getFirst() instanceof PathStep.AttributeStep
                && type != null && switch (type.kind()) {
                    case BOOLEAN, TEXT, NUMERIC, ENUMERATION, ALIGNMENT -> true;
                    default -> false;
                };
    }

    static ConstraintStatus check(UniquenessConstraint unique, ValidationPlan plan, TransferValue.Structured owner,
                                  ValuePath path, boolean partial, Runnable visit,
                                  BiConsumer<ValuePath, ValuePath> duplicate) {
        var status = CHECKED;
        for (var key : unique.localKeys()) {
            var prefix = (PathExpression) plan.expression(key.prefix()).expression();
            var fields = key.fields().stream().map(plan::expression).map(program ->
                    new KeyField(program, plan.effectiveType(program.expression().typeId()).orElseThrow().kind())).toList();
            var check = new LocalUnique.Check(plan, partial, visit, duplicate, prefix.path().steps(), fields);
            check.walk(owner, path, 0, null);
            status = combine(status, check.status);
        }
        return status;
    }

    private static ConstraintStatus combine(ConstraintStatus a, ConstraintStatus b) {
        if (a == MISSING_RUNTIME_CONTEXT || b == MISSING_RUNTIME_CONTEXT) {
            return MISSING_RUNTIME_CONTEXT;
        }
        return a == UNSUPPORTED_VALUE || b == UNSUPPORTED_VALUE ? UNSUPPORTED_VALUE : CHECKED;
    }

    private record KeyField(LocalExpressions.Program program, TypeKind kind) { }

    private static final class Check {
        private final ValidationPlan plan;
        private final boolean partial;
        private final Runnable visit;
        private final BiConsumer<ValuePath, ValuePath> duplicate;
        private final List<PathStep> prefix;
        private final List<KeyField> fields;
        private final Map<List<Object>, ValuePath> first = new HashMap<>();
        private ConstraintStatus status = CHECKED;

        Check(ValidationPlan plan, boolean partial, Runnable visit, BiConsumer<ValuePath, ValuePath> duplicate,
              List<PathStep> prefix, List<KeyField> fields) {
            this.plan = plan;
            this.partial = partial;
            this.visit = visit;
            this.duplicate = duplicate;
            this.prefix = prefix;
            this.fields = fields;
        }

        private void walk(TransferValue value, ValuePath path, int next, TypeDescriptor expected) {
            visit.run();
            if (value == TransferValue.Undefined.INSTANCE) {
                return;
            }
            if (expected instanceof CollectionType collection) {
                if (!(value instanceof TransferValue.Repeated repeated) || repeated.kind() != collection.collectionKind()
                        || !(plan.effectiveType(collection.elementType()).orElse(null) instanceof StructureValueType element)) {
                    gap(UNSUPPORTED_VALUE);
                    return;
                }
                for (int i = 0; i < repeated.values().size(); i++) {
                    var item = repeated.values().get(i);
                    var at = indexed(path, i + 1);
                    if (next == prefix.size()) {
                        candidate(item, at, element);
                    } else {
                        walk(item, at, next, element);
                    }
                }
            } else if (next < prefix.size() && prefix.get(next) instanceof PathStep.AttributeStep attribute
                    && value instanceof TransferValue.Structured structured) {
                var type = plan.object(structured.type());
                if (expected instanceof StructureValueType structure && (type == null || !plan.assignable(structure, type))) {
                    gap(UNSUPPORTED_VALUE);
                    return;
                }
                var field = type == null ? null : type.field(attribute.target());
                if (field == null) {
                    gap(MISSING_RUNTIME_CONTEXT);
                    return;
                }
                var read = LocalExpressions.attribute(context(structured), structured, attribute.target());
                switch (read) {
                    case LocalExpressions.Known known -> walk(known.value(),
                            new ValuePath(path, new DataLocation.Slot(field.slot().symbol())), next + 1,
                            field.values().getFirst().type());
                    case LocalExpressions.UndefinedByData _ -> { }
                    case LocalExpressions.MissingContext _ -> gap(MISSING_RUNTIME_CONTEXT);
                    case LocalExpressions.Unsupported _ -> gap(UNSUPPORTED_VALUE);
                }
            } else {
                gap(UNSUPPORTED_VALUE);
            }
        }

        private void candidate(TransferValue value, ValuePath path, StructureValueType expected) {
            visit.run();
            if (value == TransferValue.Undefined.INSTANCE) {
                return;
            }
            if (!(value instanceof TransferValue.Structured structured)) {
                gap(UNSUPPORTED_VALUE);
                return;
            }
            var type = plan.object(structured.type());
            if (type == null || !plan.assignable(expected, type)) {
                gap(UNSUPPORTED_VALUE);
                return;
            }
            var parts = new ArrayList<>();
            var candidateStatus = CHECKED;
            var context = context(structured);
            for (var field : fields) {
                switch (field.program().evaluate(context)) {
                    case LocalExpressions.UndefinedByData _ -> { return; }
                    case LocalExpressions.MissingContext _ -> candidateStatus = MISSING_RUNTIME_CONTEXT;
                    case LocalExpressions.Unsupported _ -> candidateStatus = combine(candidateStatus, UNSUPPORTED_VALUE);
                    case LocalExpressions.Known known -> {
                        var part = part(known.value(), field.kind());
                        if (part == null) {
                            candidateStatus = combine(candidateStatus, UNSUPPORTED_VALUE);
                        } else {
                            parts.add(part);
                        }
                    }
                }
            }
            if (candidateStatus != CHECKED) {
                gap(candidateStatus);
                return;
            }
            var previous = first.putIfAbsent(List.copyOf(parts), path);
            if (previous != null) {
                duplicate.accept(previous, path);
            }
        }

        private LocalExpressions.Context context(TransferValue value) {
            return new LocalExpressions.Context(plan, value, partial, visit);
        }

        private void gap(ConstraintStatus gap) {
            status = combine(status, gap);
        }
    }

    private static ValuePath indexed(ValuePath path, int index) {
        return new ValuePath(path, new DataLocation.Index(index));
    }

    private static Object part(TransferValue value, TypeKind kind) {
        return switch (kind) {
            case BOOLEAN -> value instanceof TransferValue.BooleanValue bool ? bool.value() : null;
            case TEXT -> value instanceof TransferValue.Text text ? text.value() : null;
            case ENUMERATION, ALIGNMENT -> value instanceof TransferValue.Enumeration enumeration ? enumeration.path() : null;
            case NUMERIC -> {
                if (value instanceof TransferValue.NumberValue number) {
                    ScalarRules.requireNumericBudget(number.exactValue());
                    yield number.exactValue().stripTrailingZeros();
                }
                yield null;
            }
            default -> throw new IllegalStateException("Unsupported local uniqueness key type");
        };
    }
}
