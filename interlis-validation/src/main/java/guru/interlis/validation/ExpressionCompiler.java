package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.BinaryExpression;
import guru.interlis.model.BinaryOperator;
import guru.interlis.model.ConstantExpression;
import guru.interlis.model.DefinedExpression;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.ExpressionTypeState;
import guru.interlis.model.FunctionCallExpression;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.LiteralExpression;
import guru.interlis.model.NumericType;
import guru.interlis.model.ObjectArgumentType;
import guru.interlis.model.ObjectSetExpression;
import guru.interlis.model.Origin;
import guru.interlis.model.PathExpression;
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
import guru.interlis.transfer.TransferValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import guru.interlis.validation.EvaluationResult.*;
import guru.interlis.validation.ExpressionEvaluator.Comparison;
import guru.interlis.validation.ExpressionEvaluator.Family;

import static guru.interlis.validation.ExpressionEvaluator.bool;
import static guru.interlis.validation.ExpressionEvaluator.value;

final class ExpressionCompiler {
    private static final int MAX_DEPTH = 128;
    private static final int MAX_NODES = 16_384;

    private ExpressionCompiler() { }

    static Optional<ExpressionProgram> compile(SemanticGraph graph, TypeRelations types, ExpressionId root) {
        return compile(graph, types, root, Optional.empty());
    }

    static Optional<ExpressionProgram> compileSet(SemanticGraph graph, TypeRelations types, ExpressionId root, SymbolId extent) {
        return compile(graph, types, root, Optional.of(extent));
    }

    private static Optional<ExpressionProgram> compile(SemanticGraph graph, TypeRelations types, ExpressionId root,
                                             Optional<SymbolId> extent) {
        var compiler = new Compiler(graph, types, extent);
        return compiler.visit(root, 0)
                ? Optional.of(new ExpressionProgram(graph.snapshotId(), root, compiler.nodes, compiler.constants, compiler.comparisons,
                        new ExpressionRequirements(compiler.populationRequired)))
                : Optional.empty();
    }

    private static final class Compiler {
        private final SemanticGraph graph;
        private final TypeRelations types;
        private final Optional<SymbolId> extent;
        private final Map<ExpressionId, TypedExpression> nodes = new LinkedHashMap<>();
        private final Map<ExpressionId, EvaluationResult> constants = new LinkedHashMap<>();
        private final Map<ExpressionId, Comparison> comparisons = new LinkedHashMap<>();

        private final Map<ExpressionId, Integer> heights = new LinkedHashMap<>();
        private boolean populationRequired;

        private Compiler(SemanticGraph graph, TypeRelations types, Optional<SymbolId> extent) {
            this.graph = graph;
            this.types = types;
            this.extent = extent;
        }

        boolean visit(ExpressionId id, int depth) {
            var knownHeight = heights.get(id);
            if (depth + (knownHeight == null ? 0 : knownHeight) > MAX_DEPTH
                    || knownHeight == null && nodes.size() >= MAX_NODES) {
                throw new ValidationLimitException("Expression exceeds the validation plan budget");
            }
            if (knownHeight != null) {
                return true;
            }
            var expression = expression(id);
            int height = 0;
            for (var child : expression.children()) {
                if (!visit(child, depth + 1)) {
                    return false;
                }
                height = Math.max(height, 1 + heights.get(child));
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
                case ObjectSetExpression set -> objectSet(set);
                case FunctionCallExpression call -> {
                    boolean supportedCall = objectCount(call);
                    populationRequired |= supportedCall;
                    yield supportedCall;
                }
                case DefinedExpression _ -> true;
                case UnaryExpression unary -> switch (unary.operator()) {
                    case NOT -> family(expression(unary.operand())) == Family.BOOLEAN;
                    case NEGATE -> family(expression(unary.operand())) == Family.NUMBER;
                };
                case BinaryExpression binary -> binary(binary);
                default -> false;
            };
            if (supported) {
                if (nodes.size() >= MAX_NODES) {
                    throw new ValidationLimitException("Expression exceeds the validation plan budget");
                }
                nodes.put(id, expression);
                heights.put(id, height);
            }
            return supported;
        }

        private boolean objectSet(ObjectSetExpression set) {
            return extent.isPresent() && effective(set.typeId()).orElse(null) instanceof ObjectArgumentType argument
                    && argument.objects() && argument.base().equals(extent) && argument.restrictions().isEmpty();
        }

        private boolean objectCount(FunctionCallExpression call) {
            return extent.isPresent() && call.children().size() == 1
                    && expression(call.children().getFirst()) instanceof ObjectSetExpression
                    && call.targetFunction().flatMap(graph::symbol).orElse(null) instanceof FunctionSymbol function
                    && function.origin() instanceof Origin.PredefinedOrigin predefined
                    && predefined.key().equals("INTERLIS.objectCount");
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

    private static EvaluationResult literal(LiteralExpression expression) {
        return switch (expression.literalKind()) {
            case TEXT -> value(new TransferValue.Text(expression.textValue().orElseThrow()), true);
            case NUMBER -> {
                var number = expression.decimal().orElseThrow();
                yield new Known(new TransferValue.NumberValue(number.lexeme(), number.exactValue()));
            }
        };
    }

}
