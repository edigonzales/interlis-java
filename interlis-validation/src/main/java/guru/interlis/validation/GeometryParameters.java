package guru.interlis.validation;

import guru.interlis.model.CoordinateType;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.GeometryType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.SymbolId;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import java.util.function.Function;

final class GeometryParameters {
    private static final BigDecimal ROOT_TWO = BigDecimal.TWO.sqrt(MathContext.DECIMAL128);

    private GeometryParameters() {}

    static CoordinateType coordinate(ValidationPlan plan, ValidationSession.BasketContext basket, SymbolId id) {
        var symbol = plan.symbol(id).orElse(null);
        var type = symbol instanceof DomainSymbol domain && domain.generic()
                ? Optional.ofNullable(basket.domains().get(id)).flatMap(plan::domainType) : plan.domainType(id);
        return type.filter(CoordinateType.class::isInstance).map(CoordinateType.class::cast).orElse(null);
    }

    static Optional<BigDecimal> quantum(CoordinateType type) {
        if (type == null || type.dimensions() < 2) {
            return Optional.empty();
        }
        var x = quantum(type.axes().get(0).minimum(), type.axes().get(0).maximum());
        var y = quantum(type.axes().get(1).minimum(), type.axes().get(1).maximum());
        return x.filter(precision -> y.filter(other -> other.compareTo(precision) == 0).isPresent());
    }

    private static Optional<BigDecimal> quantum(Optional<DecimalLiteral> minimum, Optional<DecimalLiteral> maximum) {
        return minimum.flatMap(low -> maximum.map(high -> {
            int scale = Math.max(low.exactValue().scale(), high.exactValue().scale());
            return BigDecimal.ONE.scaleByPowerOfTen(-scale);
        }));
    }

    static Optional<BigDecimal> overlap(GeometryType type, Function<SymbolId, CoordinateType> coordinate) {
        return type.overlap().flatMap(source -> switch (source) {
            case GeometryType.Explicit explicit -> Optional.of(explicit.value().exactValue());
            case GeometryType.TrackWidth width -> quantum(coordinate.apply(width.coordinateDomain()))
                    .map(q -> q.divide(ROOT_TWO, MathContext.DECIMAL128));
        });
    }
}
