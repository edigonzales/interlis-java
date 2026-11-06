package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.DeclaredName;
import guru.interlis.model.Documentation;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TypeId;
import guru.interlis.model.UnitSymbol;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.GenericTypeSyntax;
import guru.interlis.syntax.UnitDeclarationSyntax;
import guru.interlis.syntax.NumericTypeSyntax;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Builds and checks the bound unit graph and numeric unit requirements. */
final class UnitBindings {
    private final GraphBuilder g;
    private final SemanticBuildState state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;

    UnitBindings(GraphBuilder graph, SemanticBuildState state, SemanticWork work,
                   IndexedDeclarations index, SemanticDiagnostics diagnostics) {
        this.g = graph;
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
    }

    UnitSymbol buildUnit(DeclInfo decl) {
        UnitDeclarationSyntax syntax = (UnitDeclarationSyntax) decl.syntax();
        Optional<SymbolId> base = syntax.base().flatMap(name -> bindUnit(decl, name));
        Optional<SymbolId> derived = syntax.derivedFrom().flatMap(name -> bindUnit(decl, name));
        List<UnitSymbol.Component> components = new ArrayList<>();
        for (var component : syntax.components()) {
            work.checkpoint();
            bindUnit(decl, component.unit()).ifPresent(id -> components.add(new UnitSymbol.Component(component.operator(), id)));
        }
        return new UnitSymbol(g.declarations.header(decl), syntax.designation(), syntax.abstractUnit(), base, derived,
                components, syntax.conversionFactors(), syntax.conversionFunction(), syntax.definition().isBlank() ? Optional.empty() : Optional.of(syntax.definition()));
    }

    Optional<SymbolId> bindUnit(DeclInfo decl, guru.interlis.syntax.NameSyntax name) {
        return bindUnit(decl,name,new ArrayList<>());
    }

    Optional<SymbolId> bindUnit(DeclInfo decl, guru.interlis.syntax.NameSyntax name, List<DiagnosticId> errors) {
        return g.names.bindTarget(decl,name,Set.of(SymbolKind.UNIT),ReferenceUsage.TYPE_USE,errors,false);
    }

    Optional<SymbolId> builtinUnit(SymbolId context, String name) {
        if (!name.startsWith("INTERLIS.")) return Optional.empty();
        var timeUnit = g.temporal.find(index.byId(context).orElseThrow(), name, Set.of(SymbolKind.UNIT));
        if (timeUnit.isPresent()) return timeUnit;
        String shortName = name.substring("INTERLIS.".length());
        String parent = switch (shortName) {
            case "ANYUNIT" -> "";
            case "DIMENSIONLESS", "LENGTH", "MASS", "TIME", "ELECTRIC_CURRENT", "TEMPERATURE",
                    "AMOUNT_OF_MATTER", "ANGLE", "SOLID_ANGLE", "LUMINOUS_INTENSITY", "MONEY" -> "ANYUNIT";
            case "m" -> "LENGTH"; case "kg" -> "MASS"; case "s" -> "TIME";
            case "A" -> "ELECTRIC_CURRENT"; case "K" -> "TEMPERATURE"; case "mol" -> "AMOUNT_OF_MATTER";
            case "rad" -> "ANGLE"; case "sr" -> "SOLID_ANGLE"; case "cd" -> "LUMINOUS_INTENSITY";
            default -> null;
        };
        if (parent == null) return Optional.empty();
        var language = index.byId(context).orElseThrow().language();
        SymbolId id = new SymbolId("predefined:" + language + ":unit:" + name);
        if (state.hasSymbol(id)) return Optional.of(id);
        Optional<SymbolId> base = parent.isEmpty() ? Optional.empty() : builtinUnit(context, "INTERLIS." + parent);
        boolean abstractUnit = parent.isEmpty() || parent.equals("ANYUNIT");
        String designation = switch (shortName) {
            case "m" -> "METER"; case "kg" -> language==guru.interlis.model.LanguageVersion.ILI23?"KILOGRAMM":"KILOGRAM"; case "s" -> "SECOND";
            case "A" -> "AMPERE"; case "K" -> "DEGREE_KELVIN"; case "mol" -> "MOLE";
            case "rad" -> "RADIAN"; case "sr" -> "STERADIAN"; case "cd" -> "CANDELA";
            default -> shortName;
        };
        Origin origin = new Origin.PredefinedOrigin(language, name);
        state.defineSymbol(new UnitSymbol(new SymbolHeader(id, SymbolKind.UNIT, DeclaredName.synthetic(name),
                Optional.empty(), 0, origin, Optional.empty(), language,
                Documentation.empty(), MetaAttributeSet.empty(), abstractUnit
                    ? Set.of(guru.interlis.model.Modifier.ABSTRACT) : Set.of(), List.of()),
                designation, abstractUnit, base, Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty()));
        return Optional.of(id);
    }

    /** Validate bound dependencies after all declarations exist; file order is immaterial. */
    void validateUnits() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            if (!(symbol instanceof UnitSymbol unit)) continue;
            DeclInfo decl = index.byId(unit.id()).orElse(null);
            if (decl == null) continue;
            work.checkpoint();
            var syntax = (UnitDeclarationSyntax) decl.syntax();
            for (var factor : unit.conversionFactors()) {
                work.checkpoint();
                if (factor.decimal().filter(value -> value.exactValue().signum() == 0).isPresent()) {
                    boolean divide = factor.operator() == UnitSymbol.Operator.DIVIDE;
                    diagnostics.emitRule(decl, divide ? "unit conversion divides by zero" : "unit conversion multiplies by zero",
                            divide ? "UNIT_CONVERSION_ZERO_DIVISOR" : "UNIT_CONVERSION_ZERO_FACTOR", factor.range());
                }
            }
            if (index.children(decl.owner().orElseThrow()).stream()
                    .anyMatch(other -> !other.id().equals(decl.id()) && other.name().equals(decl.name())))
                diagnostics.emitRule(decl, "unit short name is not unique in its scope", "UNIT_NAME_UNIQUE");
            var pending = new java.util.ArrayDeque<SymbolId>(unitDependencies(unit));
            Set<SymbolId> seen = new HashSet<>();
            while (!pending.isEmpty()) {
                work.checkpoint();
                SymbolId next = pending.removeFirst();
                if (next.equals(unit.id())) {
                    diagnostics.emitRule(decl, "cyclic unit dependency", "UNIT_CYCLE"); break;
                }
                if (seen.add(next) && state.symbol(next) instanceof UnitSymbol dependency)
                    pending.addAll(unitDependencies(dependency));
            }
            for (var component : unit.components()) {
                UnitSymbol target = (UnitSymbol) state.symbol(component.unit());
                if (target.abstractUnit() != unit.abstractUnit())
                    diagnostics.emitRule(decl, "composed unit and all components must agree on abstraction", "UNIT_COMPONENT_ABSTRACT");
            }
            if (unit.derivedFrom().isPresent()) {
                UnitSymbol target = (UnitSymbol) state.symbol(unit.derivedFrom().orElseThrow());
                if (unit.abstractUnit() || target.abstractUnit())
                    diagnostics.emitRule(decl, "derived units require a concrete conversion target and are concrete", "UNIT_DERIVED_ABSTRACT");
            }
            UnitSymbol base = unit.base().map(state::symbol).filter(UnitSymbol.class::isInstance).map(UnitSymbol.class::cast).orElse(null);
            if (base == null) continue;
            if (!base.abstractUnit()) diagnostics.emitRule(decl, "only abstract units can be extended", "UNIT_BASE_ABSTRACT");
            if (!syntax.components().isEmpty()) {
                boolean compatible = !base.components().isEmpty() && base.components().size() == unit.components().size();
                if (compatible) for (int i = 0; i < unit.components().size(); i++) {
                    var actual = unit.components().get(i); var expected = base.components().get(i);
                    if (actual.operator() != expected.operator() || !unitExtends(actual.unit(), expected.unit())) compatible = false;
                }
                if (!compatible) diagnostics.emitRule(decl, "composition must specialize the base component by component", "UNIT_COMPOSITION_EXTENSION");
            } else if (unit.derivedFrom().isPresent() && !unitExtends(unit.derivedFrom().orElseThrow(), base.id())) {
                diagnostics.emitRule(decl, "conversion target must specialize the declared abstract base", "UNIT_DERIVED_EXTENSION");
            }
        }
    }

    private List<SymbolId> unitDependencies(UnitSymbol unit) {
        List<SymbolId> result = new ArrayList<>();
        unit.base().ifPresent(result::add); unit.derivedFrom().ifPresent(result::add);
        unit.components().forEach(c -> result.add(c.unit())); return result;
    }

    boolean unitExtends(SymbolId actual, SymbolId expected) {
        UnitSymbol target = extensionUnit(expected);
        if (target == null) return false;
        if (target.name().value().equals("INTERLIS.ANYUNIT")) return true;
        var pending = new java.util.ArrayDeque<SymbolId>(); pending.add(actual);
        Set<SymbolId> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            work.checkpoint(); SymbolId next = pending.removeFirst();
            if (next.equals(expected)) return true;
            if (!seen.add(next)) continue;
            UnitSymbol unit = extensionUnit(next);
            if (unit == null) continue;
            if (unit.header().origin() instanceof Origin.SyntheticOrigin
                    && target.header().origin() instanceof Origin.SyntheticOrigin && unit.name().equals(target.name())) return true;
            unit.base().ifPresent(pending::add); unit.derivedFrom().ifPresent(pending::add);
        }
        return false;
    }

    boolean unitsConvertible(SymbolId actual, SymbolId expected) {
        return guru.interlis.model.internal.TypeCompatibility.unitRelation(actual, expected, this::extensionUnit)
                .filter(relation -> relation.decision() == guru.interlis.model.RelationDecision.ALLOWED).isPresent();
    }

    UnitSymbol extensionUnit(SymbolId id) {
        work.checkpoint();
        Symbol symbol = state.symbol(id);
        if (symbol == null) symbol = index.byId(id).map(g.declarations::buildSymbol).orElse(null);
        return symbol instanceof UnitSymbol unit ? unit : null;
    }

    guru.interlis.model.TypeRelation numericExtension(NumericType actual, NumericType base, guru.interlis.model.LanguageVersion language) {
        return guru.interlis.model.internal.TypeCompatibility.numericExtension(actual, base, language, this::extensionUnit);
    }

    List<DiagnosticId> numericErrors(NumericTypeSyntax syntax, DeclInfo declaration) {
        List<DiagnosticId> errors = new ArrayList<>();
        if (syntax.minimum().isPresent() && syntax.maximum().isPresent()) {
            var minimum = syntax.minimum().orElseThrow(); var maximum = syntax.maximum().orElseThrow();
            if (minimum.exactValue().compareTo(maximum.exactValue()) > 0)
                errors.add(diagnostics.emitRule(declaration, "numeric minimum exceeds maximum", "NUMERIC_BOUNDS", syntax.range()));
            if (minimum.precision().fractionalDigits() != maximum.precision().fractionalDigits())
                errors.add(diagnostics.emitRule(declaration, "numeric bounds must have the same precision", "NUMERIC_PRECISION", syntax.range()));
        }
        return List.copyOf(errors);
    }

    void validateNumericUnit(NumericRange range,DeclInfo declaration,SourceRange sourceRange,List<DiagnosticId> errors) {
        if(range.minimum().isPresent() && range.maximum().isPresent() && range.unit().isPresent()) {
            UnitSymbol unit=extensionUnit(range.unit().orElseThrow());
            if(unit!=null && unit.abstractUnit())errors.add(diagnostics.emitRule(declaration,"concrete numeric range requires a concrete unit","NUMERIC_ABSTRACT_UNIT",sourceRange));
        }
    }

    void validateNumericUses() {
        for(var entry:state.declaredTypesInBuildOrder()) {
            work.checkpoint();var declaration=index.byId(entry.getKey()).orElse(null);
            if(declaration==null || declaration.kind()!=SymbolKind.DOMAIN && declaration.kind()!=SymbolKind.ATTRIBUTE)continue;
            var type=g.types.effectiveDescriptor(entry.getValue());
            while(type instanceof CollectionType collection)type=g.types.effectiveDescriptor(collection.elementType());
            boolean incomplete=type instanceof NumericType number && (number.range().minimum().isEmpty() || number.range().maximum().isEmpty())
                    || type instanceof CoordinateType coordinate && coordinate.axes().stream().anyMatch(axis->axis.minimum().isEmpty() || axis.maximum().isEmpty());
            if(!incomplete || declaration.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))continue;
            if(declaration.syntax() instanceof AttributeDeclarationSyntax attribute && attribute.type() instanceof GenericTypeSyntax inferred && inferred.name().equals("INFERRED"))continue;
            boolean generic=declaration.language()==guru.interlis.model.LanguageVersion.ILI24 && type instanceof CoordinateType
                    && (declaration.modifiers().contains(guru.interlis.model.Modifier.GENERIC) || genericCoordinateType(entry.getValue(),new HashSet<>()));
            if(!generic)diagnostics.emitRule(declaration,"incomplete numeric or coordinate declaration requires ABSTRACT","NUMERIC_ABSTRACT");
        }
    }

    private boolean genericCoordinateType(TypeId id,Set<TypeId> seen) {
        if(!seen.add(id))return false;
        if(state.type(id) instanceof DomainReferenceType reference) {
            var domain=state.symbol(reference.targetDomain());
            return domain instanceof DomainSymbol d && (d.generic() || genericCoordinateType(d.typeId(),seen));
        }
        return false;
    }
}
