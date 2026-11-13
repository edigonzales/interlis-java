package guru.interlis.validation;

import guru.interlis.model.BinaryExpression;
import guru.interlis.model.BinaryOperator;
import guru.interlis.model.DefinedExpression;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.FormattedType;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.ParameterSymbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.SymbolValueExpression;
import guru.interlis.model.NumericType;
import guru.interlis.model.UnaryOperator;
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
import java.util.ArrayList;
import java.util.Map;
import java.util.OptionalInt;

import guru.interlis.validation.EvaluationResult.*;

final class ExpressionEvaluator {
    enum Family { BOOLEAN, TEXT, NUMBER, ENUM, COORDINATE, FORMATTED, UNDEFINED, OTHER }

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
            case FunctionCallExpression call -> function(call, context);
            case SymbolValueExpression symbol -> symbolValue(symbol, context);
            case DefinedExpression defined -> switch (evaluate(defined.operand(), context)) {
                case Known _, KnownNumber _ -> bool(true);
                case UndefinedByData _ -> bool(false);
                case EvaluationResult result -> result;
            };
            case UnaryExpression unary -> unary(unary, evaluate(unary.operand(), context));
            case BinaryExpression binary -> binary(binary, context);
            default -> throw new IllegalStateException("Invalid compiled local expression");
        };
    }

    private EvaluationResult symbolValue(SymbolValueExpression expression, EvaluationFrame frame) {
        var plan = frame.plan();
        var id = new SymbolRef(plan.snapshotId(), plan.canonical(expression.target()));
        if (plan.symbol(expression.target()).orElseThrow() instanceof ParameterSymbol) {
            var parameter = plan.bindings().parameters().get(id);
            return parameter == null ? MissingContext.PARAMETER : value(parameter, false);
        }
        return new Known(new TransferValue.ClassName(plan.qualifiedName(expression.target())));
    }

    private EvaluationResult function(FunctionCallExpression call, EvaluationFrame frame) {
        var plan = frame.plan();
        var function = (FunctionSymbol) call.targetFunction().flatMap(plan::symbol).orElseThrow();
        var standard = StandardFunctions.bind(function).orElse(null);
        if (standard == StandardFunctions.OBJECT_COUNT
                && program.nodes().get(call.children().getFirst()) instanceof ObjectSetExpression) {
            frame.visit().run();
            return frame.objectCount().isEmpty() ? MissingContext.POPULATION
                    : StandardFunctions.count(frame.objectCount().orElseThrow());
        }
        if (standard == StandardFunctions.CONVERT_UNIT) {
            var child = call.children().getFirst();
            var argument = evaluate(child, frame);
            var amount = number(argument);
            if (amount == null) {
                return argument instanceof Known ? Unsupported.INSTANCE : argument;
            }
            var source = (NumericType) plan.effectiveType(program.nodes().get(child).typeId()).orElseThrow();
            var target = (NumericType) plan.effectiveType(call.typeId()).orElseThrow();
            return UnitConversions.convert(frame, source.range().unit(), target.range().unit(), amount);
        }
        if (standard == null && !plan.bindings().functions().containsKey(
                new SymbolRef(plan.snapshotId(), plan.canonical(function.id())))) {
            return Unsupported.INSTANCE;
        }
        var args = new ArrayList<TransferValue>(call.children().size());
        for (var child : call.children()) {
            var result = evaluate(child, frame);
            if (result instanceof Known known) {
                args.add(known.value());
            } else if (result instanceof UndefinedByData && standard == null) {
                args.add(TransferValue.Undefined.INSTANCE);
            } else if (result instanceof KnownNumber numeric) {
                var decimal = numeric.value().decimal();
                if (decimal.isEmpty()) {
                    return Unsupported.INSTANCE;
                }
                var value = decimal.orElseThrow();
                args.add(new TransferValue.NumberValue(value.toString(), value));
            } else {
                return result;
            }
        }
        if (standard == null) {
            return FunctionInvoker.invoke(function, args, frame);
        }
        if (standard == StandardFunctions.OBJECT_COUNT) {
            return args.getFirst() instanceof TransferValue.Structured ? StandardFunctions.count(1) : Unsupported.INSTANCE;
        }
        var firstType = plan.effectiveType(program.nodes().get(call.children().getFirst()).typeId()).orElseThrow();
        return standard.evaluate(args, frame, firstType instanceof EnumerationType enumeration ? enumeration : null);
    }

    private EvaluationResult binary(BinaryExpression binary, EvaluationFrame context) {
        var left = evaluate(binary.left(), context);
        return switch (binary.operator()) {
            case AND, OR, IMPLIES -> logical(binary, left, context);
            case EQ, NEQ, LT, LTE, GT, GTE -> comparison(binary, left, evaluate(binary.right(), context), context);
            case PLUS, MINUS, MUL, DIV -> arithmetic(binary.operator(), left, evaluate(binary.right(), context));
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

    private EvaluationResult comparison(BinaryExpression binary, EvaluationResult left, EvaluationResult right,
                                        EvaluationFrame context) {
        var gap = contextGap(left, right);
        if (gap != null) {
            return gap;
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
        if (comparison.family() == Family.NUMBER) {
            var lhs = number(left);
            var rhs = number(right);
            return lhs == null || rhs == null ? Unsupported.INSTANCE : bool(NumericSemantics.compare(binary.operator(), lhs, rhs));
        }
        if (!(left instanceof Known lhs) || !(right instanceof Known rhs)) {
            return Unsupported.INSTANCE;
        }
        var ordering = comparison.family() == Family.FORMATTED
                ? formatOrder(binary, context, lhs.value(), rhs.value()) : order(comparison, lhs.value(), rhs.value());
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

    private OptionalInt formatOrder(BinaryExpression binary, EvaluationFrame context,
                                    TransferValue left, TransferValue right) {
        if (!(left instanceof TransferValue.Text lhs) || !(right instanceof TransferValue.Text rhs)) {
            return OptionalInt.empty();
        }
        var leftType = context.plan().effectiveType(program.nodes().get(binary.left()).typeId()).orElseThrow();
        var rightType = context.plan().effectiveType(program.nodes().get(binary.right()).typeId()).orElseThrow();
        var leftFormat = leftType instanceof FormattedType format ? format : (FormattedType) rightType;
        var rightFormat = rightType instanceof FormattedType format ? format : leftFormat;
        return FormatRules.compare(context.plan(), leftFormat, rightFormat, lhs.value(), rhs.value(), context.visit());
    }

    private static EvaluationResult contextGap(EvaluationResult left, EvaluationResult right) {
        if (left instanceof BlockedByViolation || right instanceof BlockedByViolation) {
            return left instanceof BlockedByViolation ? left : right;
        }
        if (left instanceof MissingContext || right instanceof MissingContext) {
            return left instanceof MissingContext ? left : right;
        }
        return left instanceof Unsupported || right instanceof Unsupported ? Unsupported.INSTANCE : null;
    }

    private static NumericSemantics.Number number(EvaluationResult result) {
        return switch (result) {
            case KnownNumber known -> known.value();
            case Known known when known.value() instanceof TransferValue.NumberValue decimal ->
                    NumericSemantics.Number.exact(decimal.exactValue());
            default -> null;
        };
    }

    private static EvaluationResult arithmetic(BinaryOperator operator, EvaluationResult left, EvaluationResult right) {
        var gap = contextGap(left, right);
        if (gap != null) {
            return gap;
        }
        var lhs = number(left);
        var rhs = number(right);
        if (lhs == null) {
            return left instanceof Known ? Unsupported.INSTANCE : left;
        }
        if (rhs == null) {
            return right instanceof Known ? Unsupported.INSTANCE : right;
        }
        return switch (operator) {
            case PLUS -> new KnownNumber(NumericSemantics.add(lhs, rhs));
            case MINUS -> new KnownNumber(NumericSemantics.subtract(lhs, rhs));
            case MUL -> new KnownNumber(NumericSemantics.multiply(lhs, rhs));
            case DIV -> NumericSemantics.divide(lhs, rhs).<EvaluationResult>map(KnownNumber::new)
                    .orElseGet(() -> new UndefinedByData(false));
            default -> throw new IllegalArgumentException("Expected an arithmetic operator");
        };
    }

    private static EvaluationResult unary(UnaryExpression expression, EvaluationResult operand) {
        if (expression.operator() == UnaryOperator.NEGATE) {
            var number = number(operand);
            if (number != null) {
                return new KnownNumber(NumericSemantics.negate(number));
            }
        }
        if (!(operand instanceof Known known)) {
            return operand;
        }
        return known.value() instanceof TransferValue.BooleanValue value
                && expression.operator() == UnaryOperator.NOT
                ? bool(!value.value()) : Unsupported.INSTANCE;
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
            case TEXT, FORMATTED -> value instanceof TransferValue.Text;
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
            case OTHER, UNDEFINED, FORMATTED -> OptionalInt.empty();
        };
    }

    private static int numberOrder(TransferValue.NumberValue left, TransferValue.NumberValue right) {
        ScalarRules.requireNumericBudget(left.exactValue());
        ScalarRules.requireNumericBudget(right.exactValue());
        return left.exactValue().compareTo(right.exactValue());
    }
}
