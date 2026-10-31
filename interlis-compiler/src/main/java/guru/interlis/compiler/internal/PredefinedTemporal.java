package guru.interlis.compiler.internal;

import guru.interlis.model.*;
import java.math.BigInteger;
import java.util.*;

/** The versioned INTERLIS date/time declarations (reference manuals, appendix A).
 * Built on demand in the owning graph; FORMAT values use the ordinary bound layout. */
final class PredefinedTemporal {
    private final GraphBuilder g;
    PredefinedTemporal(GraphBuilder graph) { g = graph; }

    Optional<SymbolId> find(DeclInfo context, String qualified, Set<SymbolKind> kinds) {
        if (!qualified.startsWith("INTERLIS.")) return Optional.empty();
        String name = qualified.substring(9);
        SymbolKind kind = switch (name) {
            case "XMLDate", "XMLTime", "XMLDateTime", "GregorianYear" -> SymbolKind.DOMAIN;
            case "TimeOfDay", "UTC", "GregorianDate", "GregorianDateTime" -> SymbolKind.STRUCTURE;
            case "h", "min", "d", "M", "Y" -> SymbolKind.UNIT;
            case "TIMESYSTEMS.CALENDAR", "TIMESYSTEMS.TIMEOFDAYSYS" -> SymbolKind.CLASS;
            default -> null;
        };
        if (kind == null || !kinds.contains(kind)) return Optional.empty();
        SymbolId id = id(context, name);
        if (g.symbols.containsKey(id)) return Optional.of(id);
        if (kind == SymbolKind.CLASS) timeSystem(context, "UTC");
        else if (kind == SymbolKind.UNIT) unit(context, name);
        else if (kind == SymbolKind.STRUCTURE) structure(context, name);
        else domain(context, name);
        return Optional.of(id);
    }

    private SymbolId require(DeclInfo context, String name, SymbolKind kind) {
        return find(context, "INTERLIS." + name, Set.of(kind)).orElseThrow();
    }

    private void unit(DeclInfo context, String name) {
        String parent = switch (name) { case "min" -> "s"; case "h" -> "min"; case "d" -> "h"; default -> "TIME"; };
        var base = parent.equals("min") || parent.equals("h") ? Optional.of(require(context, parent, SymbolKind.UNIT))
                : g.builtinUnit(context.id(), "INTERLIS." + parent);
        String factor = switch (name) { case "min", "h" -> "60"; case "d" -> "24"; default -> null; };
        var factors = factor == null ? List.<UnitSymbol.ConversionFactor>of() : List.of(
                new UnitSymbol.ConversionFactor(UnitSymbol.Operator.MULTIPLY, UnitSymbol.ConstantKind.DECIMAL,
                        Optional.of(DecimalLiteral.parse(factor)), new guru.interlis.source.SourceRange(new guru.interlis.source.SourceId("predefined:/INTERLIS-" + context.language()), guru.interlis.source.SourceRevision.initial(), 0, 0)));
        var h = header(context, name, SymbolKind.UNIT, Optional.empty(), 0, Set.of());
        g.symbols.put(h.id(), new UnitSymbol(h, switch (name) { case "min" -> "Minute"; case "h" -> "Hour";
            case "d" -> "Day"; case "M" -> "Month"; default -> "Year"; }, false, factor == null ? base : Optional.empty(),
                factor == null ? Optional.empty() : base, List.of(), factors, Optional.empty(), Optional.empty()));
    }

    private TypeId number(DeclInfo context, String lo, String hi, String unit, boolean circular, String system) {
        var id = g.nextTypeId();
        var boundUnit = unit.equals("s") ? g.builtinUnit(context.id(), "INTERLIS.s")
                : Optional.of(require(context, unit, SymbolKind.UNIT));
        var reference = system == null ? Optional.<NumericRange.ReferenceSystem>empty()
                : Optional.of(new NumericRange.ReferenceSystem(timeSystem(context, system), false, OptionalInt.empty()));
        g.types.put(id, new NumericType(id, origin(context, "numeric:" + unit),
                new NumericRange(Optional.of(DecimalLiteral.parse(lo)), Optional.of(DecimalLiteral.parse(hi)),
                        circular, NumericDirection.NONE, boundUnit, reference)));
        return id;
    }

    private SymbolId timeSystem(DeclInfo context, String name) {
        var object = id(context, "BaseTimeSystems." + name);
        if (g.symbols.containsKey(object)) return object;
        var topic = header(context, "TIMESYSTEMS", SymbolKind.TOPIC, Optional.empty(), 0, Set.of());
        var base = g.metadata.predefined(context, "INTERLIS.SCALSYSTEM", Set.of(SymbolKind.CLASS)).orElseThrow();
        var classes = new ArrayList<SymbolId>();
        for (String className : List.of("CALENDAR", "TIMEOFDAYSYS")) {
            var h = header(context, "TIMESYSTEMS." + className, SymbolKind.CLASS, Optional.of(topic.id()), classes.size(), Set.of());
            g.baseOf.put(h.id(), base);
            var unitType = g.nextTypeId();
            g.types.put(unitType, new NumericType(unitType, h.origin(), new NumericRange(Optional.empty(), Optional.empty(),
                    false, NumericDirection.NONE, g.builtinUnit(context.id(), "INTERLIS.TIME"), Optional.empty())));
            var original = header(context, "TIMESYSTEMS." + className + ".Unit", SymbolKind.PARAMETER, Optional.of(h.id()), 0, Set.of(Modifier.EXTENDED));
            var parameter = new SymbolHeader(original.id(), original.kind(), DeclaredName.declared("Unit"), original.owner(), 0,
                    original.origin(), Optional.empty(), context.language(), Documentation.empty(), MetaAttributeSet.empty(), original.modifiers(), List.of());
            var inherited = ((ClassSymbol)g.symbols.get(base)).members().stream()
                    .filter(id -> g.symbols.get(id).kind() == SymbolKind.PARAMETER).findFirst();
            g.symbols.put(parameter.id(), new ParameterSymbol(parameter, unitType, false, inherited));
            g.declaredTypes.put(parameter.id(), unitType);
            g.symbols.put(h.id(), new ClassSymbol(h, Optional.of(base), List.of(parameter.id()), List.of()));
            g.effectiveMembers.put(h.id(), g.effectiveMembers.getOrDefault(base, List.of()).stream().map(m ->
                    new EffectiveMember(m.declaredSymbol(), h.id(), List.of(base, h.id()), m.position(), EffectiveMemberOrigin.INHERITED, false)).toList());
            classes.add(h.id());
        }
        g.symbols.put(topic.id(), new TopicSymbol(topic, Optional.empty(), classes, List.of(), List.of(), List.of()));
        var basket = header(context, "BaseTimeSystems", SymbolKind.METADATA_BASKET, Optional.empty(), 0, Set.of());
        List<SymbolId> objects = new ArrayList<>();
        for (String objectName : List.of("GregorianCalendar", "UTC")) {
            var h = header(context, "BaseTimeSystems." + objectName, SymbolKind.METAOBJECT, Optional.of(basket.id()), objects.size(), Set.of());
            g.symbols.put(h.id(), new MetaObjectSymbol(h, Optional.of(classes.get(objects.size())))); objects.add(h.id());
        }
        g.symbols.put(basket.id(), new MetadataBasketSymbol(basket, MetadataBasketSymbol.Kind.REFSYSTEM,
                Optional.of(topic.id()), Optional.empty(), objects));
        return object;
    }

    private void structure(DeclInfo context, String name) {
        String parent = switch (name) { case "UTC" -> "TimeOfDay"; case "GregorianDateTime" -> "GregorianDate"; default -> null; };
        var base = parent == null ? Optional.<SymbolId>empty() : Optional.of(require(context, parent, SymbolKind.STRUCTURE));
        var h = header(context, name, SymbolKind.STRUCTURE, Optional.empty(), 0,
                name.equals("TimeOfDay") ? Set.of(Modifier.ABSTRACT) : Set.of());
        base.ifPresent(id -> g.baseOf.put(h.id(), id));
        List<SymbolId> members = new ArrayList<>();
        if (name.equals("GregorianDate")) {
            var type = g.nextTypeId();
            g.types.put(type, new DomainReferenceType(type, h.origin(), require(context, "GregorianYear", SymbolKind.DOMAIN)));
            members.add(attribute(context, h, "Year", type, false, false, Optional.empty(), 0));
            members.add(attribute(context, h, "Month", number(context, "1", "12", "M", false, null), true, false, Optional.empty(), 1));
            members.add(attribute(context, h, "Day", number(context, "1", "31", "d", false, null), true, false, Optional.empty(), 2));
        } else {
            Optional<SymbolId> override = name.equals("UTC") ? g.findMember(base.orElseThrow(), "Hours") : Optional.empty();
            members.add(attribute(context, h, "Hours", number(context, "0", "23", "h", true,
                    name.equals("TimeOfDay") ? null : "UTC"), name.equals("GregorianDateTime"), false, override, 0));
            if (!name.equals("UTC")) {
                members.add(attribute(context, h, "Minutes", number(context, "0", "59", "min", true, null), true, true, Optional.empty(), 1));
                members.add(attribute(context, h, "Seconds", number(context, "0.000", "59.999", "s", true, null), true, true, Optional.empty(), 2));
            }
        }
        g.symbols.put(h.id(), new StructureSymbol(h, base, members, List.of()));
        List<EffectiveMember> effective = new ArrayList<>();
        base.ifPresent(id -> g.effectiveMembers.getOrDefault(id, List.of()).forEach(m -> {
            var replacement = members.stream().filter(member -> ((AttributeSymbol)g.symbols.get(member)).overrides().filter(m.declaredSymbol()::equals).isPresent()).findFirst();
            effective.add(new EffectiveMember(replacement.orElse(m.declaredSymbol()), h.id(), List.of(id, h.id()), effective.size(),
                    replacement.isPresent() ? EffectiveMemberOrigin.OVERRIDDEN : EffectiveMemberOrigin.INHERITED, replacement.isPresent()));
        }));
        for (var member : members) if (effective.stream().noneMatch(m -> m.declaredSymbol().equals(member)))
            effective.add(new EffectiveMember(member, h.id(), List.of(h.id()), effective.size(), EffectiveMemberOrigin.LOCAL, false));
        g.effectiveMembers.put(h.id(), List.copyOf(effective));
    }

    private SymbolId attribute(DeclInfo context, SymbolHeader owner, String name, TypeId type, boolean subdivision,
            boolean continuous, Optional<SymbolId> overrides, int order) {
        var h = header(context, owner.name().value().substring(9) + "." + name, SymbolKind.ATTRIBUTE,
                Optional.of(owner.id()), order, overrides.isPresent() ? Set.of(Modifier.EXTENDED) : Set.of());
        h = new SymbolHeader(h.id(), h.kind(), DeclaredName.declared(name), h.owner(), order, h.origin(), Optional.empty(),
                context.language(), Documentation.empty(), MetaAttributeSet.empty(), h.modifiers(), List.of());
        g.symbols.put(h.id(), new AttributeSymbol(h, type, false, false, continuous, subdivision, overrides, List.of()));
        g.declaredTypes.put(h.id(), type); return h.id();
    }

    private void domain(DeclInfo context, String name) {
        var h = header(context, name, SymbolKind.DOMAIN, Optional.empty(), 0, Set.of());
        TypeId type;
        Optional<SymbolId> base = Optional.empty();
        if (name.equals("GregorianYear")) type = number(context, "1582", "2999", "Y", false, "GregorianCalendar");
        else {
            String structureName = switch (name) { case "XMLDate" -> "GregorianDate"; case "XMLTime" -> "UTC"; default -> "GregorianDateTime"; };
            var structure = require(context, structureName, SymbolKind.STRUCTURE);
            List<FormattedType.Part> parts = new ArrayList<>();
            if (!name.equals("XMLTime")) dateParts(context, structure, parts);
            if (name.equals("XMLDateTime")) { literal(parts, "T", h.origin()); base = Optional.of(require(context, "XMLDate", SymbolKind.DOMAIN)); }
            if (!name.equals("XMLDate")) {
                field(parts, structure, "Hours", 2, h.origin()); literal(parts, ":", h.origin());
                field(parts, structure, "Minutes", context.language() == LanguageVersion.ILI24 ? 2 : 0, h.origin()); literal(parts, ":", h.origin());
                field(parts, structure, "Seconds", context.language() == LanguageVersion.ILI24 ? 2 : 0, h.origin());
            }
            type = g.nextTypeId();
            var kind = name.equals("XMLDate") ? TemporalKind.DATE : name.equals("XMLTime") ? TemporalKind.TIMEOFDAY : TemporalKind.DATETIME;
            g.types.put(type, new FormattedType(type, h.origin(), "INTERLIS." + name, Optional.of(kind), base,
                    Optional.of(structure), parts, Optional.empty(), Optional.empty()));
        }
        g.symbols.put(h.id(), new DomainSymbol(h, type, base, false)); g.declaredTypes.put(h.id(), type);
        base.ifPresent(id -> g.baseOf.put(h.id(), id));
    }

    private void dateParts(DeclInfo context, SymbolId structure, List<FormattedType.Part> parts) {
        var origin = origin(context, "XMLDate");
        field(parts, structure, "Year", context.language() == LanguageVersion.ILI24 ? 4 : 0, origin); literal(parts, "-", origin);
        field(parts, structure, "Month", context.language() == LanguageVersion.ILI24 ? 2 : 0, origin); literal(parts, "-", origin);
        field(parts, structure, "Day", context.language() == LanguageVersion.ILI24 ? 2 : 0, origin);
    }
    private void field(List<FormattedType.Part> parts, SymbolId structure, String name, int width, Origin origin) {
        parts.add(new FormattedType.Part(Optional.of(g.findMember(structure, name).orElseThrow()),
                width == 0 ? Optional.empty() : Optional.of(BigInteger.valueOf(width)), Optional.empty(), "", origin));
    }
    private void literal(List<FormattedType.Part> parts, String value, Origin origin) {
        parts.add(new FormattedType.Part(Optional.empty(), Optional.empty(), Optional.empty(), value, origin));
    }
    private SymbolId id(DeclInfo context, String name) { return new SymbolId("predefined:" + context.language() + ":temporal:" + name); }
    private Origin origin(DeclInfo context, String name) { return new Origin.PredefinedOrigin(context.language(), "INTERLIS." + name); }
    private SymbolHeader header(DeclInfo context, String name, SymbolKind kind, Optional<SymbolId> owner, int order, Set<Modifier> modifiers) {
        return new SymbolHeader(id(context, name), kind, DeclaredName.synthetic("INTERLIS." + name), owner, order,
                origin(context, name), Optional.empty(), context.language(), Documentation.empty(), MetaAttributeSet.empty(), modifiers, List.of());
    }
}
