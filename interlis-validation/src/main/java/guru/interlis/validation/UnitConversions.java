package guru.interlis.validation;

import guru.interlis.model.SymbolId;
import guru.interlis.model.UnitSymbol;

import guru.interlis.model.internal.UnitScales;

import java.util.Optional;

import static guru.interlis.validation.EvaluationResult.*;

final class UnitConversions {
    private UnitConversions() { }

    static EvaluationResult convert(EvaluationFrame frame, Optional<SymbolId> source, Optional<SymbolId> target,
                                    NumericSemantics.Number amount) {
        if (source.equals(target)) {
            return new KnownNumber(amount);
        }
        if (source.isEmpty() || target.isEmpty()) {
            return MissingContext.UNIT_CONVERSION;
        }
        var from = scale(frame, source.orElseThrow());
        var to = scale(frame, target.orElseThrow());
        if (from.isEmpty() || to.isEmpty()) {
            return MissingContext.UNIT_CONVERSION;
        }
        var lhs = from.orElseThrow();
        var rhs = to.orElseThrow();
        if (!lhs.dimensions().equals(rhs.dimensions())) {
            return Unsupported.INSTANCE;
        }
        var scaled = NumericSemantics.multiply(amount, new NumericSemantics.Number(lhs.factor()));
        return NumericSemantics.divide(scaled, new NumericSemantics.Number(rhs.factor())).<EvaluationResult>map(KnownNumber::new)
                .orElse(Unsupported.INSTANCE);
    }

    private static Optional<UnitScales.Scale> scale(EvaluationFrame frame, SymbolId unit) {
        return UnitScales.resolve(unit, id -> (UnitSymbol) frame.plan().symbol(id).orElseThrow(),
                frame.plan()::canonical, depth -> {
                    frame.visit().run();
                    if (depth > 128) throw new ValidationLimitException("Unit conversion exceeds the depth limit");
                }, ScalarRules::requireNumericBudget);
    }
}
