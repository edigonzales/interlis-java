package guru.interlis.validation;

import guru.interlis.model.SymbolId;
import guru.interlis.model.UnitSymbol;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.EvaluationResult.*;

final class UnitConversions {
    private static final NumericSemantics.Number ONE = new NumericSemantics.Number(BigDecimal.ONE);

    private UnitConversions() { }

    static EvaluationResult convert(EvaluationFrame frame, Optional<SymbolId> source, Optional<SymbolId> target,
                                    NumericSemantics.Number amount) {
        if (source.equals(target)) {
            return new KnownNumber(amount);
        }
        if (source.isEmpty() || target.isEmpty()) {
            return MissingContext.UNIT_CONVERSION;
        }
        var from = scale(frame, source.orElseThrow(), 0);
        var to = scale(frame, target.orElseThrow(), 0);
        if (from.isEmpty() || to.isEmpty()) {
            return MissingContext.UNIT_CONVERSION;
        }
        var lhs = from.orElseThrow();
        var rhs = to.orElseThrow();
        if (!lhs.dimensions().equals(rhs.dimensions())) {
            return Unsupported.INSTANCE;
        }
        var scaled = NumericSemantics.multiply(amount, lhs.factor());
        return NumericSemantics.divide(scaled, rhs.factor()).<EvaluationResult>map(KnownNumber::new)
                .orElse(Unsupported.INSTANCE);
    }

    private static Optional<Scale> scale(EvaluationFrame frame, SymbolId id, int depth) {
        frame.visit().run();
        if (depth > 128) {
            throw new ValidationLimitException("Unit conversion exceeds the depth limit");
        }
        var unit = (UnitSymbol) frame.plan().symbol(id).orElseThrow();
        if (unit.conversionFunction().isPresent()) {
            return Optional.empty();
        }
        if (unit.derivedFrom().isPresent()) {
            var target = scale(frame, unit.derivedFrom().orElseThrow(), depth + 1);
            if (target.isEmpty()) {
                return target;
            }
            var factor = target.orElseThrow().factor();
            for (var term : unit.conversionFactors()) {
                frame.visit().run();
                var number = term.kind() == UnitSymbol.ConstantKind.DECIMAL
                        ? new NumericSemantics.Number(term.decimal().orElseThrow().exactValue())
                        : NumericSemantics.constant(term.kind().name());
                factor = apply(factor, number, term.operator());
            }
            return Optional.of(new Scale(factor, target.orElseThrow().dimensions()));
        }
        if (unit.components().isEmpty()) {
            return Optional.of(new Scale(ONE, Map.of(frame.plan().canonical(id), 1)));
        }
        var dimensions = new HashMap<SymbolId, Integer>();
        var factor = ONE;
        for (var component : unit.components()) {
            var child = scale(frame, component.unit(), depth + 1);
            if (child.isEmpty()) {
                return child;
            }
            var member = child.orElseThrow();
            int sign = component.operator() == UnitSymbol.Operator.MULTIPLY ? 1 : -1;
            member.dimensions().forEach((key, power) -> dimensions.merge(key, Math.multiplyExact(sign, power), Math::addExact));
            factor = apply(factor, member.factor(), component.operator());
        }
        dimensions.values().removeIf(power -> power == 0);
        return Optional.of(new Scale(factor, Map.copyOf(dimensions)));
    }

    private static NumericSemantics.Number apply(NumericSemantics.Number left, NumericSemantics.Number right,
                                                 UnitSymbol.Operator operator) {
        return switch (operator) {
            case MULTIPLY -> NumericSemantics.multiply(left, right);
            case DIVIDE -> NumericSemantics.divide(left, right).orElseThrow();
        };
    }

    private record Scale(NumericSemantics.Number factor, Map<SymbolId, Integer> dimensions) { }
}
