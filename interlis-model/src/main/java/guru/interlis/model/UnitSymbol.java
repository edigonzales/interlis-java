package guru.interlis.model;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Unit with a binding name (short name), documentary designation and bound definition.
 * All values are immutable and null-free; referenced IDs belong to the same snapshot.
 * Definition text preserves conversion notation for display. Composed semantics use
 * components; numerical conversion semantics use exact, ordered conversion factors.
 *
 * @param header header whose name is the short name used in references
 * @param designation documentary full name
 * @param abstractUnit whether the unit is abstract
 * @param base explicitly declared abstract base, or an implicit builtin base
 * @param derivedFrom conversion target of a derived unit, separate from EXTENDS;
 *        empty for an unresolved target in a partial analysis with diagnostics
 * @param components composed factors in source order; empty for base/derived units
 * @param conversionFactors numerical conversion factors in source order; an empty
 *        list on a derived unit without a function means the implicit factor one
 * @param conversionFunction optional explanation of a symbolic FUNCTION conversion;
 *        never together with numerical factors; retained with an unresolved target
 *        in a partial analysis
 * @param definition original definition text for display and symbolic conversions
 */
public record UnitSymbol(SymbolHeader header, String designation, boolean abstractUnit,
                         Optional<SymbolId> base, Optional<SymbolId> derivedFrom,
                         List<Component> components, List<ConversionFactor> conversionFactors,
                         Optional<String> conversionFunction, Optional<String> definition) implements Symbol {
    /** Creates an immutable unit. */
    public UnitSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(designation, "designation");
        base = Objects.requireNonNull(base, "base");
        derivedFrom = Objects.requireNonNull(derivedFrom, "derivedFrom");
        components = List.copyOf(components);
        conversionFactors = List.copyOf(conversionFactors);
        conversionFunction = Objects.requireNonNull(conversionFunction, "conversionFunction");
        definition = Objects.requireNonNull(definition, "definition");
        if (header.kind() != SymbolKind.UNIT) throw new IllegalArgumentException("UnitSymbol needs UNIT header");
        if (!components.isEmpty() && components.getFirst().operator() != Operator.MULTIPLY)
            throw new IllegalArgumentException("first unit component must multiply");
        if (derivedFrom.isPresent() && !components.isEmpty()) throw new IllegalArgumentException("mixed unit definition");
        if (!conversionFactors.isEmpty() && conversionFactors.getFirst().operator() != Operator.MULTIPLY)
            throw new IllegalArgumentException("first conversion factor must multiply");
        if (!components.isEmpty() && (!conversionFactors.isEmpty() || conversionFunction.isPresent()))
            throw new IllegalArgumentException("mixed composed and converted unit definition");
        if (!conversionFactors.isEmpty() && conversionFunction.isPresent())
            throw new IllegalArgumentException("mixed numerical and functional conversion");
    }
    /** Composition operator; the first component always multiplies. */
    public enum Operator { MULTIPLY, DIVIDE }
    /** Exact decimal or a named, unrounded language constant. */
    public enum ConstantKind { DECIMAL, PI, LNBASE }
    /**
     * One numerical conversion term, retaining its preceding operator and literal
     * UTF-16 range including source revision. The first term always multiplies.
     * Only DECIMAL has a decimal value; PI and LNBASE remain symbolic. Zero values
     * are retained here so semantic validation can report a source diagnostic.
     */
    public record ConversionFactor(Operator operator, ConstantKind kind,
                                   Optional<DecimalLiteral> decimal, SourceRange range) {
        /** Creates a null-free immutable conversion term. */
        public ConversionFactor {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(kind, "kind");
            decimal = Objects.requireNonNull(decimal, "decimal");
            Objects.requireNonNull(range, "range");
            if ((kind == ConstantKind.DECIMAL) != decimal.isPresent())
                throw new IllegalArgumentException("only a decimal conversion factor has a decimal value");
        }
    }
    /** Bound factor in a composition. No algebraic reordering is applied. */
    public record Component(Operator operator, SymbolId unit) {
        /** Creates a null-free component. */
        public Component { Objects.requireNonNull(operator, "operator"); Objects.requireNonNull(unit, "unit"); }
    }
}
