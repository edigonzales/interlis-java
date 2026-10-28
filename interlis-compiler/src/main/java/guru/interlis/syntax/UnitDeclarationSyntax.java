package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import guru.interlis.model.UnitSymbol;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured unit syntax. The declaration name/range denote the binding short name;
 * designation preserves the documentary full name. Components retain source order
 * and exact UTF-16 name ranges. Numerical conversion factors preserve operators,
 * exact decimal values or unrounded named constants, and their literal ranges.
 * An empty factor list with a conversion target and no FUNCTION means implicit one.
 * All values are immutable and null-free.
 */
public record UnitDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
        Optional<SourceRange> nameRange, String designation, boolean abstractUnit,
        Optional<NameSyntax> base, Optional<NameSyntax> derivedFrom,
        List<Component> components, List<UnitSymbol.ConversionFactor> conversionFactors,
        Optional<String> conversionFunction, String definition) implements DeclarationSyntax {
    /** Creates immutable syntax. */
    public UnitDeclarationSyntax {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(designation, "designation"); Objects.requireNonNull(base, "base");
        Objects.requireNonNull(derivedFrom, "derivedFrom"); components = List.copyOf(components);
        conversionFactors = List.copyOf(conversionFactors);
        conversionFunction = Objects.requireNonNull(conversionFunction, "conversionFunction");
        Objects.requireNonNull(definition, "definition");
        if (!components.isEmpty() && components.getFirst().operator() != UnitSymbol.Operator.MULTIPLY)
            throw new IllegalArgumentException("first unit component must multiply");
        if (derivedFrom.isPresent() && !components.isEmpty()) throw new IllegalArgumentException("mixed unit definition");
        if ((!conversionFactors.isEmpty() || conversionFunction.isPresent()) && derivedFrom.isEmpty())
            throw new IllegalArgumentException("conversion requires a target unit");
        if (!conversionFactors.isEmpty() && conversionFactors.getFirst().operator() != UnitSymbol.Operator.MULTIPLY)
            throw new IllegalArgumentException("first conversion factor must multiply");
        if (!conversionFactors.isEmpty() && conversionFunction.isPresent())
            throw new IllegalArgumentException("mixed numerical and functional conversion");
    }
    /** One named component with its preceding composition operator. */
    public record Component(UnitSymbol.Operator operator, NameSyntax unit) {
        /** Creates a null-free component. */
        public Component { Objects.requireNonNull(operator, "operator"); Objects.requireNonNull(unit, "unit"); }
    }
    @Override public SyntaxKind kind() { return SyntaxKind.UNIT; }
    @Override public List<SyntaxNode> children() {
        var children = new ArrayList<SyntaxNode>();
        base.ifPresent(children::add); derivedFrom.ifPresent(children::add);
        components.forEach(c -> children.add(c.unit())); return List.copyOf(children);
    }
}
