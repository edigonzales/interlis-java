package guru.interlis.validation;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.NumericType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.TransferValue;

import static guru.interlis.validation.EvaluationResult.UndefinedByData;

final class DerivedValueResolver {
    private DerivedValueResolver() { }

    static EvaluationResult resolve(AttributeSymbol attribute, EvaluationFrame frame) {
        if (frame.evaluating(attribute.id())) {
            return EvaluationResult.MissingContext.DERIVATION_CYCLE;
        }
        if (frame.activeDerivations().size() >= 128) {
            throw new ValidationLimitException("Attribute derivation exceeds the evaluation depth limit");
        }
        return select(attribute, frame.enterDerivation(attribute.id()), 0);
    }

    static boolean matches(EvaluationResult expected, TransferValue supplied, TypeDescriptor type) {
        if (expected instanceof UndefinedByData) {
            return supplied == TransferValue.Undefined.INSTANCE;
        }
        var number = switch (expected) {
            case EvaluationResult.KnownNumber value -> value.value();
            case EvaluationResult.Known known when known.value() instanceof TransferValue.NumberValue value ->
                    NumericSemantics.Number.exact(value.exactValue());
            default -> null;
        };
        if (number != null && supplied instanceof TransferValue.NumberValue delivered && type instanceof NumericType numeric) {
            var declared = numeric.range().minimum().filter(bound ->
                    bound.precision().origin() == guru.interlis.model.PrecisionOrigin.LEXICAL);
            if (declared.isPresent()) {
                var precision = declared.orElseThrow().precision();
                return NumericSemantics.matchesRounded(number, delivered.exactValue(),
                        Math.subtractExact(precision.fractionalDigits(), precision.exponent()));
            }
            return NumericSemantics.compare(guru.interlis.model.BinaryOperator.EQ,
                    number, NumericSemantics.Number.exact(delivered.exactValue()));
        }
        return expected instanceof EvaluationResult.Known known && known.value().equals(supplied);
    }

    private static EvaluationResult select(AttributeSymbol attribute, EvaluationFrame frame, int depth) {
        frame.visit().run();
        if (depth > 128) {
            throw new ValidationLimitException("Attribute override chain exceeds the evaluation depth limit");
        }
        var alternatives = attribute.derivations();
        for (int index = alternatives.size() - 1; index >= 0; index--) {
            var program = frame.plan().datasetExpression(alternatives.get(index));
            if (program.isEmpty()) {
                return EvaluationResult.Unsupported.INSTANCE;
            }
            var candidate = program.orElseThrow().evaluate(frame);
            if (!(candidate instanceof UndefinedByData)) {
                return candidate;
            }
        }
        return attribute.overrides().flatMap(frame.plan()::symbol).filter(AttributeSymbol.class::isInstance)
                .map(AttributeSymbol.class::cast).map(base -> select(base, frame, depth + 1))
                .orElseGet(() -> new UndefinedByData(false));
    }
}
