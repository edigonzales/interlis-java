package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.*;
import guru.interlis.syntax.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Metadata binding within one graph build. External object values are never loaded. */
final class MetadataBindings {
    private final SemanticGraphRead.Symbols state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final NameResolver resolver;
    private final NameBindings names;
    private final ReferenceBindings references;
    private final InheritanceBindings inheritance;
    private final UnitBindings units;
    private final Supplier<TypeBindings> typeBindings;
    private final ObjectPathBindings paths;
    private final StructureBindings structures;
    private final Supplier<ExpressionBindings> expressions;
    private final Function<DeclInfo, SymbolHeader> symbolHeader;
    private final Function<DeclInfo, Symbol> ensureSymbol;
    private final Set<SymbolId> active = new HashSet<>();

    MetadataBindings(SemanticGraphRead.Symbols state,
                     SemanticWork work,
                     IndexedDeclarations index,
                     SemanticDiagnostics diagnostics,
                     NameResolver resolver,
                     NameBindings names,
                     ReferenceBindings references,
                     InheritanceBindings inheritance,
                     UnitBindings units,
                     Supplier<TypeBindings> typeBindings,
                     ObjectPathBindings paths,
                     StructureBindings structures,
                     Supplier<ExpressionBindings> expressions,
                     Function<DeclInfo, SymbolHeader> symbolHeader,
                     Function<DeclInfo, Symbol> ensureSymbol) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.resolver = resolver;
        this.names = names;
        this.references = references;
        this.inheritance = inheritance;
        this.units = units;
        this.typeBindings = typeBindings;
        this.paths = paths;
        this.structures = structures;
        this.expressions = expressions;
        this.symbolHeader = symbolHeader;
        this.ensureSymbol = ensureSymbol;
    }

    MetadataBasketSymbol basket(DeclInfo decl) {
        var syntax = (MetadataBasketDeclarationSyntax)decl.syntax();
        var topic = names.bindTarget(decl, syntax.topic(), Set.of(SymbolKind.TOPIC), ReferenceUsage.TYPE_USE, new ArrayList<>());
        var base = syntax.baseBasket().flatMap(name -> names.bindTarget(decl, name, Set.of(SymbolKind.METADATA_BASKET), ReferenceUsage.BASE_TYPE, new ArrayList<>()));
        var result = new MetadataBasketSymbol(symbolHeader.apply(decl), syntax.basketKind(), topic, base,
                index.children(decl.id()).stream().map(DeclInfo::id).toList());
        state.defineSymbol(result);
        active.add(decl.id());
        base.ifPresent(id -> {
            if (active.contains(id)) { diagnostics.emitRule(decl, "cyclic metadata basket inheritance", "METADATA_BASKET_CYCLE"); return; }
            var parent = (MetadataBasketSymbol)ensureSymbol.apply(index.byId(id).orElseThrow());
            if (parent.header().has(Modifier.FINAL)) diagnostics.emitRule(decl, "cannot extend FINAL metadata basket", "METADATA_BASKET_FINAL");
            if (parent.basketKind() != syntax.basketKind()) diagnostics.emitRule(decl, "metadata basket kind differs from base", "METADATA_BASKET_KIND");
            if (topic.isPresent() && parent.topic().isPresent() && !topicSpecializes(topic.get(), parent.topic().get()))
                diagnostics.emitRule(decl, "metadata basket topic must equal or extend its base topic", "METADATA_BASKET_TOPIC");
        });
        for (var child : index.children(decl.id())) { work.checkpoint(); ensureSymbol.apply(child); }
        active.remove(decl.id());
        return result;
    }

    private boolean topicSpecializes(SymbolId actual, SymbolId expected) {
        Set<SymbolId> seen = new HashSet<>();
        while (actual != null && seen.add(actual)) {
            work.checkpoint(); if (actual.equals(expected)) return true;
            var declaration = index.byId(actual).orElseThrow();
            actual = ((TopicSyntax)declaration.syntax()).baseTopic().flatMap(name ->
                    resolver.resolve(declaration.id(), name, Set.of(SymbolKind.TOPIC)).target()).orElse(null);
        }
        return false;
    }

    MetaObjectSymbol object(DeclInfo decl) {
        var syntax = (MetadataBasketDeclarationSyntax.ObjectDeclaration)decl.syntax();
        var owner = index.byId(decl.owner().orElseThrow()).orElseThrow();
        var basket = state.symbol(owner.id()) instanceof MetadataBasketSymbol b ? b : basket(owner);
        Optional<SymbolId> target = Optional.empty();
        SymbolId topic = basket.topic().orElse(null);
        Set<SymbolId> seen = new HashSet<>();
        while (topic != null && seen.add(topic) && target.isEmpty()) {
            work.checkpoint();
            target = index.children(topic).stream().filter(d -> d.kind() == SymbolKind.CLASS && d.name().equals(syntax.objectClass().text())).map(DeclInfo::id).findFirst();
            var topicDecl = index.byId(topic).orElseThrow();
            topic = ((TopicSyntax)topicDecl.syntax()).baseTopic().flatMap(name -> resolver.resolve(topicDecl.id(), name, Set.of(SymbolKind.TOPIC)).target()).orElse(null);
        }
        references.recordReference(decl, syntax.objectClass(), target.orElse(null), ReferenceUsage.TYPE_USE);
        if (target.isEmpty()) diagnostics.emitRule(decl, "metadata object class is not declared in the basket topic", "METAOBJECT_CLASS");
        target.ifPresent(id -> {
            ensureBases(id);
            var root = predefined(decl, basket.basketKind() == MetadataBasketSymbol.Kind.REFSYSTEM ? "INTERLIS.REFSYSTEM" : "INTERLIS.SIGN", Set.of(SymbolKind.CLASS)).orElseThrow();
            if (!specializes(id, root)) diagnostics.emitRule(decl, "metadata object class has the wrong metadata family", "METAOBJECT_CLASS_KIND");
        });
        if (index.children(owner.id()).stream().filter(d -> d.name().equals(decl.name())).count() > 1)
            diagnostics.emitRule(decl, "duplicate metadata object name", "METAOBJECT_NAME");
        return new MetaObjectSymbol(symbolHeader.apply(decl), target);
    }

    void ensureBases(SymbolId id) {
        Set<SymbolId> seen = new HashSet<>();
        while (id != null && seen.add(id)) { work.checkpoint(); id = inheritance.objectInheritanceBase(id).orElse(null); }
    }
    private boolean specializes(SymbolId actual, SymbolId expected) {
        return guru.interlis.model.internal.TypeCompatibility.specializes(actual, expected, state::baseOf);
    }

    NumericRange numeric(NumericTypeSyntax syntax, DeclInfo context, List<DiagnosticId> errors) {
        var unit = syntax.unit().flatMap(name -> units.bindUnit(context, name, errors));
        var ref = syntax.referenceSystem().flatMap(r -> {
            if (r.axis().isPresent() && r.axis().getAsInt() < 1) {
                errors.add(diagnostics.emitRule(context, "reference-system axis numbers start at one", "REFSYSTEM_AXIS")); return Optional.empty();
            }
            Optional<SymbolId> target = r.domain() ? names.bindTarget(context, r.target(), Set.of(SymbolKind.DOMAIN), ReferenceUsage.TYPE_USE, errors)
                    : objectReference(context, r.target(),errors);
            return target.map(id -> new NumericRange.ReferenceSystem(id, r.domain(), r.axis()));
        });
        return new NumericRange(syntax.minimum(), syntax.maximum(), syntax.circular(), syntax.direction(), unit, ref);
    }

    Optional<SymbolId> objectReference(DeclInfo context, NameSyntax name) {
        return objectReference(context,name,new ArrayList<>());
    }

    private Optional<SymbolId> objectReference(DeclInfo context, NameSyntax name,List<DiagnosticId> errors) {
        var resolution = resolver.resolve(context.id(), name, Set.of(SymbolKind.METAOBJECT));
        var target = resolution.target();
        if (resolution.status() == NameResolver.Status.UNRESOLVED) {
            List<SymbolId> baskets = new ArrayList<>();
            if (name.segments().size() > 1) {
                var prefix = new NameSyntax(name.id(), name.range(), name.segments().subList(0, name.segments().size() - 1));
                resolver.resolve(context.id(), prefix, Set.of(SymbolKind.METADATA_BASKET)).target().ifPresent(baskets::add);
            } else {
                // A bare object name is legal only with one visible applicable basket.
                SymbolId scope = context.id();
                while (scope != null && baskets.isEmpty()) {
                    for (var child : index.children(scope)) if (child.kind() == SymbolKind.METADATA_BASKET) baskets.add(child.id());
                    scope = index.byId(scope).flatMap(DeclInfo::owner).orElse(null);
                }
            }
            if (baskets.size() == 1) target = inheritedObject(baskets.getFirst(), name.segments().getLast().text(), new HashSet<>());
        }
        if (target.isPresent()) {
            references.recordReference(context, name, target.orElseThrow(), ReferenceUsage.TYPE_USE);
        } else {
            references.recordResolution(context, name, resolution, ReferenceUsage.TYPE_USE);
            errors.add(diagnostics.emitRule(context, "metadata object reference is missing or ambiguous: " + name.text(), "METAOBJECT_REFERENCE"));
        }
        target.ifPresent(id -> ensureSymbol.apply(index.byId(id).orElseThrow()));
        return target;
    }

    private Optional<SymbolId> inheritedObject(SymbolId basketId, String name, Set<SymbolId> seen) {
        if (!seen.add(basketId)) return Optional.empty();
        var basket = (MetadataBasketSymbol)ensureSymbol.apply(index.byId(basketId).orElseThrow());
        var local = index.children(basketId).stream().filter(d -> d.name().equals(name)).map(DeclInfo::id).toList();
        if (!local.isEmpty()) return local.size() == 1 ? Optional.of(local.getFirst()) : Optional.empty();
        return basket.baseBasket().flatMap(id -> inheritedObject(id, name, seen));
    }

    void validate() {
        for (var symbol : List.copyOf(state.symbolsInBuildOrder())) {
            var decl = index.byId(symbol.id()).orElse(null);
            if (decl == null) continue;
            if (symbol instanceof ParameterSymbol parameter && index.byId(decl.owner().orElseThrow()).orElseThrow().kind() != SymbolKind.MODEL)
                validateParameter(decl, parameter);
        }
        for (var entry : List.copyOf(state.declaredTypesInBuildOrder())) {
            var decl = index.byId(entry.getKey()).orElse(null); if (decl == null) continue;
            var type = typeBindings.get().effectiveDescriptor(entry.getValue());
            if (type instanceof NumericType n) validateRange(decl, n.range());
            if (type instanceof CoordinateType c) {
                c.axes().forEach(axis -> validateRange(decl, axis));
                c.rotation().ifPresent(r -> { if (r.zeroAxis() < 1 || r.quarterTurnAxis() < 1 || r.zeroAxis() > c.dimensions()
                        || r.quarterTurnAxis() > c.dimensions() || r.zeroAxis() == r.quarterTurnAxis())
                    diagnostics.emitRule(decl, "coordinate rotation needs two distinct existing axes", "COORDINATE_ROTATION"); });
            }
        }
    }

    private void validateParameter(DeclInfo decl, ParameterSymbol parameter) {
        SymbolId owner = decl.owner().orElseThrow();
        var axis = predefined(decl, "INTERLIS.AXIS", Set.of(SymbolKind.STRUCTURE)).orElseThrow();
        var refsystem = predefined(decl, "INTERLIS.REFSYSTEM", Set.of(SymbolKind.CLASS)).orElseThrow();
        var sign = predefined(decl, "INTERLIS.SIGN", Set.of(SymbolKind.CLASS)).orElseThrow();
        if (specializes(owner, sign)) {
            var inherited = parameter(state.baseOf(owner), decl.name());
            if (index.children(owner).stream().filter(d -> d.kind()==SymbolKind.PARAMETER && d.name().equals(decl.name())).count()>1)
                diagnostics.emitRule(decl, "duplicate signature parameter", "PARAMETER_NAME_UNIQUE");
            if (inherited.isPresent() != decl.modifiers().contains(Modifier.EXTENDED))
                diagnostics.emitRule(decl, "signature parameter override needs an inherited parameter and EXTENDED", "PARAMETER_OVERRIDE");
            inherited.ifPresent(id -> {
                var parent=(ParameterSymbol)state.symbol(id);
                if (parent.header().has(Modifier.FINAL)) diagnostics.emitRule(decl,"cannot override FINAL parameter","PARAMETER_FINAL");
                if (!typeBindings.get().assignable(parameter.typeId(),parent.typeId()) || parent.mandatory() && !parameter.mandatory())
                    diagnostics.emitRule(decl,"signature parameter must refine inherited type and mandatory state","PARAMETER_TYPE");
            });
            state.replaceSymbol(new ParameterSymbol(parameter.header(),parameter.typeId(),parameter.mandatory(),inherited));
            return;
        }
        if (!(specializes(owner, axis) || specializes(owner, refsystem))) {
            diagnostics.emitRule(decl, "class parameters require a reference-system, axis or signature class", "PARAMETER_OWNER"); return;
        }
        var inherited = parameter(state.baseOf(owner), decl.name());
        if (!decl.name().equals("Unit") || inherited.isEmpty()) {
            diagnostics.emitRule(decl, "reference-system and axis parameters may only refine the predefined Unit", "PARAMETER_UNIT"); return;
        }
        if (!decl.modifiers().contains(Modifier.EXTENDED)) diagnostics.emitRule(decl, "parameter override requires EXTENDED", "PARAMETER_OVERRIDE");
        var parent = (ParameterSymbol)state.symbol(inherited.get());
        var actual = typeBindings.get().effectiveDescriptor(parameter.typeId()); var expected = typeBindings.get().effectiveDescriptor(parent.typeId());
        if (!(actual instanceof NumericType a && expected instanceof NumericType b)
                || b.range().unit().isPresent() && (a.range().unit().isEmpty() || !units.unitExtends(a.range().unit().get(), b.range().unit().get())))
            diagnostics.emitRule(decl, "Unit parameter must specialize the inherited numeric unit", "PARAMETER_UNIT_TYPE");
        if (parent.header().has(Modifier.FINAL)) diagnostics.emitRule(decl, "cannot override FINAL parameter", "PARAMETER_FINAL");
        state.replaceSymbol(new ParameterSymbol(parameter.header(), parameter.typeId(), parameter.mandatory(), inherited));
    }

    Optional<SymbolId> parameter(SymbolId owner, String name) {
        Set<SymbolId> seen = new HashSet<>();
        while (owner != null && seen.add(owner)) {
            work.checkpoint(); var symbol = state.symbol(owner);
            var members = symbol instanceof ClassSymbol c ? c.members() : symbol instanceof StructureSymbol s ? s.members() : List.<SymbolId>of();
            for (var id : members) if (state.symbol(id) instanceof ParameterSymbol p && p.name().value().equals(name)) return Optional.of(id);
            owner = state.baseOf(owner);
        }
        return Optional.empty();
    }

    void validateRange(DeclInfo decl, NumericRange range) {
        if (range.referenceSystem().isEmpty()) return;
        var ref = range.referenceSystem().get(); Optional<SymbolId> requiredUnit = Optional.empty();
        if (ref.domain()) {
            var domain = (DomainSymbol)state.symbol(ref.target());
            var target = domain == null ? null : typeBindings.get().effectiveDescriptor(domain.typeId());
            if (target instanceof NumericType n && ref.axis().isEmpty()) requiredUnit = n.range().unit();
            else if (target instanceof CoordinateType c && ref.axis().isPresent() && ref.axis().getAsInt() <= c.dimensions())
                requiredUnit = c.axes().get(ref.axis().getAsInt() - 1).unit();
            else if (target instanceof CoordinateType c && ref.axis().isEmpty() && (c.rotation().isPresent() || domain.generic())) {
                // A direction uses the coordinate system's zero direction and sense, not one of its length axes.
                if (range.unit().isPresent() && !units.unitExtends(range.unit().get(), units.builtinUnit(decl.id(), "INTERLIS.ANGLE").orElseThrow()))
                    diagnostics.emitRule(decl, "a coordinate direction reference requires an angular unit", "REFSYSTEM_DIRECTION_UNIT");
            }
            else diagnostics.emitRule(decl, "reference-system domain must be numeric or select an existing coordinate axis", "REFSYSTEM_DOMAIN");
        } else {
            var object = (MetaObjectSymbol)state.symbol(ref.target());
            if (object == null || object.objectClass().isEmpty()) return;
            SymbolId cls = object.objectClass().get();
            var required = predefined(decl, ref.axis().isPresent() ? "INTERLIS.COORDSYSTEM" : "INTERLIS.REFSYSTEM", Set.of(SymbolKind.CLASS)).orElseThrow();
            if (!specializes(cls, required)) { diagnostics.emitRule(decl, "reference-system object has the wrong scalar/coordinate family", "REFSYSTEM_OBJECT"); return; }
            if (ref.axis().isPresent()) {
                var attribute = paths.findMember(cls, "Axis").map(state::symbol).orElse(null);
                if (attribute instanceof AttributeSymbol a && typeBindings.get().effectiveDescriptor(a.declaredType()) instanceof CollectionType list) {
                    if (list.cardinality().upper() instanceof CardinalityUpper.Finite max && java.math.BigInteger.valueOf(ref.axis().getAsInt()).compareTo(max.value()) > 0)
                        diagnostics.emitRule(decl, "reference-system axis is outside the declared axis list", "REFSYSTEM_AXIS");
                    if (typeBindings.get().effectiveDescriptor(list.elementType()) instanceof StructureValueType s) cls = structures.target(s);
                }
            }
            var param = parameter(cls, "Unit").map(state::symbol).orElse(null);
            if (param instanceof ParameterSymbol p && typeBindings.get().effectiveDescriptor(p.typeId()) instanceof NumericType n) requiredUnit = n.range().unit();
        }
        if (requiredUnit.isPresent() && range.unit().isEmpty() && !requiredUnit.get().equals(units.builtinUnit(decl.id(), "INTERLIS.ANYUNIT").orElseThrow()))
            diagnostics.emitRule(decl, "reference system with a specific Unit requires an explicit numeric unit", "REFSYSTEM_UNIT_REQUIRED");
        if (requiredUnit.isPresent() && range.unit().isPresent() && !units.unitExtends(range.unit().get(), requiredUnit.get())
                && !units.unitsConvertible(range.unit().get(), requiredUnit.get()))
            diagnostics.emitRule(decl, "numeric unit is incompatible with its reference system", "REFSYSTEM_UNIT");
    }

    Optional<SymbolId> predefined(DeclInfo context, String name, Set<SymbolKind> kinds) {
        if (!Set.of("INTERLIS.METAOBJECT", "INTERLIS.METAOBJECT_TRANSLATION", "INTERLIS.REFSYSTEM", "INTERLIS.COORDSYSTEM", "INTERLIS.SCALSYSTEM", "INTERLIS.AXIS", "INTERLIS.SIGN").contains(name)) return Optional.empty();
        var kind = name.equals("INTERLIS.AXIS") ? SymbolKind.STRUCTURE : SymbolKind.CLASS;
        if (!kinds.contains(kind)) return Optional.empty();
        var h = header(context.language(), name.substring(9), kind, Optional.empty(), 0,
                kind == SymbolKind.CLASS && !name.equals("INTERLIS.METAOBJECT_TRANSLATION") ? Set.of(Modifier.ABSTRACT) : Set.of());
        if (state.hasSymbol(h.id())) return Optional.of(h.id());
        String baseName = switch (name) {
            case "INTERLIS.REFSYSTEM", "INTERLIS.SIGN" -> "INTERLIS.METAOBJECT";
            case "INTERLIS.COORDSYSTEM", "INTERLIS.SCALSYSTEM" -> "INTERLIS.REFSYSTEM";
            default -> null;
        };
        var base = baseName == null ? Optional.<SymbolId>empty() : predefined(context, baseName, Set.of(SymbolKind.CLASS));
        base.ifPresent(id -> inheritance.bindBase(h.id(), id));
        List<SymbolId> members = new ArrayList<>();
        List<SymbolId> constraints = new ArrayList<>();
        if (name.equals("INTERLIS.METAOBJECT") || name.equals("INTERLIS.METAOBJECT_TRANSLATION")) {
            var t = typeBindings.get().nextTypeId(); typeBindings.get().defineType(new TextType(t, h.origin(), TextKind.NAME, OptionalInt.of(255)));
            var names = name.equals("INTERLIS.METAOBJECT") ? List.of("Name") : List.of("Name", "NameInBaseLanguage");
            for (int i = 0; i < names.size(); i++) {
                work.checkpoint(); members.add(member(h, names.get(i), t, false, true, i));
            }
            var range = new guru.interlis.source.SourceRange(new guru.interlis.source.SourceId("predefined:/INTERLIS-" + context.language()),
                    guru.interlis.source.SourceRevision.initial(), 0, 0);
            for (int i = 0; i < names.size(); i++) {
                work.checkpoint();
                var ch = header(context.language(), name.substring(9) + "." + names.get(i) + "Unique",
                        SymbolKind.CONSTRAINT, Optional.of(h.id()), names.size() + i, Set.of());
                var expression = new ExpressionId(ch.id().value() + ":path");
                expressions.get().defineExpression(new PathExpression(expression, t, ExpressionTypeState.RESOLVED, range, List.of(),
                        new ObjectPath(List.of(new PathStep.AttributeStep(names.get(i), members.get(i), range)))));
                state.defineSymbol(new ConstraintSymbol(ch, new UniquenessConstraint(Optional.empty(), false, Optional.empty(), List.of(expression), List.of()), Optional.empty()));
                constraints.add(ch.id());
            }
        }
        if (name.equals("INTERLIS.AXIS") || name.equals("INTERLIS.SCALSYSTEM")) {
            var t = typeBindings.get().nextTypeId(); typeBindings.get().defineType(new NumericType(t, h.origin(), new NumericRange(Optional.empty(), Optional.empty(), false,
                    NumericDirection.NONE, units.builtinUnit(context.id(), "INTERLIS.ANYUNIT"), Optional.empty())));
            members.add(member(h, "Unit", t, true, false, 0));
        }
        if (name.equals("INTERLIS.COORDSYSTEM")) {
            var axis = predefined(context, "INTERLIS.AXIS", Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            var t = typeBindings.get().nextTypeId(); typeBindings.get().defineType(new StructureValueType(t, h.origin(), Optional.of(axis), List.of()));
            var list = typeBindings.get().nextTypeId(); typeBindings.get().defineType(new CollectionType(list, h.origin(), CollectionKind.LIST, Cardinality.of(1, 3), t));
            members.add(member(h, "Axis", list, false, false, 0));
        }
        if (name.equals("INTERLIS.SIGN")) {
            var type=typeBindings.get().nextTypeId(); typeBindings.get().defineType(new MetaObjectType(type,h.origin(),Optional.empty()));
            members.add(member(h,"Sign",type,true,false, 0));
        }
        state.defineSymbol(kind == SymbolKind.CLASS ? new ClassSymbol(h, base, members, constraints) : new StructureSymbol(h, base, members, constraints));
        List<EffectiveMember> effective = new ArrayList<>();
        base.ifPresent(id -> state.effectiveMembers(id).forEach(m -> effective.add(new EffectiveMember(m.declaredSymbol(), h.id(), List.of(id, h.id()), effective.size(), EffectiveMemberOrigin.INHERITED, false))));
        for (var id : members) if (state.symbol(id).kind() == SymbolKind.ATTRIBUTE)
            effective.add(new EffectiveMember(id, h.id(), List.of(h.id()), effective.size(), EffectiveMemberOrigin.LOCAL, false));
        inheritance.setEffectiveMembers(h.id(), List.copyOf(effective));
        return Optional.of(h.id());
    }

    private SymbolId member(SymbolHeader owner, String name, TypeId type, boolean parameter, boolean mandatory, int order) {
        var original = header(owner.languageVersion(), owner.name().value().substring(9) + "." + name,
                parameter ? SymbolKind.PARAMETER : SymbolKind.ATTRIBUTE, Optional.of(owner.id()), order, Set.of());
        var h = new SymbolHeader(original.id(), original.kind(), DeclaredName.declared(name), original.owner(), order, original.origin(), Optional.empty(),
                original.languageVersion(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of());
        state.defineSymbol(parameter ? new ParameterSymbol(h, type, mandatory, Optional.empty())
                : new AttributeSymbol(h, type, mandatory, false, false, false, Optional.empty(), List.of()));
        typeBindings.get().bindDeclaredType(h.id(), type); return h.id();
    }
    private SymbolHeader header(LanguageVersion language, String name, SymbolKind kind, Optional<SymbolId> owner, int order, Set<Modifier> modifiers) {
        return new SymbolHeader(new SymbolId("predefined:" + language + ":" + name), kind, owner.isEmpty() ? DeclaredName.synthetic("INTERLIS." + name) : DeclaredName.declared(name),
                owner, order, new Origin.PredefinedOrigin(language, "INTERLIS." + name), Optional.empty(), language, Documentation.empty(), MetaAttributeSet.empty(), modifiers, List.of());
    }
}
