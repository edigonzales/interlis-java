package guru.interlis.model.internal;

import guru.interlis.model.SymbolId;
import guru.interlis.model.UnitSymbol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

/** Resolves the scale of declared units using the decimal evaluation profile. */
public final class UnitScales {
    public record Scale(BigDecimal factor, Map<SymbolId, BigInteger> dimensions) { }

    private UnitScales() { }

    public static Optional<Scale> resolve(SymbolId unit, Function<SymbolId, UnitSymbol> lookup,
                                          UnaryOperator<SymbolId> baseIdentity,
                                          IntConsumer visit, Consumer<BigDecimal> checkNumber) {
        return resolve(unit, lookup, baseIdentity, visit, checkNumber, 0);
    }

    private static Optional<Scale> resolve(SymbolId id, Function<SymbolId, UnitSymbol> lookup,
                                           UnaryOperator<SymbolId> baseIdentity,
                                           IntConsumer visit, Consumer<BigDecimal> checkNumber, int depth) {
        visit.accept(depth);
        var unit = lookup.apply(id);
        if (unit.conversionFunction().isPresent()) return Optional.empty();
        if (unit.derivedFrom().isPresent()) {
            var base = resolve(unit.derivedFrom().orElseThrow(), lookup, baseIdentity, visit, checkNumber, depth + 1);
            if (base.isEmpty()) return base;
            var scale = base.orElseThrow();
            var factor = scale.factor();
            for (var term : unit.conversionFactors()) {
                visit.accept(depth);
                var number = term.kind() == UnitSymbol.ConstantKind.DECIMAL
                        ? term.decimal().orElseThrow().exactValue()
                        : DecimalMath.constant(term.kind().name());
                factor = apply(factor, number, term.operator(), checkNumber);
            }
            return Optional.of(new Scale(factor, scale.dimensions()));
        }
        if (unit.components().isEmpty()) {
            return Optional.of(new Scale(BigDecimal.ONE, Map.of(baseIdentity.apply(id), BigInteger.ONE)));
        }
        var dimensions = new HashMap<SymbolId, BigInteger>();
        var factor = BigDecimal.ONE;
        for (var term : unit.components()) {
            var child = resolve(term.unit(), lookup, baseIdentity, visit, checkNumber, depth + 1);
            if (child.isEmpty()) return child;
            var value = child.orElseThrow();
            int sign = term.operator() == UnitSymbol.Operator.MULTIPLY ? 1 : -1;
            value.dimensions().forEach((key, power) ->
                    dimensions.merge(key, sign > 0 ? power : power.negate(), BigInteger::add));
            factor = apply(factor, value.factor(), term.operator(), checkNumber);
        }
        dimensions.values().removeIf(power -> power.signum() == 0);
        return Optional.of(new Scale(factor, Map.copyOf(dimensions)));
    }

    private static BigDecimal apply(BigDecimal left, BigDecimal right, UnitSymbol.Operator operator,
                                    Consumer<BigDecimal> checkNumber) {
        checkNumber.accept(right);
        var result = switch (operator) {
            case MULTIPLY -> left.multiply(right);
            case DIVIDE -> DecimalMath.divide(left, right).orElseThrow();
        };
        checkNumber.accept(result);
        return result;
    }
}
