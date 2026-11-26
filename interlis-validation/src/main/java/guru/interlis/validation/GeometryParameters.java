package guru.interlis.validation;

import guru.interlis.model.CoordinateType;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolId;
import guru.interlis.transfer.TransferGeometry;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import java.util.function.Function;

final class GeometryParameters {
    private static final BigDecimal ROOT_TWO = new BigDecimal("1.414213562373095048801688724209698");

    private GeometryParameters() {}

    static CoordinateType coordinate(ValidationPlan plan, ValidationSession.BasketContext basket, SymbolId id) {
        return coordinate(plan, basket.domains(), id);
    }

    static CoordinateType coordinate(ValidationPlan plan, java.util.Map<SymbolId, SymbolId> domains, SymbolId id) {
        var symbol = plan.symbol(id).orElse(null);
        var type = symbol instanceof DomainSymbol domain && domain.generic()
                ? Optional.ofNullable(domains.get(id)).flatMap(plan::domainType) : plan.domainType(id);
        return type.filter(CoordinateType.class::isInstance).map(CoordinateType.class::cast).orElse(null);
    }

    private static Optional<BigDecimal> quantum(CoordinateType type) {
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

    static GeometryEngine.Input input(TransferGeometry value, GeometryType type, DataLocation location,
                                      LanguageVersion language, Function<SymbolId, CoordinateType> coordinate,
                                      boolean partial) {
        var vertexDomain = type.vertexDomain();
        var vertex = vertexDomain.map(coordinate);
        var implicitQuantum = type.overlap().filter(GeometryType.TrackWidth.class::isInstance)
                .map(GeometryType.TrackWidth.class::cast)
                .map(GeometryType.TrackWidth::coordinateDomain)
                .flatMap(domain -> vertexDomain.filter(domain::equals).isPresent()
                        ? quantum(vertex.orElse(null)) : quantum(coordinate.apply(domain)));
        var overlap = type.overlap().filter(GeometryType.Explicit.class::isInstance)
                .map(GeometryType.Explicit.class::cast).map(explicit -> explicit.value().exactValue())
                .or(() -> implicitQuantum.map(q -> q.divide(ROOT_TWO, MathContext.DECIMAL128)));
        return new GeometryEngine.Input(value, type, location, language, quantum(vertex.orElse(null)), overlap,
                partial, vertex.map(CoordinateType::id), implicitQuantum);
    }
}
