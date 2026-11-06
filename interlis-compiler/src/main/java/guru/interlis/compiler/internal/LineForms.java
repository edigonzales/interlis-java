package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.*;
import guru.interlis.syntax.LineFormDeclarationSyntax;
import guru.interlis.syntax.LineTypeSyntax;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Bound line declarations and predefined segment types; state belongs to the compilation graph. */
final class LineForms {
    private final SemanticGraphRead.Symbols state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final NameBindings names;
    private final InheritanceBindings inheritance;
    private final ReferenceBindings references;
    private final Supplier<TypeBindings> typeBindings;
    private final UnitBindings units;
    private final Supplier<ExpressionBindings> expressions;
    private final Function<DeclInfo, SymbolHeader> symbolHeader;
    private final Function<DeclInfo, Symbol> ensureSymbol;

    LineForms(SemanticGraphRead.Symbols state,
              SemanticWork work,
              IndexedDeclarations index,
              SemanticDiagnostics diagnostics,
              NameBindings names,
              InheritanceBindings inheritance,
              ReferenceBindings references,
              Supplier<TypeBindings> typeBindings,
              UnitBindings units,
              Supplier<ExpressionBindings> expressions,
              Function<DeclInfo, SymbolHeader> symbolHeader,
              Function<DeclInfo, Symbol> ensureSymbol) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.names = names;
        this.inheritance = inheritance;
        this.references = references;
        this.typeBindings = typeBindings;
        this.units = units;
        this.expressions = expressions;
        this.symbolHeader = symbolHeader;
        this.ensureSymbol = ensureSymbol;
    }

    LineFormSymbol buildForm(DeclInfo decl) {
        var syntax = (LineFormDeclarationSyntax) decl.syntax();
        List<DiagnosticId> errors = new ArrayList<>();
        var target = names.bindTarget(decl, syntax.segmentStructure(), Set.of(SymbolKind.STRUCTURE), ReferenceUsage.TYPE_USE, errors);
        target.ifPresent(id -> {
            SymbolId base = id;
            Set<SymbolId> seen = new HashSet<>();
            while (base != null && seen.add(base)) { work.checkpoint(); base = inheritance.objectInheritanceBase(base).orElse(null); }
            SymbolId required = predefined(decl, "INTERLIS.LineSegment", Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            if (id.equals(required) || !guru.interlis.model.internal.TypeCompatibility.specializes(id, required, state::baseOf))
                diagnostics.emitRule(decl, "line form structure must extend INTERLIS.LineSegment", "LINE_FORM_STRUCTURE");
        });
        var model = index.models().stream().filter(e -> index.modelOfSymbol(decl.id()).filter(e.id()::equals).isPresent())
                .findFirst().orElseThrow().model().syntax();
        if (decl.language() == LanguageVersion.ILI23 && !model.contracted())
            diagnostics.emitRule(decl, "custom line forms require CONTRACTED in INTERLIS 2.3", "LINE_FORM_CONTRACTED");
        if (index.children(decl.owner().orElseThrow()).stream().anyMatch(other -> !other.id().equals(decl.id())
                && other.name().equals(decl.name()) && other.kind() != SymbolKind.PARAMETER))
            diagnostics.emitRule(decl, "line form name is not unique in its scope", "LINE_FORM_NAME");
        return new LineFormSymbol(symbolHeader.apply(decl), target);
    }

    GeometryType buildType(TypeId id, LineTypeSyntax syntax, DeclInfo decl) {
        List<DiagnosticId> errors = new ArrayList<>();
        List<SymbolId> forms = new ArrayList<>();
        for (var name : syntax.lineForms()) {
            work.checkpoint();
            var target = predefined(decl, name.text(), Set.of(SymbolKind.LINE_FORM));
            if (target.isPresent()) references.recordReference(decl, name, target.orElseThrow(), ReferenceUsage.TYPE_USE);
            else target = names.bindTarget(decl, name, Set.of(SymbolKind.LINE_FORM), ReferenceUsage.TYPE_USE, errors);
            target.ifPresent(forms::add);
        }
        var vertex = syntax.vertexDomain().flatMap(name -> names.bindTarget(decl, name, Set.of(SymbolKind.DOMAIN), ReferenceUsage.TYPE_USE, errors));
        vertex.ifPresent(target -> {
            index.byId(target).ifPresent(ensureSymbol::apply);
            var domain = (DomainSymbol)state.symbol(target);
            if (!(typeBindings.get().effectiveDescriptor(domain.typeId()) instanceof CoordinateType coordinate) || coordinate.multiCoordinate())
                diagnostics.emitRule(decl, "VERTEX must reference a coordinate domain", "LINE_VERTEX_COORDINATE");
        });
        if (decl.language() == LanguageVersion.ILI24 && syntax.lineAttributes().isPresent())
            diagnostics.emitRule(decl, "LINE ATTRIBUTES are not supported by INTERLIS 2.4", "LINE_ATTRIBUTES_VERSION");
        var attributes = syntax.lineAttributes().flatMap(name -> names.bindTarget(decl, name, Set.of(SymbolKind.STRUCTURE), ReferenceUsage.TYPE_USE, errors));
        Optional<GeometryType.Tolerance> overlap = syntax.overlap().map(DecimalLiteral::parse).map(GeometryType.Explicit::new);
        return new GeometryType(id, new Origin.DeclaredOrigin(syntax.range()), syntax.geometryKind(), forms,
                vertex, attributes, syntax.directed(), syntax.withoutOverlaps(), overlap);
    }

    /** Complete this declaration's owned descriptor before any expression sees it.
     * Aliases are read, never mutated; direction and LINE ATTRIBUTES in an explicit
     * line declaration retain their language meaning rather than being invented. */
    void effective(TypeId id, TypeDescriptor inherited, DeclInfo declaration) {
        if (!(state.type(id) instanceof GeometryType own)) return;
        GeometryType parent = inherited instanceof GeometryType p ? p : null;
        if (inherited != null && parent == null) diagnostics.emitRule(declaration, "geometry needs a geometric base", "GEOMETRY_EXTENSION");
        var forms = own.lineForms().isEmpty() && parent != null ? parent.lineForms() : own.lineForms();
        var vertex = own.vertexDomain().isEmpty() && parent != null ? parent.vertexDomain() : own.vertexDomain();
        var tolerance = own.overlap().isEmpty() && parent != null ? parent.overlap() : own.overlap();
        boolean surface = switch (own.geometryKind()) { case SURFACE, AREA, MULTISURFACE, MULTIAREA -> true; default -> false; };
        boolean without = own.withoutOverlaps() || parent != null && parent.withoutOverlaps() || surface;
        if (tolerance.isEmpty() && without && declaration.language() == LanguageVersion.ILI24 && vertex.isPresent()
                && typeBindings.get().effectiveDescriptor(state.declaredType(vertex.orElseThrow())) instanceof CoordinateType c
                && c.axes().stream().allMatch(a -> a.minimum().isPresent() && a.maximum().isPresent()))
            tolerance = Optional.of(new GeometryType.TrackWidth(vertex.orElseThrow()));
        var result = new GeometryType(id, own.origin(), own.geometryKind(), forms, vertex, own.lineAttributes(), own.directed(), without, tolerance);
        typeBindings.get().storeType(result);
        if (parent != null) {
            var relation = extension(result, parent);
            if (relation.decision() == RelationDecision.DISALLOWED)
                diagnostics.emitRule(declaration, "geometry extension: " + relation.reason(), "GEOMETRY_EXTENSION");
        }
        if (!result.lineAttributes().isEmpty() && !surface)
            diagnostics.emitRule(declaration, "LINE ATTRIBUTES require SURFACE or AREA", "LINE_ATTRIBUTES_FAMILY");
        boolean generic = declaration.language() == LanguageVersion.ILI24 && vertex.filter(this::genericVertex).isPresent();
        CoordinateType coordinate = vertex.map(state::declaredType).map(typeBindings.get()::effectiveDescriptor)
                .filter(CoordinateType.class::isInstance).map(CoordinateType.class::cast).orElse(null);
        if (coordinate != null && declaration.language() == LanguageVersion.ILI24) {
            var length = units.builtinUnit(declaration.id(), "INTERLIS.LENGTH").orElseThrow();
            for (var axis : coordinate.axes()) if (axis.unit().isPresent() && !units.unitExtends(axis.unit().orElseThrow(), length))
                diagnostics.emitRule(declaration, "line vertices require Cartesian coordinates with length units", "LINE_VERTEX_CARTESIAN");
            Integer scale = null;
            for (var axis : coordinate.axes()) {
                work.checkpoint();
                if (axis.minimum().isEmpty() || axis.maximum().isEmpty()) continue;
                int value = scale(axis.minimum().orElseThrow());
                if (value != scale(axis.maximum().orElseThrow()) || scale != null && scale != value)
                    diagnostics.emitRule(declaration, "line vertex axes must have the same numerical precision", "LINE_VERTEX_PRECISION");
                scale = value;
            }
        }
        if (declaration.language() == LanguageVersion.ILI23 && own.withoutOverlaps() && own.overlap().isEmpty())
            diagnostics.emitRule(declaration, "INTERLIS 2.3 WITHOUT OVERLAPS requires an explicit value", "OVERLAP_VERSION");
        if (own.overlap().orElse(null) instanceof GeometryType.Explicit explicit) {
            if (explicit.value().exactValue().signum() <= 0)
                diagnostics.emitRule(declaration, "overlap tolerance must be greater than zero", "OVERLAP_POSITIVE");
            if (declaration.language() == LanguageVersion.ILI24 && coordinate != null && coordinate.axes().getFirst().minimum().isPresent()
                    && scale(explicit.value()) != scale(coordinate.axes().getFirst().minimum().orElseThrow()))
                diagnostics.emitRule(declaration, "overlap tolerance must have the vertex precision", "OVERLAP_PRECISION");
        }
        if (incomplete(result) && !generic && !abstractContext(declaration))
            diagnostics.emitRule(declaration, "concrete geometry requires line forms, concrete vertices and the versioned tolerance", "GEOMETRY_INCOMPLETE");
    }

    TypeRelation extension(GeometryType derived, GeometryType base) {
        return guru.interlis.model.internal.TypeCompatibility.geometryExtension(derived, base, state::baseOf,
                id -> typeBindings.get().effectiveDescriptor(state.declaredType(id)));
    }

    private static int scale(DecimalLiteral value) {
        return value.precision().fractionalDigits() - value.precision().exponent();
    }

    private boolean genericVertex(SymbolId domain) {
        var seen = new HashSet<SymbolId>(); var pending = new ArrayDeque<SymbolId>(); pending.add(domain);
        while (!pending.isEmpty()) {
            work.checkpoint(); var id = pending.removeFirst(); if (!seen.add(id)) continue;
            if (state.symbol(id) instanceof DomainSymbol d && d.generic()) return true;
            if (state.type(state.declaredType(id)) instanceof DomainReferenceType alias) pending.add(alias.targetDomain());
            if (state.baseOf(id) != null) pending.add(state.baseOf(id));
        }
        return false;
    }

    private boolean abstractContext(DeclInfo declaration) {
        if (declaration.modifiers().contains(Modifier.ABSTRACT)) return true;
        return declaration.kind() == SymbolKind.ATTRIBUTE && declaration.owner().flatMap(index::byId)
                .filter(d -> d.modifiers().contains(Modifier.ABSTRACT)).isPresent();
    }

    private boolean incomplete(GeometryType geometry) {
        if (geometry.lineForms().isEmpty() || geometry.vertexDomain().isEmpty()) return true;
        var descriptor = typeBindings.get().effectiveDescriptor(state.declaredType(geometry.vertexDomain().orElseThrow()));
        if (state.symbol(geometry.vertexDomain().orElseThrow()).header().has(Modifier.ABSTRACT)) return true;
        if (!(descriptor instanceof CoordinateType c) || c.axes().stream().anyMatch(a -> a.minimum().isEmpty() || a.maximum().isEmpty())) return true;
        return geometry.withoutOverlaps() && geometry.overlap().isEmpty();
    }

    void validateConcreteMembers() {
        for (var entry : state.effectiveMembersInBuildOrder()) {
            work.checkpoint(); var owner = index.byId(entry.getKey()).orElse(null);
            if (owner == null || owner.modifiers().contains(Modifier.ABSTRACT)) continue;
            for (var member : entry.getValue()) {
                var symbol = state.symbol(member.declaredSymbol());
                if (!(symbol instanceof AttributeSymbol attribute)) continue;
                var type = typeBindings.get().effectiveDescriptor(state.declaredType(attribute.id()));
                if (type instanceof GeometryType geometry && (attribute.header().has(Modifier.ABSTRACT)
                        || incomplete(geometry) && !(owner.language() == LanguageVersion.ILI24 && geometry.vertexDomain().filter(this::genericVertex).isPresent())))
                    diagnostics.emitRule(owner, "concrete owner retains an abstract geometry member", "GEOMETRY_ABSTRACT_MEMBER");
            }
        }
    }

    Optional<SymbolId> predefined(DeclInfo context, String name, Set<SymbolKind> kinds) {
        if (name.equals("INTERLIS.LineCoord") && kinds.contains(SymbolKind.DOMAIN)) {
            lineCoord(context.language()); return Optional.of(id(context.language(), "LineCoord"));
        }
        boolean form = name.equals("STRAIGHTS") || name.equals("ARCS");
        if (form && !kinds.contains(SymbolKind.LINE_FORM)) return Optional.empty();
        if (!form && (!kinds.contains(SymbolKind.STRUCTURE) || !Set.of("INTERLIS.LineSegment", "INTERLIS.StartSegment",
                "INTERLIS.StraightSegment", "INTERLIS.ArcSegment", "INTERLIS.SurfaceEdge",
                "INTERLIS.SurfaceBoundary", "INTERLIS.LineGeometry").contains(name))) return Optional.empty();
        LanguageVersion language = context.language();
        SymbolId id = id(language, form ? name : name.substring(9));
        if (state.hasSymbol(id)) return Optional.of(id);
        if (form) {
            SymbolId structure = predefined(context, name.equals("STRAIGHTS") ? "INTERLIS.StraightSegment" : "INTERLIS.ArcSegment",
                    Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            state.defineSymbol(new LineFormSymbol(header(language, name, SymbolKind.LINE_FORM, Optional.empty(), Set.of()), Optional.of(structure)));
            return Optional.of(id);
        }
        String shortName = name.substring(9);
        if (Set.of("SurfaceEdge", "SurfaceBoundary", "LineGeometry").contains(shortName))
            return Optional.of(geometryStructure(context, shortName));
        Optional<SymbolId> base = shortName.equals("LineSegment") ? Optional.empty()
                : predefined(context, "INTERLIS.LineSegment", Set.of(SymbolKind.STRUCTURE));
        base.ifPresent(parent -> inheritance.bindBase(id, parent));
        var header = header(language, shortName, SymbolKind.STRUCTURE, Optional.empty(),
                Set.of(shortName.equals("LineSegment") ? Modifier.ABSTRACT : Modifier.FINAL));
        List<SymbolId> members = new ArrayList<>();
        if (shortName.equals("LineSegment")) members.add(attribute(language, id, "SegmentEndPoint", lineCoord(language), true));
        if (shortName.equals("ArcSegment")) {
            members.add(attribute(language, id, "ArcPoint", lineCoord(language), true));
            TypeId numeric = typeBindings.get().nextTypeId();
            var unit = units.builtinUnit(context.id(), "INTERLIS.LENGTH");
            typeBindings.get().storeType(new NumericType(numeric, header.origin(), new NumericRange(Optional.empty(), Optional.empty(), false,
                    NumericDirection.NONE, unit, Optional.empty())));
            members.add(attribute(language, id, "Radius", numeric, false));
        }
        state.defineSymbol(new StructureSymbol(header, base, members, List.of()));
        List<EffectiveMember> effective = new ArrayList<>();
        base.ifPresent(parent -> state.effectiveMembers(parent).forEach(member -> effective.add(
                new EffectiveMember(member.declaredSymbol(), id, List.of(parent, id), effective.size(), EffectiveMemberOrigin.INHERITED, false))));
        for (SymbolId member : members) effective.add(new EffectiveMember(member, id, List.of(id), effective.size(), EffectiveMemberOrigin.LOCAL, false));
        inheritance.setEffectiveMembers(id, List.copyOf(effective));
        return Optional.of(id);
    }

    /** Appendix-A declarations retain unspecified geometry rather than borrowing a source vertex. */
    private SymbolId geometryStructure(DeclInfo context, String name) {
        work.checkpoint();
        var language = context.language(); var h = header(language, name, SymbolKind.STRUCTURE, Optional.empty(), Set.of());
        List<SymbolId> members = new ArrayList<>();
        if (name.equals("SurfaceEdge")) {
            TypeId geometry = typeBindings.get().nextTypeId();
            typeBindings.get().storeType(new GeometryType(geometry, h.origin(), GeometryKind.POLYLINE, List.of(),
                    Optional.empty(), Optional.empty(), true, false, Optional.empty()));
            members.add(attribute(language, h.id(), "Geometry", geometry, false));
            if (language == LanguageVersion.ILI23) {
                TypeId attrs = typeBindings.get().nextTypeId(); typeBindings.get().storeType(new GenericType(attrs, h.origin(), "ANYSTRUCTURE"));
                members.add(attribute(language, h.id(), "LineAttrs", attrs, false, 1));
            }
        } else {
            String targetName = name.equals("SurfaceBoundary") ? "SurfaceEdge" : "LineSegment";
            var target = predefined(context, "INTERLIS." + targetName, Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            TypeId element = typeBindings.get().nextTypeId(); typeBindings.get().storeType(new StructureValueType(element, h.origin(), Optional.of(target), List.of()));
            TypeId list = typeBindings.get().nextTypeId(); typeBindings.get().storeType(new CollectionType(list, h.origin(), CollectionKind.LIST, Cardinality.unboundedFrom(0), element));
            members.add(attribute(language, h.id(), name.equals("SurfaceBoundary") ? "Lines" : "Segments", list, false));
        }
        state.defineSymbol(new StructureSymbol(h, Optional.empty(), members, List.of()));
        List<EffectiveMember> effective = new ArrayList<>();
        for (SymbolId member : members) effective.add(new EffectiveMember(member, h.id(), List.of(h.id()), effective.size(), EffectiveMemberOrigin.LOCAL, false));
        inheritance.setEffectiveMembers(h.id(), List.copyOf(effective));
        if (name.equals("LineGeometry")) {
            var segment = predefined(context, "INTERLIS.LineSegment", Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            var start = predefined(context, "INTERLIS.StartSegment", Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            var range = new guru.interlis.source.SourceRange(new guru.interlis.source.SourceId("predefined:/INTERLIS-" + language),
                    guru.interlis.source.SourceRevision.initial(), 0, 0);
            startConstraint(context, h.id(), segment, start, range);
        }
        return h.id();
    }

    void startConstraint(DeclInfo owner, SymbolId line, SymbolId segment, SymbolId start, guru.interlis.source.SourceRange range) {
        var structure = (StructureSymbol)state.symbol(line);
        if (!structure.constraints().isEmpty()) return;
        SymbolId attribute = structure.members().getFirst();
        TypeId element = typeBindings.get().nextTypeId(); typeBindings.get().storeType(new StructureValueType(element, structure.origin(), Optional.of(segment), List.of()));
        ExpressionId path = new ExpressionId(line.value() + ":first");
        expressions.get().storeExpression(new PathExpression(path, element, ExpressionTypeState.RESOLVED, range, List.of(), new ObjectPath(List.of(
                new PathStep.AttributeStep("Segments", attribute, range), new PathStep.IndexStep(new PathIndex(PathIndex.Kind.FIRST, Optional.empty()), element, range)))));
        TypeId classType = typeBindings.get().nextTypeId(); typeBindings.get().storeType(new ClassValueType(classType, structure.origin(), true, List.of(start)));
        ExpressionId value = new ExpressionId(line.value() + ":start");
        expressions.get().storeExpression(new SymbolValueExpression(value, classType, ExpressionTypeState.RESOLVED, range, start));
        TypeId bool = typeBindings.get().nextTypeId(); typeBindings.get().storeType(new BooleanType(bool, structure.origin()));
        ExpressionId call = new ExpressionId(line.value() + ":isStart");
        expressions.get().storeExpression(new FunctionCallExpression(call, bool, ExpressionTypeState.RESOLVED, range, List.of(path, value), "INTERLIS.isOfClass", Optional.of(expressions.get().builtinDeclaration(BuiltinFunctions.find("isOfClass").orElseThrow(),owner.language()).id()), Map.of()));
        SymbolId constraint = new SymbolId(line.value() + ":startConstraint");
        Origin constraintOrigin = structure.origin() instanceof Origin.PredefinedOrigin predefined
                ? new Origin.PredefinedOrigin(predefined.language(), predefined.key() + ".StartSegment")
                : new Origin.DerivedOrigin(((StructureSymbol)state.symbol(((Origin.DerivedOrigin)structure.origin()).declaredAt())).constraints().getFirst(), DerivationKind.CONTEXTUALIZATION);
        var header = new SymbolHeader(constraint, SymbolKind.CONSTRAINT, DeclaredName.synthetic("StartSegment"), Optional.of(line), 0,
                constraintOrigin, Optional.empty(), owner.language(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of());
        state.defineSymbol(new ConstraintSymbol(header, new MandatoryConstraint(Optional.empty(), call), Optional.empty()));
        state.replaceSymbol(new StructureSymbol(structure.header(), structure.baseType(), structure.members(), List.of(constraint)));
    }

    private TypeId lineCoord(LanguageVersion language) {
        SymbolId domain = id(language, "LineCoord");
        if (state.hasDeclaredType(domain)) return state.declaredType(domain);
        TypeId type = typeBindings.get().nextTypeId();
        var h = header(language, "LineCoord", SymbolKind.DOMAIN, Optional.empty(), Set.of(Modifier.ABSTRACT));
        typeBindings.get().storeType(new CoordinateType(type, h.origin(), 2, List.of(NumericRange.unbounded(), NumericRange.unbounded()), false, Optional.empty(), Optional.empty()));
        state.defineSymbol(new DomainSymbol(h, type, Optional.empty(), false)); typeBindings.get().bindDeclaredType(domain, type);
        return type;
    }

    private SymbolId attribute(LanguageVersion language, SymbolId owner, String name, TypeId type, boolean mandatory) {
        return attribute(language, owner, name, type, mandatory, 0);
    }
    private SymbolId attribute(LanguageVersion language, SymbolId owner, String name, TypeId type, boolean mandatory, int order) {
        var h = header(language, owner.value().substring(owner.value().lastIndexOf(':') + 1) + "." + name,
                SymbolKind.ATTRIBUTE, Optional.of(owner), Set.of());
        // Attribute names are local even though their predefined identity includes the owning structure.
        h = new SymbolHeader(h.id(), h.kind(), DeclaredName.declared(name), h.owner(), order, h.origin(), h.nameRange(),
                h.languageVersion(), h.documentation(), h.metaAttributes(), h.modifiers(), h.oidRules());
        state.defineSymbol(new AttributeSymbol(h, type, mandatory, false, false, false, Optional.empty(), List.of()));
        typeBindings.get().bindDeclaredType(h.id(), type); return h.id();
    }

    private SymbolId id(LanguageVersion language, String name) { return new SymbolId("predefined:" + language + ":" + name); }
    private SymbolHeader header(LanguageVersion language, String name, SymbolKind kind, Optional<SymbolId> owner, Set<Modifier> modifiers) {
        return new SymbolHeader(id(language, name), kind, owner.isEmpty() ? DeclaredName.synthetic("INTERLIS." + name) : DeclaredName.declared(name),
                owner, 0, new Origin.PredefinedOrigin(language, "INTERLIS." + name), Optional.empty(), language,
                Documentation.empty(), MetaAttributeSet.empty(), modifiers, List.of());
    }
}
