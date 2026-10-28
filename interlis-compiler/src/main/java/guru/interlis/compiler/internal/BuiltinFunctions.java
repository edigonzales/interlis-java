package guru.interlis.compiler.internal;

import guru.interlis.model.LanguageVersion;

import java.util.List;
import java.util.Optional;

/**
 * Standard function signatures from the 2.3/2.4 reference manuals, function chapter.
 * Names are case-sensitive; dependent arguments use the explicit rule.
 */
final class BuiltinFunctions {
    enum Value { TEXT, MTEXT, NUMERIC, BOOLEAN, CLASS_VALUE, ANYSTRUCTURE, OBJECT, OBJECTS, BAG,
        ENUM, ENUM_TREE, POLYLINE, MULTIPOLYLINE, SURFACE, MULTISURFACE, ATTRIBUTE }

    enum Rule { SIMPLE, CONVERT_UNIT, AREAS_ATTRIBUTE, AREAS_TEXT }

    record Signature(String name, Value result, List<Value> arguments,
                     LanguageVersion since, Rule rule) {
        Signature {
            arguments = List.copyOf(arguments);
        }
    }

    private static Signature signature(String name, Value result, Value... args) {
        return new Signature(name, result, List.of(args), LanguageVersion.ILI23, Rule.SIMPLE);
    }

    private static final List<Signature> SIGNATURES = List.of(
            signature("len", Value.NUMERIC, Value.TEXT),
            signature("lenM", Value.NUMERIC, Value.MTEXT),
            signature("trim", Value.TEXT, Value.TEXT),
            signature("trimM", Value.MTEXT, Value.MTEXT),
            signature("objectCount", Value.NUMERIC, Value.OBJECTS),
            signature("elementCount", Value.NUMERIC, Value.BAG),
            signature("myClass", Value.CLASS_VALUE, Value.ANYSTRUCTURE),
            signature("isSubClass", Value.BOOLEAN, Value.CLASS_VALUE, Value.CLASS_VALUE),
            signature("isOfClass", Value.BOOLEAN, Value.ANYSTRUCTURE, Value.CLASS_VALUE),
            signature("isEnumSubVal", Value.BOOLEAN, Value.ENUM_TREE, Value.ENUM_TREE),
            signature("inEnumRange", Value.BOOLEAN, Value.ENUM, Value.ENUM_TREE, Value.ENUM_TREE),
            new Signature("convertUnit", Value.NUMERIC, List.of(Value.NUMERIC), LanguageVersion.ILI23, Rule.CONVERT_UNIT),
            new Signature("areAreas", Value.BOOLEAN, List.of(Value.OBJECTS, Value.ATTRIBUTE, Value.ATTRIBUTE), LanguageVersion.ILI23, Rule.AREAS_ATTRIBUTE),
            new Signature("length", Value.NUMERIC, List.of(Value.POLYLINE), LanguageVersion.ILI24, Rule.SIMPLE),
            new Signature("multilength", Value.NUMERIC, List.of(Value.MULTIPOLYLINE), LanguageVersion.ILI24, Rule.SIMPLE),
            new Signature("surface", Value.NUMERIC, List.of(Value.SURFACE), LanguageVersion.ILI24, Rule.SIMPLE),
            new Signature("multisurface", Value.NUMERIC, List.of(Value.MULTISURFACE), LanguageVersion.ILI24, Rule.SIMPLE),
            // The normative function chapter and its ALL example specify an object set.
            // Appendix A's areAreas3 spelling is retained as its documented set alias.
            new Signature("areAreas2", Value.BOOLEAN, List.of(Value.OBJECTS, Value.TEXT, Value.TEXT),
                    LanguageVersion.ILI24, Rule.AREAS_TEXT),
            new Signature("areAreas3", Value.BOOLEAN, List.of(Value.OBJECTS, Value.TEXT, Value.TEXT),
                    LanguageVersion.ILI24, Rule.AREAS_TEXT),
            new Signature("areAreasWithoutGaps", Value.BOOLEAN, List.of(Value.OBJECTS, Value.ATTRIBUTE, Value.ATTRIBUTE),
                    LanguageVersion.ILI24, Rule.AREAS_ATTRIBUTE),
            new Signature("areAreasWithoutGaps2", Value.BOOLEAN, List.of(Value.OBJECTS, Value.TEXT, Value.TEXT),
                    LanguageVersion.ILI24, Rule.AREAS_TEXT));

    static List<String> parameterNames(String name) {
        return switch(name) {
            case "len","lenM","trim","trimM" -> List.of("TextVal");
            case "objectCount" -> List.of("Objects");
            case "elementCount" -> List.of("bag");
            case "myClass" -> List.of("Object");
            case "isSubClass" -> List.of("potSubClass","potSuperClass");
            case "isOfClass" -> List.of("Object","Class");
            case "isEnumSubVal" -> List.of("SubVal","NodeVal");
            case "inEnumRange" -> List.of("Enum","MinVal","MaxVal");
            case "convertUnit" -> List.of("from");
            case "length","multilength","surface","multisurface" -> List.of("geom");
            default -> List.of("Objects","SurfaceBag","SurfaceAttr");
        };
    }

    static Optional<Signature> find(String name) {
        return SIGNATURES.stream().filter(signature -> signature.name().equals(name)).findFirst();
    }

    private BuiltinFunctions() { }
}
