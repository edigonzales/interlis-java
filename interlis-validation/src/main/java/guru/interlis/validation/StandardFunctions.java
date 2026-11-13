package guru.interlis.validation;

import guru.interlis.model.ClassSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.Origin;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.TextLengths;
import guru.interlis.transfer.TransferValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static guru.interlis.validation.EvaluationResult.*;

enum StandardFunctions {
    LENGTH, TRIM, OBJECT_COUNT, ELEMENT_COUNT, MY_CLASS, IS_SUB_CLASS, IS_OF_CLASS,
    IS_ENUM_SUB_VALUE, IN_ENUM_RANGE, CONVERT_UNIT;

    static Optional<StandardFunctions> bind(FunctionSymbol symbol) {
        if (!(symbol.origin() instanceof Origin.PredefinedOrigin predefined)) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (predefined.key()) {
            case "INTERLIS.len", "INTERLIS.lenM" -> LENGTH;
            case "INTERLIS.trim", "INTERLIS.trimM" -> TRIM;
            case "INTERLIS.objectCount" -> OBJECT_COUNT;
            case "INTERLIS.elementCount" -> ELEMENT_COUNT;
            case "INTERLIS.myClass" -> MY_CLASS;
            case "INTERLIS.isSubClass" -> IS_SUB_CLASS;
            case "INTERLIS.isOfClass" -> IS_OF_CLASS;
            case "INTERLIS.isEnumSubVal" -> IS_ENUM_SUB_VALUE;
            case "INTERLIS.inEnumRange" -> IN_ENUM_RANGE;
            case "INTERLIS.convertUnit" -> CONVERT_UNIT;
            default -> null;
        });
    }

    EvaluationResult evaluate(List<TransferValue> arguments, EvaluationFrame frame, EnumerationType enumeration) {
        var first = arguments.getFirst();
        if (!argumentsMatch(arguments)) {
            return Unsupported.INSTANCE;
        }
        return switch (this) {
            case LENGTH -> count(TextLengths.canonicalLength(((TransferValue.Text) first).value()));
            case TRIM -> ExpressionEvaluator.value(new TransferValue.Text(trim(((TransferValue.Text) first).value())), true);
            case ELEMENT_COUNT -> count(((TransferValue.Repeated) first).values().size());
            case MY_CLASS -> new Known(new TransferValue.ClassName(
                    frame.plan().qualifiedName(((TransferValue.Structured) first).type().symbol())));
            case IS_SUB_CLASS -> ExpressionEvaluator.bool(subclass(frame.plan(),
                    classId(frame.plan(), first), classId(frame.plan(), arguments.get(1))));
            case IS_OF_CLASS -> ExpressionEvaluator.bool(subclass(frame.plan(),
                    ((TransferValue.Structured) first).type().symbol(), classId(frame.plan(), arguments.get(1))));
            case IS_ENUM_SUB_VALUE -> {
                var child = path(first);
                var parent = path(arguments.get(1));
                yield ExpressionEvaluator.bool(child.size() > parent.size() && under(child, parent));
            }
            case IN_ENUM_RANGE -> inRange(enumeration, path(first), path(arguments.get(1)), path(arguments.get(2)));
            case OBJECT_COUNT, CONVERT_UNIT -> throw new IllegalStateException("Function requires bound expression arguments");
        };
    }

    private boolean argumentsMatch(List<TransferValue> values) {
        var first = values.getFirst();
        return switch (this) {
            case LENGTH, TRIM -> first instanceof TransferValue.Text;
            case ELEMENT_COUNT -> first instanceof TransferValue.Repeated;
            case MY_CLASS -> first instanceof TransferValue.Structured;
            case IS_OF_CLASS -> first instanceof TransferValue.Structured
                    && values.get(1) instanceof TransferValue.ClassName;
            case IS_SUB_CLASS -> first instanceof TransferValue.ClassName
                    && values.get(1) instanceof TransferValue.ClassName;
            case IS_ENUM_SUB_VALUE, IN_ENUM_RANGE -> values.stream().allMatch(TransferValue.Enumeration.class::isInstance);
            case OBJECT_COUNT, CONVERT_UNIT -> false;
        };
    }

    static Known count(long count) {
        return new Known(new TransferValue.NumberValue(Long.toString(count), BigDecimal.valueOf(count)));
    }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(start, end);
    }

    private static SymbolId classId(ValidationPlan plan, TransferValue value) {
        QualifiedName name = ((TransferValue.ClassName) value).name();
        return plan.symbols(name).stream().filter(id -> plan.symbol(id)
                .filter(symbol -> symbol instanceof ClassSymbol || symbol instanceof StructureSymbol).isPresent())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown class value " + name));
    }

    private static boolean subclass(ValidationPlan plan, SymbolId child, SymbolId parent) {
        var object = plan.object(new guru.interlis.model.SymbolRef(plan.snapshotId(), child));
        if (object != null) {
            return ModelValueRules.specializes(plan, object.descriptor(), List.of(parent));
        }
        for (var current = Optional.of(child); current.isPresent();) {
            var id = current.orElseThrow();
            if (plan.canonical(id).equals(plan.canonical(parent))) {
                return true;
            }
            current = plan.symbol(id).flatMap(symbol -> switch (symbol) {
                case ClassSymbol type -> type.baseType();
                case StructureSymbol type -> type.baseType();
                default -> Optional.empty();
            });
        }
        return false;
    }

    private static List<String> path(TransferValue value) {
        return ((TransferValue.Enumeration) value).path();
    }

    private static boolean under(List<String> candidate, List<String> ancestor) {
        return candidate.size() >= ancestor.size() && candidate.subList(0, ancestor.size()).equals(ancestor);
    }

    private static EvaluationResult inRange(EnumerationType enumeration, List<String> value,
                                            List<String> lower, List<String> upper) {
        if (enumeration == null || !enumeration.ordered()) {
            return ExpressionEvaluator.bool(false);
        }
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        Integer current = null;
        for (var element : enumeration.values()) {
            var path = List.of(element.path().split("\\."));
            if (under(path, lower)) {
                first = Math.min(first, element.order());
            }
            if (under(path, upper)) {
                last = Math.max(last, element.order());
            }
            if (path.equals(value)) {
                current = element.order();
            }
        }
        return ExpressionEvaluator.bool(current != null && current >= first && current <= last);
    }
}
