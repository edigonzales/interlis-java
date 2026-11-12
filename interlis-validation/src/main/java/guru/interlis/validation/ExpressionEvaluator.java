package guru.interlis.validation;

import guru.interlis.model.BinaryExpression;
import guru.interlis.model.BinaryOperator;
import guru.interlis.model.DefinedExpression;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.FunctionCallExpression;
import guru.interlis.model.ObjectSetExpression;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathIndex;
import guru.interlis.model.PathStep;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeKind;
import guru.interlis.model.UnaryExpression;
import guru.interlis.model.UndefinedExpression;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferValue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import guru.interlis.validation.EvaluationResult.*;

final class ExpressionEvaluator {
    enum Family { BOOLEAN, TEXT, NUMBER, ENUM, COORDINATE, UNDEFINED, OTHER }

    record Comparison(Family family, Map<List<String>, Integer> enumOrder) { }
    private final ExpressionProgram program;

    ExpressionEvaluator(ExpressionProgram program) {
        this.program = program;
    }

    EvaluationResult evaluate(EvaluationFrame context) {
        if (!program.snapshot().equals(context.plan().snapshotId())) {
            throw new IllegalArgumentException("Expression and evaluation frame belong to different snapshots");
        }
        return evaluate(program.root(), context);
    }

    private EvaluationResult evaluate(ExpressionId id, EvaluationFrame context) {
        context.visit().run();
        var constant = program.constants().get(id);
        if (constant != null) {
            return constant;
        }
        return switch (program.nodes().get(id)) {
            case PathExpression path -> readPath(path, context);
            case FunctionCallExpression call -> objectCount(call, context);
            case DefinedExpression defined -> switch (evaluate(defined.operand(), context)) {
                case Known _ -> bool(true);
                case UndefinedByData _ -> bool(false);
                case EvaluationResult result -> result;
            };
            case UnaryExpression unary -> unary(unary, evaluate(unary.operand(), context));
            case BinaryExpression binary -> binary(binary, context);
            default -> throw new IllegalStateException("Invalid compiled local expression");
        };
    }

    private EvaluationResult objectCount(FunctionCallExpression call, EvaluationFrame context) {
        context.visit().run();
        if (!(program.nodes().get(call.children().getFirst()) instanceof ObjectSetExpression)) {
            throw new IllegalStateException("Missing compiled object set");
        }
        if (context.objectCount().isEmpty()) {
            return MissingContext.POPULATION;
        }
        long count = context.objectCount().orElseThrow();
        return new Known(new TransferValue.NumberValue(Long.toString(count), java.math.BigDecimal.valueOf(count)));
    }

    private EvaluationResult binary(BinaryExpression binary, EvaluationFrame context) {
        var left = evaluate(binary.left(), context);
        return switch (binary.operator()) {
            case AND, OR, IMPLIES -> logical(binary, left, context);
            case EQ, NEQ, LT, LTE, GT, GTE -> comparison(binary, left, evaluate(binary.right(), context));
            default -> throw new IllegalStateException("Unexpected compiled binary operator");
        };
    }

    private EvaluationResult logical(BinaryExpression binary, EvaluationResult left, EvaluationFrame context) {
        if (!(left instanceof Known known)) {
            return left;
        }
        if (!(known.value() instanceof TransferValue.BooleanValue value)) {
            return Unsupported.INSTANCE;
        }
        return switch (binary.operator()) {
            case AND -> value.value() ? logicalOperand(evaluate(binary.right(), context)) : bool(false);
            case OR -> value.value() ? bool(true) : logicalOperand(evaluate(binary.right(), context));
            case IMPLIES -> value.value() ? logicalOperand(evaluate(binary.right(), context)) : bool(true);
            default -> throw new IllegalStateException("Unexpected logical operator");
        };
    }

    private EvaluationResult comparison(BinaryExpression binary, EvaluationResult left, EvaluationResult right) {
        if (left instanceof BlockedByViolation) {
            return left;
        }
        if (right instanceof BlockedByViolation) {
            return right;
        }
        if (left instanceof MissingContext) {
            return left;
        }
        if (right instanceof MissingContext) {
            return right;
        }
        if (left instanceof Unsupported || right instanceof Unsupported) {
            return Unsupported.INSTANCE;
        }
        var comparison = program.comparisons().get(binary.id());
        if (left instanceof UndefinedByData || right instanceof UndefinedByData) {
            boolean testsUndefined = program.nodes().get(binary.left()) instanceof UndefinedExpression
                    || program.nodes().get(binary.right()) instanceof UndefinedExpression;
            if (testsUndefined && (binary.operator() == BinaryOperator.EQ || binary.operator() == BinaryOperator.NEQ)) {
                boolean equal = left instanceof UndefinedByData && right instanceof UndefinedByData;
                return bool(binary.operator() == BinaryOperator.EQ ? equal : !equal);
            }
            if (left instanceof Known lhs && !matches(comparison.family(), lhs.value())
                    || right instanceof Known rhs && !matches(comparison.family(), rhs.value())) {
                return Unsupported.INSTANCE;
            }
            return new UndefinedByData(true);
        }
        var ordering = order(comparison, ((Known) left).value(), ((Known) right).value());
        if (ordering.isEmpty()) {
            return Unsupported.INSTANCE;
        }
        int order = ordering.getAsInt();
        return bool(switch (binary.operator()) {
            case EQ -> order == 0;
            case NEQ -> order != 0;
            case LT -> order < 0;
            case LTE -> order <= 0;
            case GT -> order > 0;
            case GTE -> order >= 0;
            default -> throw new IllegalStateException("Unexpected comparison operator");
        });
    }

    private static EvaluationResult unary(UnaryExpression expression, EvaluationResult operand) {
        if (!(operand instanceof Known known)) {
            return operand;
        }
        return switch (expression.operator()) {
            case NOT -> known.value() instanceof TransferValue.BooleanValue value ? bool(!value.value()) : Unsupported.INSTANCE;
            case NEGATE -> {
                if (!(known.value() instanceof TransferValue.NumberValue value)) {
                    yield Unsupported.INSTANCE;
                }
                ScalarRules.requireNumericBudget(value.exactValue());
                var negated = value.exactValue().negate();
                yield new Known(new TransferValue.NumberValue(negated.toString(), negated));
            }
        };
    }

    private static EvaluationResult logicalOperand(EvaluationResult result) {
        return result instanceof Known known && !(known.value() instanceof TransferValue.BooleanValue)
                ? Unsupported.INSTANCE : result;
    }

    private static EvaluationResult readPath(PathExpression expression, EvaluationFrame context) {
        EvaluationResult current = value(context.current(), false);
        for (var step : expression.path().steps()) {
            context.visit().run();
            if (!(current instanceof Known known)) {
                return current;
            }
            current = switch (step) {
                case PathStep.ThisStep _ -> value(context.current(), false);
                case PathStep.AttributeStep attribute -> attribute(context, known.value(), attribute.target());
                case PathStep.IndexStep index -> index(known.value(), index.index(),
                        context.plan().effectiveType(index.resultType()).map(type -> type.kind() == TypeKind.TEXT).orElse(false));
                default -> throw new IllegalStateException("Unexpected compiled path step");
            };
        }
        return current;
    }

    static EvaluationResult attribute(EvaluationFrame context, TransferValue current, SymbolId target) {
        if (!(current instanceof TransferValue.Structured object)) {
            return Unsupported.INSTANCE;
        }
        var type = context.plan().object(object.type());
        if (type == null) {
            return MissingContext.OBJECT_TYPE;
        }
        var field = context.plan().field(type, target);
        if (field == null) {
            return MissingContext.ATTRIBUTE_VALUE;
        }
        var found = object.value(field.slot().symbol());
        if (found.isEmpty() && (field.derived() || context.partial()
                && field.values().getFirst().type().kind() == TypeKind.GEOMETRY)) {
            return MissingContext.ATTRIBUTE_VALUE;
        }
        return found.map(item -> value(item, field.values().getFirst().type().kind() == TypeKind.TEXT))
                .orElseGet(() -> new UndefinedByData(false));
    }

    private static EvaluationResult index(TransferValue current, PathIndex index, boolean textType) {
        List<? extends TransferValue> values = switch (current) {
            case TransferValue.Repeated repeated when repeated.kind() == guru.interlis.model.CollectionKind.LIST ->
                    repeated.values();
            case TransferGeometry.Coordinate coordinate -> coordinate.ordinates();
            default -> null;
        };
        if (values == null) {
            return Unsupported.INSTANCE;
        }
        BigInteger position = switch (index.kind()) {
            case FIRST -> BigInteger.ONE;
            case LAST -> BigInteger.valueOf(values.size());
            case POSITION -> index.position().orElseThrow();
        };
        if (position.signum() <= 0 || position.compareTo(BigInteger.valueOf(values.size())) > 0) {
            return new UndefinedByData(false);
        }
        return value(values.get(position.intValueExact() - 1), textType);
    }

    static EvaluationResult value(TransferValue value, boolean textType) {
        if (value == TransferValue.Undefined.INSTANCE
                || textType && value instanceof TransferValue.Text text && ScalarRules.textLength(text.value()) == 0) {
            return new UndefinedByData(false);
        }
        return new Known(value);
    }

    static Known bool(boolean value) {
        return new Known(new TransferValue.BooleanValue(value));
    }

    private static boolean matches(Family family, TransferValue value) {
        return switch (family) {
            case NUMBER -> value instanceof TransferValue.NumberValue;
            case BOOLEAN -> value instanceof TransferValue.BooleanValue;
            case TEXT -> value instanceof TransferValue.Text;
            case ENUM -> value instanceof TransferValue.Enumeration;
            case COORDINATE -> value instanceof TransferGeometry.Coordinate;
            case OTHER, UNDEFINED -> false;
        };
    }

    private static OptionalInt order(Comparison comparison, TransferValue left, TransferValue right) {
        return switch (comparison.family()) {
            case NUMBER -> left instanceof TransferValue.NumberValue a && right instanceof TransferValue.NumberValue b
                    ? OptionalInt.of(numberOrder(a, b)) : OptionalInt.empty();
            case BOOLEAN -> left instanceof TransferValue.BooleanValue a && right instanceof TransferValue.BooleanValue b
                    ? OptionalInt.of(Boolean.compare(a.value(), b.value())) : OptionalInt.empty();
            case TEXT -> left instanceof TransferValue.Text a && right instanceof TransferValue.Text b
                    ? OptionalInt.of(a.value().equals(b.value()) ? 0 : 1) : OptionalInt.empty();
            case ENUM -> {
                if (!(left instanceof TransferValue.Enumeration a) || !(right instanceof TransferValue.Enumeration b)) {
                    yield OptionalInt.empty();
                }
                if (comparison.enumOrder().isEmpty()) {
                    yield OptionalInt.of(a.path().equals(b.path()) ? 0 : 1);
                }
                var lhs = comparison.enumOrder().get(a.path());
                var rhs = comparison.enumOrder().get(b.path());
                yield lhs == null || rhs == null ? OptionalInt.empty() : OptionalInt.of(lhs.compareTo(rhs));
            }
            case COORDINATE -> {
                if (!(left instanceof TransferGeometry.Coordinate a) || !(right instanceof TransferGeometry.Coordinate b)
                        || a.ordinates().size() != b.ordinates().size()) {
                    yield OptionalInt.empty();
                }
                int order = 0;
                for (int i = 0; i < a.ordinates().size() && order == 0; i++) {
                    order = numberOrder(a.ordinates().get(i), b.ordinates().get(i));
                }
                yield OptionalInt.of(order);
            }
            case OTHER, UNDEFINED -> OptionalInt.empty();
        };
    }

    private static int numberOrder(TransferValue.NumberValue left, TransferValue.NumberValue right) {
        ScalarRules.requireNumericBudget(left.exactValue());
        ScalarRules.requireNumericBudget(right.exactValue());
        return left.exactValue().compareTo(right.exactValue());
    }
}
