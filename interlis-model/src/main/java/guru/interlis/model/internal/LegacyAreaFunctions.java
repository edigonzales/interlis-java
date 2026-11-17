package guru.interlis.model.internal;

import guru.interlis.model.BooleanType;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ModelKind;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.ObjectArgumentType;
import guru.interlis.model.Symbol;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;

import java.util.function.Function;

/** Identity and signature of the model-declared INTERLIS 2.3 extension. */
public final class LegacyAreaFunctions {
    private LegacyAreaFunctions() { }

    public static boolean isModel(String name, ModelKind kind, LanguageVersion language, String issuer) {
        return kind == ModelKind.TYPE && language == LanguageVersion.ILI23
                && name.equals("INTERLIS_ext") && issuer.equals("http://www.interlis.ch/models");
    }

    public static boolean matches(FunctionSymbol function, Symbol owner, Function<TypeId, TypeDescriptor> effectiveType) {
        return owner instanceof ModelSymbol model
                && isModel(model.name().value(), model.modelKind(), model.languageVersion(), model.issuer())
                && matchesSignature(function, effectiveType);
    }

    public static boolean matchesSignature(FunctionSymbol function, Function<TypeId, TypeDescriptor> effectiveType) {
        var name = function.name().value();
        var parameters = function.parameters();
        return (name.equals("areAreas2") || name.equals("areAreas3")) && parameters.size() == 3
                && !function.returnMandatory()
                && parameters.stream().noneMatch(parameter -> parameter.mandatory())
                && effectiveType.apply(parameters.getFirst().typeId()) instanceof ObjectArgumentType objects
                && objects.objects() == name.equals("areAreas3") && objects.base().isEmpty() && objects.restrictions().isEmpty()
                && parameters.subList(1, 3).stream().allMatch(parameter ->
                        effectiveType.apply(parameter.typeId()) instanceof TextType text
                                && text.textKind() == TextKind.TEXT && text.maxLength().isEmpty())
                && effectiveType.apply(function.returnType()) instanceof BooleanType;
    }
}
