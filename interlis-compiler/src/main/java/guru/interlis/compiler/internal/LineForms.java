package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.*;
import guru.interlis.syntax.LineFormDeclarationSyntax;
import guru.interlis.syntax.LineTypeSyntax;
import java.util.*;

/** Bound line declarations and predefined segment types; state belongs to the compilation graph. */
final class LineForms {
    private final GraphBuilder g;
    LineForms(GraphBuilder graph) { g = graph; }

    LineFormSymbol buildForm(DeclInfo decl) {
        var syntax = (LineFormDeclarationSyntax) decl.syntax();
        List<DiagnosticId> errors = new ArrayList<>();
        var target = g.bindTarget(decl, syntax.segmentStructure(), Set.of(SymbolKind.STRUCTURE), ReferenceUsage.TYPE_USE, errors);
        target.ifPresent(id -> {
            SymbolId base = id;
            Set<SymbolId> seen = new HashSet<>();
            while (base != null && seen.add(base)) { g.checkWork(); base = g.objectInheritanceBase(base).orElse(null); }
            SymbolId required = predefined(decl, "INTERLIS.LineSegment", Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            if (id.equals(required) || !guru.interlis.model.internal.TypeCompatibility.specializes(id, required, g.baseOf::get))
                g.emitRule(decl, "line form structure must extend INTERLIS.LineSegment", "LINE_FORM_STRUCTURE");
        });
        var model = g.index.models().stream().filter(e -> g.index.modelOfSymbol(decl.id()).filter(e.id()::equals).isPresent())
                .findFirst().orElseThrow().model().syntax();
        if (decl.language() == LanguageVersion.ILI23 && !model.contracted())
            g.emitRule(decl, "custom line forms require CONTRACTED in INTERLIS 2.3", "LINE_FORM_CONTRACTED");
        if (g.index.children(decl.owner().orElseThrow()).stream().anyMatch(other -> !other.id().equals(decl.id())
                && other.name().equals(decl.name()) && other.kind() != SymbolKind.PARAMETER))
            g.emitRule(decl, "line form name is not unique in its scope", "LINE_FORM_NAME");
        return new LineFormSymbol(g.header(decl), target);
    }

    GeometryType buildType(TypeId id, LineTypeSyntax syntax, DeclInfo decl) {
        List<DiagnosticId> errors = new ArrayList<>();
        List<SymbolId> forms = new ArrayList<>();
        for (var name : syntax.lineForms()) {
            g.checkWork();
            var target = predefined(decl, name.text(), Set.of(SymbolKind.LINE_FORM));
            if (target.isPresent()) g.recordReference(decl, name, target.orElseThrow(), ReferenceUsage.TYPE_USE);
            else target = g.bindTarget(decl, name, Set.of(SymbolKind.LINE_FORM), ReferenceUsage.TYPE_USE, errors);
            target.ifPresent(forms::add);
        }
        var vertex = syntax.vertexDomain().flatMap(name -> g.bindTarget(decl, name, Set.of(SymbolKind.DOMAIN), ReferenceUsage.TYPE_USE, errors));
        vertex.ifPresent(target -> {
            g.index.byId(target).ifPresent(g::buildSymbol);
            var domain = (DomainSymbol)g.symbols.get(target);
            if (!(g.effectiveDescriptor(domain.typeId()) instanceof CoordinateType coordinate) || coordinate.multiCoordinate())
                g.emitRule(decl, "VERTEX must reference a coordinate domain", "LINE_VERTEX_COORDINATE");
        });
        if (decl.language() == LanguageVersion.ILI24 && syntax.lineAttributes().isPresent())
            g.emitRule(decl, "LINE ATTRIBUTES are not supported by INTERLIS 2.4", "LINE_ATTRIBUTES_VERSION");
        var attributes = syntax.lineAttributes().flatMap(name -> g.bindTarget(decl, name, Set.of(SymbolKind.STRUCTURE), ReferenceUsage.TYPE_USE, errors));
        Optional<GeometryType.Tolerance> overlap = syntax.overlap().map(DecimalLiteral::parse).map(GeometryType.Explicit::new);
        return new GeometryType(id, new Origin.DeclaredOrigin(syntax.range()), syntax.geometryKind(), forms,
                vertex, attributes, syntax.directed(), syntax.withoutOverlaps(), overlap);
    }

    /** Complete this declaration's owned descriptor before any expression sees it.
     * Aliases are read, never mutated; direction and LINE ATTRIBUTES in an explicit
     * line declaration retain their language meaning rather than being invented. */
    void effective(TypeId id, TypeDescriptor inherited, DeclInfo declaration) {
        if (!(g.types.get(id) instanceof GeometryType own)) return;
        GeometryType parent = inherited instanceof GeometryType p ? p : null;
        if (inherited != null && parent == null) g.emitRule(declaration, "geometry needs a geometric base", "GEOMETRY_EXTENSION");
        var forms = own.lineForms().isEmpty() && parent != null ? parent.lineForms() : own.lineForms();
        var vertex = own.vertexDomain().isEmpty() && parent != null ? parent.vertexDomain() : own.vertexDomain();
        var tolerance = own.overlap().isEmpty() && parent != null ? parent.overlap() : own.overlap();
        boolean surface = switch (own.geometryKind()) { case SURFACE, AREA, MULTISURFACE, MULTIAREA -> true; default -> false; };
        boolean without = own.withoutOverlaps() || parent != null && parent.withoutOverlaps() || surface;
        if (tolerance.isEmpty() && without && declaration.language() == LanguageVersion.ILI24 && vertex.isPresent()
                && g.effectiveDescriptor(g.declaredTypes.get(vertex.orElseThrow())) instanceof CoordinateType c
                && c.axes().stream().allMatch(a -> a.minimum().isPresent() && a.maximum().isPresent()))
            tolerance = Optional.of(new GeometryType.TrackWidth(vertex.orElseThrow()));
        var result = new GeometryType(id, own.origin(), own.geometryKind(), forms, vertex, own.lineAttributes(), own.directed(), without, tolerance);
        g.types.put(id, result);
        if (parent != null) {
            var relation = extension(result, parent);
            if (relation.decision() == RelationDecision.DISALLOWED)
                g.emitRule(declaration, "geometry extension: " + relation.reason(), "GEOMETRY_EXTENSION");
        }
        if (!result.lineAttributes().isEmpty() && !surface)
            g.emitRule(declaration, "LINE ATTRIBUTES require SURFACE or AREA", "LINE_ATTRIBUTES_FAMILY");
        boolean generic = declaration.language() == LanguageVersion.ILI24 && vertex.filter(this::genericVertex).isPresent();
        CoordinateType coordinate = vertex.map(g.declaredTypes::get).map(g::effectiveDescriptor)
                .filter(CoordinateType.class::isInstance).map(CoordinateType.class::cast).orElse(null);
        if (coordinate != null && declaration.language() == LanguageVersion.ILI24) {
            var length = g.builtinUnit(declaration.id(), "INTERLIS.LENGTH").orElseThrow();
            for (var axis : coordinate.axes()) if (axis.unit().isPresent() && !g.unitExtends(axis.unit().orElseThrow(), length))
                g.emitRule(declaration, "line vertices require Cartesian coordinates with length units", "LINE_VERTEX_CARTESIAN");
            Integer scale = null;
            for (var axis : coordinate.axes()) {
                g.checkWork();
                if (axis.minimum().isEmpty() || axis.maximum().isEmpty()) continue;
                int value = scale(axis.minimum().orElseThrow());
                if (value != scale(axis.maximum().orElseThrow()) || scale != null && scale != value)
                    g.emitRule(declaration, "line vertex axes must have the same numerical precision", "LINE_VERTEX_PRECISION");
                scale = value;
            }
        }
        if (declaration.language() == LanguageVersion.ILI23 && own.withoutOverlaps() && own.overlap().isEmpty())
            g.emitRule(declaration, "INTERLIS 2.3 WITHOUT OVERLAPS requires an explicit value", "OVERLAP_VERSION");
        if (own.overlap().orElse(null) instanceof GeometryType.Explicit explicit) {
            if (explicit.value().exactValue().signum() <= 0)
                g.emitRule(declaration, "overlap tolerance must be greater than zero", "OVERLAP_POSITIVE");
            if (declaration.language() == LanguageVersion.ILI24 && coordinate != null && coordinate.axes().getFirst().minimum().isPresent()
                    && scale(explicit.value()) != scale(coordinate.axes().getFirst().minimum().orElseThrow()))
                g.emitRule(declaration, "overlap tolerance must have the vertex precision", "OVERLAP_PRECISION");
        }
        if (incomplete(result) && !generic && !abstractContext(declaration))
            g.emitRule(declaration, "concrete geometry requires line forms, concrete vertices and the versioned tolerance", "GEOMETRY_INCOMPLETE");
    }

    TypeRelation extension(GeometryType derived, GeometryType base) {
        return guru.interlis.model.internal.TypeCompatibility.geometryExtension(derived, base, g.baseOf::get,
                id -> g.effectiveDescriptor(g.declaredTypes.get(id)));
    }

    private static int scale(DecimalLiteral value) {
        return value.precision().fractionalDigits() - value.precision().exponent();
    }

    private boolean genericVertex(SymbolId domain) {
        var seen = new HashSet<SymbolId>(); var pending = new ArrayDeque<SymbolId>(); pending.add(domain);
        while (!pending.isEmpty()) {
            g.checkWork(); var id = pending.removeFirst(); if (!seen.add(id)) continue;
            if (g.symbols.get(id) instanceof DomainSymbol d && d.generic()) return true;
            if (g.types.get(g.declaredTypes.get(id)) instanceof DomainReferenceType alias) pending.add(alias.targetDomain());
            if (g.baseOf.get(id) != null) pending.add(g.baseOf.get(id));
        }
        return false;
    }

    private boolean abstractContext(DeclInfo declaration) {
        if (declaration.modifiers().contains(Modifier.ABSTRACT)) return true;
        return declaration.kind() == SymbolKind.ATTRIBUTE && declaration.owner().flatMap(g.index::byId)
                .filter(d -> d.modifiers().contains(Modifier.ABSTRACT)).isPresent();
    }

    private boolean incomplete(GeometryType geometry) {
        if (geometry.lineForms().isEmpty() || geometry.vertexDomain().isEmpty()) return true;
        var descriptor = g.effectiveDescriptor(g.declaredTypes.get(geometry.vertexDomain().orElseThrow()));
        if (g.symbols.get(geometry.vertexDomain().orElseThrow()).header().has(Modifier.ABSTRACT)) return true;
        if (!(descriptor instanceof CoordinateType c) || c.axes().stream().anyMatch(a -> a.minimum().isEmpty() || a.maximum().isEmpty())) return true;
        return geometry.withoutOverlaps() && geometry.overlap().isEmpty();
    }

    void validateConcreteMembers() {
        for (var entry : g.effectiveMembers.entrySet()) {
            g.checkWork(); var owner = g.index.byId(entry.getKey()).orElse(null);
            if (owner == null || owner.modifiers().contains(Modifier.ABSTRACT)) continue;
            for (var member : entry.getValue()) {
                var symbol = g.symbols.get(member.declaredSymbol());
                if (!(symbol instanceof AttributeSymbol attribute)) continue;
                var type = g.effectiveDescriptor(g.declaredTypes.get(attribute.id()));
                if (type instanceof GeometryType geometry && (attribute.header().has(Modifier.ABSTRACT)
                        || incomplete(geometry) && !(owner.language() == LanguageVersion.ILI24 && geometry.vertexDomain().filter(this::genericVertex).isPresent())))
                    g.emitRule(owner, "concrete owner retains an abstract geometry member", "GEOMETRY_ABSTRACT_MEMBER");
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
        if (g.symbols.containsKey(id)) return Optional.of(id);
        if (form) {
            SymbolId structure = predefined(context, name.equals("STRAIGHTS") ? "INTERLIS.StraightSegment" : "INTERLIS.ArcSegment",
                    Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            g.symbols.put(id, new LineFormSymbol(header(language, name, SymbolKind.LINE_FORM, Optional.empty(), Set.of()), Optional.of(structure)));
            return Optional.of(id);
        }
        String shortName = name.substring(9);
        if (Set.of("SurfaceEdge", "SurfaceBoundary", "LineGeometry").contains(shortName))
            return Optional.of(geometryStructure(context, shortName));
        Optional<SymbolId> base = shortName.equals("LineSegment") ? Optional.empty()
                : predefined(context, "INTERLIS.LineSegment", Set.of(SymbolKind.STRUCTURE));
        base.ifPresent(parent -> g.baseOf.put(id, parent));
        var header = header(language, shortName, SymbolKind.STRUCTURE, Optional.empty(),
                Set.of(shortName.equals("LineSegment") ? Modifier.ABSTRACT : Modifier.FINAL));
        List<SymbolId> members = new ArrayList<>();
        if (shortName.equals("LineSegment")) members.add(attribute(language, id, "SegmentEndPoint", lineCoord(language), true));
        if (shortName.equals("ArcSegment")) {
            members.add(attribute(language, id, "ArcPoint", lineCoord(language), true));
            TypeId numeric = g.nextTypeId();
            var unit = g.builtinUnit(context.id(), "INTERLIS.LENGTH");
            g.types.put(numeric, new NumericType(numeric, header.origin(), new NumericRange(Optional.empty(), Optional.empty(), false,
                    NumericDirection.NONE, unit, Optional.empty())));
            members.add(attribute(language, id, "Radius", numeric, false));
        }
        g.symbols.put(id, new StructureSymbol(header, base, members, List.of()));
        List<EffectiveMember> effective = new ArrayList<>();
        base.ifPresent(parent -> g.effectiveMembers.getOrDefault(parent, List.of()).forEach(member -> effective.add(
                new EffectiveMember(member.declaredSymbol(), id, List.of(parent, id), effective.size(), EffectiveMemberOrigin.INHERITED, false))));
        for (SymbolId member : members) effective.add(new EffectiveMember(member, id, List.of(id), effective.size(), EffectiveMemberOrigin.LOCAL, false));
        g.effectiveMembers.put(id, List.copyOf(effective));
        return Optional.of(id);
    }

    /** Appendix-A declarations retain unspecified geometry rather than borrowing a source vertex. */
    private SymbolId geometryStructure(DeclInfo context, String name) {
        g.checkWork();
        var language = context.language(); var h = header(language, name, SymbolKind.STRUCTURE, Optional.empty(), Set.of());
        List<SymbolId> members = new ArrayList<>();
        if (name.equals("SurfaceEdge")) {
            TypeId geometry = g.nextTypeId();
            g.types.put(geometry, new GeometryType(geometry, h.origin(), GeometryKind.POLYLINE, List.of(),
                    Optional.empty(), Optional.empty(), true, false, Optional.empty()));
            members.add(attribute(language, h.id(), "Geometry", geometry, false));
            if (language == LanguageVersion.ILI23) {
                TypeId attrs = g.nextTypeId(); g.types.put(attrs, new GenericType(attrs, h.origin(), "ANYSTRUCTURE"));
                members.add(attribute(language, h.id(), "LineAttrs", attrs, false, 1));
            }
        } else {
            String targetName = name.equals("SurfaceBoundary") ? "SurfaceEdge" : "LineSegment";
            var target = predefined(context, "INTERLIS." + targetName, Set.of(SymbolKind.STRUCTURE)).orElseThrow();
            TypeId element = g.nextTypeId(); g.types.put(element, new StructureValueType(element, h.origin(), Optional.of(target), List.of()));
            TypeId list = g.nextTypeId(); g.types.put(list, new CollectionType(list, h.origin(), CollectionKind.LIST, Cardinality.unboundedFrom(0), element));
            members.add(attribute(language, h.id(), name.equals("SurfaceBoundary") ? "Lines" : "Segments", list, false));
        }
        g.symbols.put(h.id(), new StructureSymbol(h, Optional.empty(), members, List.of()));
        List<EffectiveMember> effective = new ArrayList<>();
        for (SymbolId member : members) effective.add(new EffectiveMember(member, h.id(), List.of(h.id()), effective.size(), EffectiveMemberOrigin.LOCAL, false));
        g.effectiveMembers.put(h.id(), List.copyOf(effective));
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
        var structure = (StructureSymbol)g.symbols.get(line);
        if (!structure.constraints().isEmpty()) return;
        SymbolId attribute = structure.members().getFirst();
        TypeId element = g.nextTypeId(); g.types.put(element, new StructureValueType(element, structure.origin(), Optional.of(segment), List.of()));
        ExpressionId path = new ExpressionId(line.value() + ":first");
        g.expressions.put(path, new PathExpression(path, element, ExpressionTypeState.RESOLVED, range, List.of(), new ObjectPath(List.of(
                new PathStep.AttributeStep("Segments", attribute, range), new PathStep.IndexStep(new PathIndex(PathIndex.Kind.FIRST, Optional.empty()), element, range)))));
        TypeId classType = g.nextTypeId(); g.types.put(classType, new ClassValueType(classType, structure.origin(), true, List.of(start)));
        ExpressionId value = new ExpressionId(line.value() + ":start");
        g.expressions.put(value, new SymbolValueExpression(value, classType, ExpressionTypeState.RESOLVED, range, start));
        TypeId bool = g.nextTypeId(); g.types.put(bool, new BooleanType(bool, structure.origin()));
        ExpressionId call = new ExpressionId(line.value() + ":isStart");
        g.expressions.put(call, new FunctionCallExpression(call, bool, ExpressionTypeState.RESOLVED, range, List.of(path, value), "INTERLIS.isOfClass", Optional.of(g.builtinDeclaration(BuiltinFunctions.find("isOfClass").orElseThrow(),owner.language()).id()), Map.of()));
        SymbolId constraint = new SymbolId(line.value() + ":startConstraint");
        Origin constraintOrigin = structure.origin() instanceof Origin.PredefinedOrigin predefined
                ? new Origin.PredefinedOrigin(predefined.language(), predefined.key() + ".StartSegment")
                : new Origin.DerivedOrigin(((StructureSymbol)g.symbols.get(((Origin.DerivedOrigin)structure.origin()).declaredAt())).constraints().getFirst(), DerivationKind.CONTEXTUALIZATION);
        var header = new SymbolHeader(constraint, SymbolKind.CONSTRAINT, DeclaredName.synthetic("StartSegment"), Optional.of(line), 0,
                constraintOrigin, Optional.empty(), owner.language(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of());
        g.symbols.put(constraint, new ConstraintSymbol(header, new MandatoryConstraint(Optional.empty(), call), Optional.empty()));
        g.symbols.put(line, new StructureSymbol(structure.header(), structure.baseType(), structure.members(), List.of(constraint)));
    }

    private TypeId lineCoord(LanguageVersion language) {
        SymbolId domain = id(language, "LineCoord");
        if (g.declaredTypes.containsKey(domain)) return g.declaredTypes.get(domain);
        TypeId type = g.nextTypeId();
        var h = header(language, "LineCoord", SymbolKind.DOMAIN, Optional.empty(), Set.of(Modifier.ABSTRACT));
        g.types.put(type, new CoordinateType(type, h.origin(), 2, List.of(NumericRange.unbounded(), NumericRange.unbounded()), false, Optional.empty(), Optional.empty()));
        g.symbols.put(domain, new DomainSymbol(h, type, Optional.empty(), false)); g.declaredTypes.put(domain, type);
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
        g.symbols.put(h.id(), new AttributeSymbol(h, type, mandatory, false, false, false, Optional.empty(), List.of()));
        g.declaredTypes.put(h.id(), type); return h.id();
    }

    private SymbolId id(LanguageVersion language, String name) { return new SymbolId("predefined:" + language + ":" + name); }
    private SymbolHeader header(LanguageVersion language, String name, SymbolKind kind, Optional<SymbolId> owner, Set<Modifier> modifiers) {
        return new SymbolHeader(id(language, name), kind, owner.isEmpty() ? DeclaredName.synthetic("INTERLIS." + name) : DeclaredName.declared(name),
                owner, 0, new Origin.PredefinedOrigin(language, "INTERLIS." + name), Optional.empty(), language,
                Documentation.empty(), MetaAttributeSet.empty(), modifiers, List.of());
    }
}
