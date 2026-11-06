package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.FormattedType;
import guru.interlis.model.internal.FormattedValues;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ErrorType;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.syntax.FormattedTypeSyntax;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Binds formatted types and checks their bound ranges and components. */
final class FormattedTypeBindings {
    private final GraphBuilder g;
    private final SemanticBuildState state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;

    FormattedTypeBindings(GraphBuilder graph, SemanticBuildState state, SemanticWork work,
                   IndexedDeclarations index, SemanticDiagnostics diagnostics) {
        this.g = graph;
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
    }

    private Optional<SymbolId> bindFormatName(SymbolId context,guru.interlis.syntax.NameSyntax name,Set<SymbolKind> kinds) {
        var target=g.names.bindTarget(index.byId(context).orElseThrow(),name,kinds,ReferenceUsage.TYPE_USE,new ArrayList<>());
        target.flatMap(index::byId).filter(d->d.kind()==SymbolKind.DOMAIN).ifPresent(g.types::domainType);
        return target;
    }

    TypeDescriptor buildFormattedType(TypeId id, FormattedTypeSyntax syntax, SymbolId context) {
        DeclInfo owner = index.byId(context).orElseThrow();
        List<DiagnosticId> errors=new ArrayList<>();
        Optional<SymbolId> domain = syntax.basedOnStructure() || syntax.base().isEmpty()
                ? Optional.ofNullable(state.baseOf(context)) : Optional.empty();
        Optional<SymbolId> structure = Optional.empty();
        Optional<guru.interlis.model.TemporalKind> predefined = Optional.empty();
        if (syntax.base().isPresent()) {
            var name = syntax.base().orElseThrow();
            if (syntax.basedOnStructure()) structure = bindFormatName(context, name, Set.of(SymbolKind.STRUCTURE));
            else domain = bindFormatName(context, name, Set.of(SymbolKind.DOMAIN));
        }
        TypeDescriptor inherited = domain.map(state::declaredType).map(g.types::effectiveDescriptor).orElse(null);
        FormattedType base = inherited instanceof FormattedType f ? f : null;
        List<FormattedType.Part> parts = new ArrayList<>();
        if (!syntax.basedOnStructure()) {
            if (base == null) errors.add(diagnostics.emitRule(owner, "FORMAT requires a bound formatted base domain", "FORMAT_BASE"));
            else { structure = base.structure(); predefined = base.predefined(); parts.addAll(base.parts()); }
        } else if (syntax.inheritance()) {
            if (base == null || structure.isEmpty() || base.structure().isEmpty()
                    || !g.targetSpecializes(structure.orElseThrow(), base.structure().orElseThrow()))
                errors.add(diagnostics.emitRule(owner, "FORMAT INHERITANCE requires the base structure or a specialization", "FORMAT_INHERITANCE"));
            else for (var part : base.parts()) {
                // INHERITANCE keeps the layout and provenance, but uses the effective
                // attribute of the specialized structure (including its precision).
                Optional<SymbolId> attribute = part.attribute().isEmpty() ? Optional.empty()
                        : g.findMember(structure.orElseThrow(), state.symbol(part.attribute().orElseThrow()).name().value());
                parts.add(new FormattedType.Part(attribute, part.width(), part.format(), part.literal(), part.origin()));
            }
        } else if (base != null) errors.add(diagnostics.emitRule(owner, "extended FORMAT must retain its inherited prefix", "FORMAT_INHERITANCE"));
        for (var part : syntax.parts()) {
            work.checkpoint();
            Optional<SymbolId> attribute = Optional.empty(), nested = Optional.empty();
            if (part.attribute().isEmpty()) {
                if (!part.literal().isEmpty() && Character.isDigit(part.literal().charAt(0)))
                    errors.add(diagnostics.emitRule(owner, "FORMAT separator must not start with a digit", "FORMAT_SEPARATOR"));
            } else {
                var name = part.attribute().orElseThrow();
                attribute = structure.flatMap(st -> g.findMember(st, name.text()));
                g.references.recordReference(owner, name, attribute.orElse(null), ReferenceUsage.TYPE_USE);
                if (attribute.isEmpty()) errors.add(diagnostics.unresolved(name));
                attribute.flatMap(index::byId).ifPresent(g.types::attributeType);
                TypeDescriptor value = attribute.map(state::declaredType).map(g.types::effectiveDescriptor).orElse(null);
                if (part.format().isPresent()) {
                    nested = bindFormatName(context, part.format().orElseThrow(), Set.of(SymbolKind.DOMAIN));
                    TypeDescriptor nestedType = nested.map(state::declaredType).map(g.types::effectiveDescriptor).orElse(null);
                    SymbolId selected = value instanceof CollectionType c && !g.isMany(c.cardinality()) ? g.structures.target(c) : g.structures.target(value);
                    if (value instanceof CollectionType c && g.isMany(c.cardinality())) selected = null;
                    if (!(nestedType instanceof FormattedType f) || f.structure().isEmpty() || selected == null
                            || !g.targetSpecializes(selected, f.structure().orElseThrow()))
                        errors.add(diagnostics.emitRule(owner, "nested FORMAT requires a compatible singleton structure", "FORMAT_ATTRIBUTE"));
                } else if (!(value instanceof NumericType)) errors.add(diagnostics.emitRule(owner, "FORMAT attribute must be numeric", "FORMAT_ATTRIBUTE"));
                else {
                    NumericRange range = ((NumericType)value).range();
                    if (range.minimum().isPresent() && range.maximum().isPresent()
                            && range.minimum().orElseThrow().precision().fractionalDigits()
                            != range.maximum().orElseThrow().precision().fractionalDigits())
                        errors.add(diagnostics.emitRule(owner, "FORMAT component bounds must have matching precision", "FORMAT_PRECISION"));
                }
                if (part.width().filter(w -> w.signum() <= 0).isPresent()) errors.add(diagnostics.emitRule(owner, "FORMAT width must be positive", "FORMAT_WIDTH"));
            }
            parts.add(new FormattedType.Part(attribute, part.width(), nested, part.literal(), new Origin.DeclaredOrigin(part.range())));
        }
        var result = new FormattedType(id, new Origin.DeclaredOrigin(syntax.range()),
                syntax.base().map(guru.interlis.syntax.NameSyntax::text).orElse("<inherited>"),
                predefined, domain, structure, parts, syntax.minimum(), syntax.maximum());
        if (result.minimum().isPresent() || result.maximum().isPresent()) {
            var min = result.minimum().flatMap(v -> formatValue(result, v));
            var max = result.maximum().flatMap(v -> formatValue(result, v));
            if (min.isEmpty() || max.isEmpty() || FormattedValues.compare(min.orElseThrow(), max.orElseThrow()) > 0)
                errors.add(diagnostics.emitRule(owner, "FORMAT bounds must fit the layout and component ranges", "FORMAT_BOUNDS"));
            else {
                Set<TypeId> seen = new LinkedHashSet<>();
                for (FormattedType ancestor = base; ancestor != null && seen.add(ancestor.id());) {
                    work.checkpoint();
                    var lower = ancestor.minimum().isPresent() ? formatValue(ancestor, ancestor.minimum().orElseThrow()) : Optional.<List<java.math.BigDecimal>>empty();
                    var upper = ancestor.maximum().isPresent() ? formatValue(ancestor, ancestor.maximum().orElseThrow()) : Optional.<List<java.math.BigDecimal>>empty();
                    if (lower.filter(v -> FormattedValues.compare(min.orElseThrow(), v) < 0).isPresent()
                            || upper.filter(v -> FormattedValues.compare(max.orElseThrow(), v) > 0).isPresent())
                        errors.add(diagnostics.emitRule(owner, "FORMAT extension widens base bounds", "FORMAT_BOUNDS"));
                    TypeDescriptor next = ancestor.baseDomain().map(state::declaredType).map(g.types::effectiveDescriptor).orElse(null);
                    ancestor = next instanceof FormattedType f ? f : null;
                }
            }
        }
        return errors.isEmpty()?result:new ErrorType(id,new Origin.DeclaredOrigin(syntax.range()),errors);
    }

    void validateFormattedUses() {
        for (var entry : state.declaredTypesInBuildOrder()) {
            work.checkpoint();
            DeclInfo declaration = index.byId(entry.getKey()).orElse(null);
            if (declaration == null || declaration.kind() != SymbolKind.DOMAIN && declaration.kind() != SymbolKind.ATTRIBUTE)
                continue;
            TypeDescriptor type = g.types.effectiveDescriptor(entry.getValue());
            while (type instanceof CollectionType collection) type = g.types.effectiveDescriptor(collection.elementType());
            if (type instanceof FormattedType format && formattedValues().isAbstract(format)
                    && !declaration.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))
                diagnostics.emitRule(declaration, "FORMAT with abstract components requires an abstract domain or attribute", "FORMAT_ABSTRACT");
        }
    }

    FormattedValues formattedValues() {
        return new FormattedValues(id -> g.types.effectiveDescriptor(state.declaredType(id)), work::checkpoint);
    }

    private Optional<List<java.math.BigDecimal>> formatValue(FormattedType type, String value) {
        return formattedValues().parse(type, value, false);
    }
}
