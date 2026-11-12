package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.BinaryExpression;
import guru.interlis.model.BinaryOperator;
import guru.interlis.model.ConstantExpression;
import guru.interlis.model.DefinedExpression;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.ExpressionTypeState;
import guru.interlis.model.LiteralExpression;
import guru.interlis.model.NumericType;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathIndex;
import guru.interlis.model.PathStep;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeKind;
import guru.interlis.model.TypeRef;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.UnaryExpression;
import guru.interlis.model.UndefinedExpression;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferValue;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

final class LocalExpressions {
    private static final int MAX_DEPTH = 128;
    private static final int MAX_NODES = 16_384;

    private LocalExpressions() { }

    sealed interface Result permits Known, UndefinedByData, MissingContext, Unsupported { }

    record Known(TransferValue value) implements Result { }
    record UndefinedByData(boolean comparison) implements Result { }
    enum MissingContext implements Result { INSTANCE }
    enum Unsupported implements Result { INSTANCE }

    enum Family { BOOLEAN, TEXT, NUMBER, ENUM, COORDINATE, UNDEFINED, OTHER }

    record Comparison(Family family, Map<List<String>, Integer> enumOrder) { }
    record Context(ValidationPlan plan, TransferValue current, boolean partial, Runnable visit) { }

    record Program(ExpressionId root, Map<ExpressionId, TypedExpression> nodes,
                   Map<ExpressionId, Result> constants, Map<ExpressionId, Comparison> comparisons) {
        Program {
            nodes = Map.copyOf(nodes);
            constants = Map.copyOf(constants);
            comparisons = Map.copyOf(comparisons);
        }

        TypedExpression expression() {
            return nodes.get(root);
        }

        Result evaluate(Context context) {
            return evaluate(root, context);
        }

        private Result evaluate(ExpressionId id, Context context) {
            context.visit().run();
            var constant = constants.get(id);
            if (constant != null) {
                return constant;
            }
            return switch (nodes.get(id)) {
                case PathExpression path -> readPath(path, context);
                case DefinedExpression defined -> switch (evaluate(defined.operand(), context)) {
                    case Known _ -> bool(true);
                    case UndefinedByData _ -> bool(false);
                    case Result result -> result;
                };
                case UnaryExpression unary -> unary(unary, evaluate(unary.operand(), context));
                case BinaryExpression binary -> binary(binary, context);
                default -> throw new IllegalStateException("Invalid compiled local expression");
            };
        }

        private Result binary(BinaryExpression binary, Context context) {
            var left = evaluate(binary.left(), context);
            return switch (binary.operator()) {
                case AND, OR, IMPLIES -> logical(binary, left, context);
                case EQ, NEQ, LT, LTE, GT, GTE -> comparison(binary, left, evaluate(binary.right(), context));
                default -> throw new IllegalStateException("Unexpected compiled binary operator");
            };
        }

        private Result logical(BinaryExpression binary, Result left, Context context) {
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

        private Result comparison(BinaryExpression binary, Result left, Result right) {
            if (left instanceof MissingContext || right instanceof MissingContext) {
                return MissingContext.INSTANCE;
            }
            if (left instanceof Unsupported || right instanceof Unsupported) {
                return Unsupported.INSTANCE;
            }
            var comparison = comparisons.get(binary.id());
            if (left instanceof UndefinedByData || right instanceof UndefinedByData) {
                boolean testsUndefined = nodes.get(binary.left()) instanceof UndefinedExpression
                        || nodes.get(binary.right()) instanceof UndefinedExpression;
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
    }

    static Optional<Program> compile(SemanticGraph graph, TypeRelations types, ExpressionId root) {
        var compiler = new Compiler(graph, types);
        return compiler.visit(root, 0)
                ? Optional.of(new Program(root, compiler.nodes, compiler.constants, compiler.comparisons))
                : Optional.empty();
    }

    private static final class Compiler {
        private final SemanticGraph graph;
        private final TypeRelations types;
        private final Map<ExpressionId, TypedExpression> nodes = new LinkedHashMap<>();
        private final Map<ExpressionId, Result> constants = new LinkedHashMap<>();
        private final Map<ExpressionId, Comparison> comparisons = new LinkedHashMap<>();

        private int visits;

        private Compiler(SemanticGraph graph, TypeRelations types) {
            this.graph = graph;
            this.types = types;
        }

        boolean visit(ExpressionId id, int depth) {
            if (depth > MAX_DEPTH || ++visits > MAX_NODES) {
                throw new ValidationLimitException("Expression exceeds the validation plan budget");
            }
            var expression = expression(id);
            for (var child : expression.children()) {
                if (!visit(child, depth + 1)) {
                    return false;
                }
            }
            boolean supported = switch (expression) {
                case LiteralExpression literal -> {
                    constants.put(id, literal(literal));
                    yield true;
                }
                case UndefinedExpression _ -> {
                    constants.put(id, new UndefinedByData(false));
                    yield true;
                }
                case ConstantExpression constant -> constant(constant);
                case PathExpression path -> path.path().steps().stream().allMatch(this::localStep);
                case DefinedExpression _ -> true;
                case UnaryExpression unary -> switch (unary.operator()) {
                    case NOT -> family(expression(unary.operand())) == Family.BOOLEAN;
                    case NEGATE -> family(expression(unary.operand())) == Family.NUMBER;
                };
                case BinaryExpression binary -> binary(binary);
                default -> false;
            };
            if (supported) {
                nodes.put(id, expression);
            }
            return supported;
        }

        private TypedExpression expression(ExpressionId id) {
            var node = graph.expression(id).orElseThrow(() -> new IllegalArgumentException("Missing expression " + id));
            if (node.typeState() != ExpressionTypeState.RESOLVED) {
                throw new IllegalArgumentException("Unresolved expression " + id);
            }
            return node;
        }

        private boolean localStep(PathStep step) {
            if (step instanceof PathStep.ThisStep || step instanceof PathStep.IndexStep) {
                return true;
            }
            if (step instanceof PathStep.AttributeStep attribute
                    && graph.symbol(attribute.target()).orElse(null) instanceof AttributeSymbol symbol) {
                return effective(symbol.declaredType()).map(type -> type.kind() != TypeKind.REFERENCE
                        && type.kind() != TypeKind.OBJECT_ARGUMENT && type.kind() != TypeKind.OBJECT_SET).orElse(false);
            }
            return false;
        }

        private boolean constant(ConstantExpression constant) {
            var name = constant.constantName();
            var family = family(constant);
            if (family == Family.BOOLEAN && (name.equals("#true") || name.equals("#false"))) {
                constants.put(constant.id(), bool(name.equals("#true")));
                return true;
            }
            if (family == Family.ENUM && name.startsWith("#")) {
                constants.put(constant.id(), new Known(new TransferValue.Enumeration(
                        List.of(name.substring(1).split("\\.")))));
                return true;
            }
            return false;
        }

        private boolean binary(BinaryExpression binary) {
            var left = expression(binary.left());
            var right = expression(binary.right());
            var lhs = family(left);
            var rhs = family(right);
            return switch (binary.operator()) {
                case AND, OR, IMPLIES -> lhs == Family.BOOLEAN && rhs == Family.BOOLEAN;
                case PLUS, MINUS, MUL, DIV -> false;
                case EQ, NEQ, LT, LTE, GT, GTE -> {
                    boolean equality = binary.operator() == BinaryOperator.EQ || binary.operator() == BinaryOperator.NEQ;
                    if (equality && (lhs == Family.UNDEFINED || rhs == Family.UNDEFINED)) {
                        comparisons.put(binary.id(), new Comparison(Family.UNDEFINED, Map.of()));
                        yield true;
                    }
                    if (lhs != rhs || lhs == Family.OTHER || lhs == Family.UNDEFINED) {
                        yield false;
                    }
                    if (lhs == Family.NUMBER && !compatibleNumbers(left, right)
                            || lhs == Family.COORDINATE && !left.typeId().equals(right.typeId())) {
                        yield false;
                    }
                    Map<List<String>, Integer> enumOrder = Map.of();
                    if (!equality && lhs != Family.NUMBER) {
                        if (lhs != Family.ENUM) {
                            yield false;
                        }
                        enumOrder = enumOrder(left, right);
                        if (enumOrder.isEmpty()) {
                            yield false;
                        }
                    }
                    comparisons.put(binary.id(), new Comparison(lhs, enumOrder));
                    yield true;
                }
            };
        }

        private boolean compatibleNumbers(TypedExpression left, TypedExpression right) {
            var lhs = (NumericType) effective(left.typeId()).orElseThrow();
            var rhs = (NumericType) effective(right.typeId()).orElseThrow();
            return compatible(lhs.range().unit(), rhs.range().unit(), left, right)
                    && compatible(lhs.range().referenceSystem(), rhs.range().referenceSystem(), left, right);
        }

        private static boolean compatible(Optional<?> left, Optional<?> right, TypedExpression lhs, TypedExpression rhs) {
            return left.equals(right) || left.isEmpty() && lhs instanceof LiteralExpression
                    || right.isEmpty() && rhs instanceof LiteralExpression;
        }

        private Map<List<String>, Integer> enumOrder(TypedExpression left, TypedExpression right) {
            var lhs = effective(left.typeId()).orElseThrow();
            var rhs = effective(right.typeId()).orElseThrow();
            if (lhs instanceof EnumerationType a && rhs instanceof EnumerationType b
                    && a.ordered() && b.ordered() && !a.circular() && !b.circular() && a.id().equals(b.id())) {
                var result = new LinkedHashMap<List<String>, Integer>();
                a.values().forEach(value -> result.put(List.of(value.path().split("\\.")), value.order()));
                return Map.copyOf(result);
            }
            return Map.of();
        }

        private Family family(TypedExpression expression) {
            if (expression instanceof UndefinedExpression) {
                return Family.UNDEFINED;
            }
            return effective(expression.typeId()).map(type -> switch (type.kind()) {
                case BOOLEAN -> Family.BOOLEAN;
                case TEXT -> Family.TEXT;
                case NUMERIC -> Family.NUMBER;
                case ENUMERATION, ALIGNMENT -> Family.ENUM;
                case COORDINATE -> ((guru.interlis.model.CoordinateType) type).multiCoordinate()
                        ? Family.OTHER : Family.COORDINATE;
                default -> Family.OTHER;
            }).orElse(Family.OTHER);
        }

        private Optional<TypeDescriptor> effective(guru.interlis.model.TypeId type) {
            return types.resolveAliases(new TypeRef(graph.snapshotId(), type)).known()
                    .flatMap(result -> graph.type(result.effectiveType()));
        }
    }

    private static Result literal(LiteralExpression expression) {
        return switch (expression.literalKind()) {
            case TEXT -> value(new TransferValue.Text(expression.textValue().orElseThrow()), true);
            case NUMBER -> {
                var number = expression.decimal().orElseThrow();
                yield new Known(new TransferValue.NumberValue(number.lexeme(), number.exactValue()));
            }
        };
    }

    private static Result unary(UnaryExpression expression, Result operand) {
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

    private static Result logicalOperand(Result result) {
        return result instanceof Known known && !(known.value() instanceof TransferValue.BooleanValue)
                ? Unsupported.INSTANCE : result;
    }

    private static Result readPath(PathExpression expression, Context context) {
        Result current = value(context.current(), false);
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

    static Result attribute(Context context, TransferValue current, SymbolId target) {
        if (!(current instanceof TransferValue.Structured object)) {
            return Unsupported.INSTANCE;
        }
        var type = context.plan().object(object.type());
        if (type == null) {
            return MissingContext.INSTANCE;
        }
        var field = context.plan().field(type, target);
        if (field == null) {
            return MissingContext.INSTANCE;
        }
        var found = object.value(field.slot().symbol());
        if (found.isEmpty() && (field.derived() || context.partial()
                && field.values().getFirst().type().kind() == TypeKind.GEOMETRY)) {
            return MissingContext.INSTANCE;
        }
        return found.map(item -> value(item, field.values().getFirst().type().kind() == TypeKind.TEXT))
                .orElseGet(() -> new UndefinedByData(false));
    }

    private static Result index(TransferValue current, PathIndex index, boolean textType) {
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

    private static Result value(TransferValue value, boolean textType) {
        if (value == TransferValue.Undefined.INSTANCE
                || textType && value instanceof TransferValue.Text text && ScalarRules.textLength(text.value()) == 0) {
            return new UndefinedByData(false);
        }
        return new Known(value);
    }

    private static Known bool(boolean value) {
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
