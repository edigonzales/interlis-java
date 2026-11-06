package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.ClassValueType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GenericType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.DeclaredName;
import guru.interlis.model.Documentation;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.ErrorType;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.BooleanTypeSyntax;
import guru.interlis.syntax.AlignmentTypeSyntax;
import guru.interlis.syntax.BlackboxTypeSyntax;
import guru.interlis.syntax.ClassValueTypeSyntax;
import guru.interlis.syntax.CollectionTypeSyntax;
import guru.interlis.syntax.OidTypeSyntax;
import guru.interlis.syntax.CoordinateTypeSyntax;
import guru.interlis.syntax.DomainDeclarationSyntax;
import guru.interlis.syntax.FormattedTypeSyntax;
import guru.interlis.syntax.GenericTypeSyntax;
import guru.interlis.syntax.EnumTreeValueSyntax;
import guru.interlis.syntax.LineTypeSyntax;
import guru.interlis.syntax.NumericTypeSyntax;
import guru.interlis.syntax.FunctionDeclarationSyntax;
import guru.interlis.syntax.DomainReferenceSyntax;
import guru.interlis.syntax.EnumerationTypeSyntax;
import guru.interlis.syntax.ReferenceTypeSyntax;
import guru.interlis.syntax.TemporalTypeSyntax;
import guru.interlis.syntax.TextTypeSyntax;
import guru.interlis.syntax.TypeSyntax;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Binds declared types on demand and completes their effective descriptors. */
final class TypeBindings {
    private final GraphBuilder g;
    private final SemanticBuildState state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final Set<SymbolId> resolvingDomainTypes = new HashSet<>();
    private final Set<SymbolId> resolvingAttributeTypes = new HashSet<>();
    private final Map<guru.interlis.model.AttributePathType.Argument, TypeId> functionArgumentTypes = new LinkedHashMap<>();
    private final Set<guru.interlis.model.AttributePathType.Argument> resolvingFunctionArguments = new HashSet<>();
    private final Map<SymbolId, List<ExpressionId>> inferredDerivations = new LinkedHashMap<>();
    private final Map<guru.interlis.model.LanguageVersion,TypeId> booleanTypes = new java.util.EnumMap<>(guru.interlis.model.LanguageVersion.class);

    TypeBindings(GraphBuilder graph, SemanticBuildState state, SemanticWork work,
                   IndexedDeclarations index, SemanticDiagnostics diagnostics) {
        this.g = graph;
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
    }

    guru.interlis.model.TypeRelation extension(TypeDescriptor derived, TypeDescriptor base, guru.interlis.model.LanguageVersion language) {
        return guru.interlis.model.internal.TypeCompatibility.extend(derived,base,language,this::effectiveDescriptor,
                state::baseOf,id->effectiveDescriptor(state.declaredType(id)),g.units::extensionUnit);
    }

    TypeId functionArgumentType(DeclInfo function, int position) {
        var key = new guru.interlis.model.AttributePathType.Argument(function.id(), position);
        TypeId bound = functionArgumentTypes.get(key);
        if (bound != null) return bound;
        if (!resolvingFunctionArguments.add(key)) {
            TypeId error = nextTypeId();
            state.storeType(new ErrorType(error, function.origin(), List.of(diagnostics.emitRule(function,
                    "cyclic attribute-path argument dependency", "FUNCTION_ARGUMENT_CYCLE"))));
            return error;
        }
        var syntax = (FunctionDeclarationSyntax) function.syntax();
        TypeId type = buildType(syntax.parameters().get(position).type(), function.id(), function.sourceId());
        functionArgumentTypes.put(key, type); resolvingFunctionArguments.remove(key);
        return type;
    }

    /** Bind referenced types before expressions without recursively building their constraints. */
    TypeId domainType(DeclInfo decl) {
        TypeId existing = state.declaredType(decl.id());
        if (existing != null) return existing;
        work.checkpoint();
        if (!resolvingDomainTypes.add(decl.id())) {
            TypeId error = nextTypeId();
            state.storeType(new ErrorType(error, decl.origin(),
                    List.of(diagnostics.emitRule(decl, "cyclic domain type dependency", "DOMAIN_TYPE_CYCLE"))));
            state.bindDeclaredType(decl.id(), error);
            return error;
        }
        try {
            DomainDeclarationSyntax syntax = (DomainDeclarationSyntax) decl.syntax();
            Optional<SymbolId> base=syntax.baseDomain().flatMap(name->g.names.bindTarget(decl,name,Set.of(SymbolKind.DOMAIN),ReferenceUsage.BASE_TYPE,new ArrayList<>()));

            base.ifPresent(id -> {
                state.bindBase(decl.id(), id);
                index.byId(id).ifPresent(this::domainType);
                if ((state.symbol(id) != null && state.symbol(id).header().has(guru.interlis.model.Modifier.FINAL))
                        || index.byId(id).filter(d -> d.modifiers().contains(guru.interlis.model.Modifier.FINAL)).isPresent())
                    diagnostics.emitRule(decl, "cannot extend FINAL domain", "DOMAIN_FINAL");
            });
            TypeId type;
            if (syntax.type() instanceof GenericTypeSyntax generic && "INHERITED".equals(generic.name()) && base.isPresent()) {
                type = nextTypeId();
                state.storeType(new DomainReferenceType(type, new Origin.DeclaredOrigin(syntax.type().range()), base.orElseThrow()));
            } else type = buildType(syntax.type(), decl.id(), decl.sourceId());
            TypeDescriptor inherited = base.map(state::declaredType).map(this::effectiveDescriptor).orElse(null);
            inheritTypeProperties(type, syntax.type(), inherited,decl);
            if (syntax.type() instanceof LineTypeSyntax) g.lines.effective(type, inherited, decl);
            else if (inherited instanceof GeometryType && !(effectiveDescriptor(type) instanceof GeometryType))
                diagnostics.emitRule(decl, "geometry base requires a geometric extension", "GEOMETRY_EXTENSION");
            if (effectiveDescriptor(type) instanceof NumericType numeric && inherited instanceof NumericType parent) {
                var relation = g.units.numericExtension(numeric, parent, decl.language());
                if (relation.decision() == guru.interlis.model.RelationDecision.DISALLOWED)
                    diagnostics.emitRule(decl, "numeric type does not specialize its base: " + relation.reason(), relation.reason().startsWith("numeric-unit") || relation.reason().contains("unit-") ? "NUMERIC_UNIT_EXTENSION" : "NUMERIC_EXTENSION");
            }
            state.bindDeclaredTypeIfAbsent(decl.id(), type);
            if (syntax.type() instanceof EnumerationTypeSyntax enumeration) {
                if (base.isPresent() && inherited == null) diagnostics.emitRule(decl, "enumeration base must already be defined", "ENUM_BASE");
                state.storeType(effectiveEnumeration(type, enumeration, inherited, decl));
            }
            TypeDescriptor own=effectiveDescriptor(type);
            if(inherited!=null && !(inherited instanceof NumericType && own instanceof NumericType)
                    && !(own instanceof GeometryType) && !(inherited instanceof guru.interlis.model.OidType)
                    && !(own instanceof ErrorType) && !(inherited instanceof ErrorType)) {
                var relation=extension(own,inherited,decl.language());
                if(relation.decision()==guru.interlis.model.RelationDecision.DISALLOWED)
                    diagnostics.emitRule(decl,"domain does not specialize its base: "+relation.reason(),"TYPE_EXTENSION");
            }
            if (base.isPresent() && effectiveDescriptor(state.declaredType(base.orElseThrow())) instanceof guru.interlis.model.OidType oid
                    && (Optional.ofNullable(state.symbol(base.orElseThrow()))
                            .filter(s -> s.name().value().equals("INTERLIS.NOOID")).isPresent()
                    || oid.oidKind() != guru.interlis.model.OidKind.ANY
                    || !(effectiveDescriptor(type) instanceof guru.interlis.model.OidType derived)
                    || derived.oidKind() == guru.interlis.model.OidKind.ANY))
                diagnostics.emitRule(decl, "concrete OID domain cannot be extended", "OID_EXTENSION");
            return state.declaredType(decl.id());
        } finally {
            resolvingDomainTypes.remove(decl.id());
        }
    }

    private EnumerationType effectiveEnumeration(TypeId id, EnumerationTypeSyntax syntax,
            TypeDescriptor inherited, DeclInfo declaration) {
        EnumerationType base = inherited instanceof EnumerationType e ? e : null;
        if (inherited != null && (base == null || base.treeValues())) diagnostics.emitRule(declaration, "enumeration definition requires an enumeration definition as base", "ENUM_BASE");
        if (base != null && base.id().equals(id)) diagnostics.emitRule(declaration, "cyclic enumeration inheritance", "ENUM_BASE");
        if (base != null && base.circular()) diagnostics.emitRule(declaration, "circular enumeration cannot be extended", "ENUM_CIRCULAR");
        if (base != null && syntax.circular()) diagnostics.emitRule(declaration, "enumeration extension cannot introduce circular ordering", "ENUM_ORDER");
        Map<String, EnumerationValue> values = new LinkedHashMap<>();
        if (base != null) base.values().forEach(v -> values.put(v.path(), v));
        Set<String> own = new LinkedHashSet<>();
        Set<String> finals = new LinkedHashSet<>(base == null ? Set.of() : base.finalSubtrees());
        for (var value : syntax.values()) {
            work.checkpoint();
            String path = value.path();
            String parent = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
            if (value.qualified() && (base == null || base.values().stream().noneMatch(v -> v.path().equals(path))))
                diagnostics.emitRule(declaration, "qualified enum name must identify an inherited element", "ENUM_PARENT");
            if (!own.add(path)) diagnostics.emitRule(declaration, "duplicate enumeration value " + path, "ENUM_VALUE_UNIQUE");
            if (values.containsKey(path)) {
                if (!value.hasChildren()) diagnostics.emitRule(declaration, "extended enumeration repeats inherited value " + path, "ENUM_VALUE_UNIQUE");
            } else {
                if (!parent.isEmpty() && !values.containsKey(parent)) diagnostics.emitRule(declaration, "unknown enumeration parent " + parent, "ENUM_PARENT");
                if (finals.contains(parent)) diagnostics.emitRule(declaration, "enumeration sibling list is FINAL: " + parent, "ENUM_FINAL");
                values.put(path, new EnumerationValue(path, 0, Optional.of(value.range())));
            }
        }
        finals.addAll(syntax.finalSubtrees());
        // Preserve inherited sibling order and place new descendants under their parent.
        List<EnumerationValue> ordered = new ArrayList<>();
        appendEnumerationChildren("", values, ordered);
        return new EnumerationType(id, new Origin.DeclaredOrigin(syntax.range()), ordered,
                syntax.ordered() || syntax.circular() || base != null && base.ordered(), syntax.circular(), finals, Optional.ofNullable(base).map(EnumerationType::id), false);
    }

    private void appendEnumerationChildren(String parent, Map<String, EnumerationValue> values, List<EnumerationValue> result) {
        for (var value : values.values()) {
            work.checkpoint();
            String path = value.path();
            String candidate = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
            if (candidate.equals(parent)) {
                result.add(new EnumerationValue(path, result.size(), value.range()));
                appendEnumerationChildren(path, values, result);
            }
        }
    }

    boolean mandatoryDomain(SymbolId domain) {
        Set<SymbolId> seen = new HashSet<>();
        for (SymbolId current = domain; current != null && seen.add(current); current = state.baseOf(current)) {
            work.checkpoint();
            if (index.byId(current).filter(d -> d.modifiers().contains(guru.interlis.model.Modifier.MANDATORY)).isPresent()) return true;
            if (state.symbol(current) instanceof DomainSymbol d && d.mandatory()) return true;
        }
        return false;
    }

    boolean mandatoryType(TypeId type) {
        Set<TypeId> seen = new HashSet<>();
        while (type != null && seen.add(type) && state.type(type) instanceof DomainReferenceType reference) {
            if (mandatoryDomain(reference.targetDomain())) return true;
            type = state.declaredType(reference.targetDomain());
        }
        return false;
    }

    /** Complete only owned inline types; referenced alias descriptors are never mutated. */
    private void inheritTypeProperties(TypeId id, TypeSyntax syntax, TypeDescriptor base,DeclInfo declaration) {
        if (syntax instanceof NumericTypeSyntax own && state.type(id) instanceof NumericType numeric && base instanceof NumericType parent) {
            var range=inheritRange(numeric.range(),parent.range(),own);var errors=new ArrayList<DiagnosticId>();
            g.units.validateNumericUnit(range,declaration,syntax.range(),errors);
            state.storeType(errors.isEmpty()?new NumericType(id,numeric.origin(),range):new ErrorType(id,numeric.origin(),errors));
        }
        else if(syntax instanceof CoordinateTypeSyntax own && state.type(id) instanceof CoordinateType coordinate && base instanceof CoordinateType parent) {
            List<NumericRange> axes=new ArrayList<>(coordinate.axes());
            for(int i=0;i<Math.min(axes.size(),parent.axes().size());i++)axes.set(i,inheritRange(axes.get(i),parent.axes().get(i),own.axes().get(i)));
            var errors=new ArrayList<DiagnosticId>();
            for(int i=0;i<axes.size();i++)g.units.validateNumericUnit(axes.get(i),declaration,own.axes().get(i).range(),errors);
            state.storeType(errors.isEmpty()?new CoordinateType(id,coordinate.origin(),coordinate.dimensions(),axes,coordinate.multiCoordinate(),
                    coordinate.referenceSystem().isPresent()?coordinate.referenceSystem():parent.referenceSystem(),
                    coordinate.rotation().isPresent()?coordinate.rotation():parent.rotation()):new ErrorType(id,coordinate.origin(),errors));
        } else if(syntax instanceof CollectionTypeSyntax own && state.type(id) instanceof CollectionType collection && base instanceof CollectionType parent)
            inheritTypeProperties(collection.elementType(),own.elementType(),effectiveDescriptor(parent.elementType()),declaration);
    }

    private NumericRange inheritRange(NumericRange own, NumericRange base, NumericTypeSyntax syntax) {
        return new NumericRange(own.minimum(),own.maximum(),own.circular()||base.circular(),
                syntax.direction()==guru.interlis.model.NumericDirection.NONE?base.direction():own.direction(),
                syntax.unit().isEmpty()?base.unit():own.unit(),syntax.referenceSystem().isEmpty()?base.referenceSystem():own.referenceSystem());
    }

    boolean assignable(TypeId actual, TypeId expected) {
        if(mandatoryType(expected) && guru.interlis.model.internal.TypeCompatibility.isUndefined(effectiveDescriptor(actual)))return false;
        return guru.interlis.model.internal.TypeCompatibility.assign(effectiveDescriptor(actual), effectiveDescriptor(expected), this::effectiveDescriptor, state::baseOf, domain -> effectiveDescriptor(state.declaredType(domain))).decision() == guru.interlis.model.RelationDecision.ALLOWED;
    }

    /** Resolve once so every expression and the published attribute share the same type. */
    TypeId attributeType(DeclInfo decl) {
        TypeId existing = state.declaredType(decl.id());
        if (existing != null) return existing;
        work.checkpoint();
        AttributeDeclarationSyntax syntax = (AttributeDeclarationSyntax) decl.syntax();
        TypeId type;
        if (!resolvingAttributeTypes.add(decl.id())) {
            type = nextTypeId();
            state.storeType(new ErrorType(type, decl.origin(),
                    List.of(diagnostics.emitRule(decl, "cyclic inherited attribute type", "INHERITANCE_CYCLE"))));
            return type;
        }
        try {
            decl.owner().flatMap(g::objectInheritanceBase).flatMap(base->g.paths.findMember(base,decl.name()))
                    .filter(base->state.symbol(base) instanceof AttributeSymbol || index.byId(base).filter(d->d.kind()==SymbolKind.ATTRIBUTE).isPresent())
                    .ifPresent(base->state.bindBase(decl.id(),base));
            if (syntax.type() instanceof GenericTypeSyntax inferred && "INFERRED".equals(inferred.name())) {
                var result = g.expressions.buildExpression(syntax.derivations().getFirst(), decl.owner().orElseThrow());
                inferredDerivations.put(decl.id(), List.of(result.id()));
                type = result.typeId();
            } else if (syntax.type() instanceof GenericTypeSyntax generic && "INHERITED".equals(generic.name())) {
                Optional<SymbolId> base = decl.owner().flatMap(g::objectInheritanceBase)
                        .flatMap(owner -> g.paths.findMember(owner, decl.name()));
                DeclInfo inherited = base.flatMap(index::byId).orElse(null);
                if (inherited != null && inherited.kind() == SymbolKind.ATTRIBUTE) {
                    type = attributeType(inherited);
                } else if (base.map(state::symbol).orElse(null) instanceof AttributeSymbol attribute) {
                    // ALL OF members have a bound type but no separate syntax declaration.
                    type = attribute.declaredType();
                } else {
                    type = nextTypeId();
                    state.storeType(new ErrorType(type, decl.origin(), List.of(diagnostics.emitRule(decl,
                            "MANDATORY without a type requires an inherited attribute", "ATTRIBUTE_INHERITED_TYPE"))));
                }
            } else {
                type = buildType(syntax.type(), decl.id(), decl.sourceId());
                TypeDescriptor inheritedGeometry = null;
                if (decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                    var base = decl.owner().flatMap(g::objectInheritanceBase).flatMap(owner -> g.paths.findMember(owner, decl.name()));
                    TypeId baseType = base.flatMap(index::byId).filter(d -> d.kind() == SymbolKind.ATTRIBUTE)
                            .map(this::attributeType).orElseGet(() -> base.map(state::symbol)
                                    .filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                                    .map(AttributeSymbol::declaredType).orElse(null));
                    inheritTypeProperties(type, syntax.type(), effectiveDescriptor(baseType),decl);
                    inheritedGeometry = effectiveDescriptor(baseType);
                }
                if (syntax.type() instanceof LineTypeSyntax) g.lines.effective(type, inheritedGeometry, decl);
            }
            state.bindDeclaredType(decl.id(), type);
            return type;
        } finally {
            resolvingAttributeTypes.remove(decl.id());
        }
    }

    /** Simple predefined domains allowed by DomainRef. No fallback for unknown names. */
    Optional<SymbolId> builtinSimpleDomain(DeclInfo context, String name) {
        if (!Set.of("INTERLIS.URI", "INTERLIS.NAME", "INTERLIS.BOOLEAN", "INTERLIS.INTERLIS_1_DATE", "INTERLIS.HALIGNMENT", "INTERLIS.VALIGNMENT").contains(name))
            return Optional.empty();
        SymbolId symbol = new SymbolId("predefined:" + context.language() + ":domain:" + name);
        if (state.hasSymbol(symbol)) return Optional.of(symbol);
        Origin origin = new Origin.PredefinedOrigin(context.language(), name);
        TypeId type = nextTypeId();
        TypeDescriptor descriptor=switch(name) {
            case "INTERLIS.URI" -> new TextType(type,origin,TextKind.URI,java.util.OptionalInt.of(1023));
            case "INTERLIS.NAME" -> new TextType(type,origin,TextKind.NAME,java.util.OptionalInt.of(255));
            case "INTERLIS.BOOLEAN" -> new guru.interlis.model.BooleanType(type,origin);
            case "INTERLIS.INTERLIS_1_DATE" -> new TextType(type,origin,TextKind.TEXT,java.util.OptionalInt.of(8));
            default -> new guru.interlis.model.AlignmentType(type,origin,name.equals("INTERLIS.HALIGNMENT")
                    ? guru.interlis.model.AlignmentKind.HORIZONTAL : guru.interlis.model.AlignmentKind.VERTICAL);
        };
        state.storeType(descriptor);
        state.bindDeclaredType(symbol, type);
        state.defineSymbol(new DomainSymbol(new SymbolHeader(symbol, SymbolKind.DOMAIN,
                DeclaredName.synthetic(name), Optional.empty(), 0, origin, Optional.empty(), context.language(),
                Documentation.empty(), MetaAttributeSet.empty(), Set.of(guru.interlis.model.Modifier.FINAL), List.of()),
                type, Optional.empty(), false));
        return Optional.of(symbol);
    }

    TypeId buildType(TypeSyntax syntax, SymbolId context, SourceId source) {
        work.checkpoint();
        Objects.requireNonNull(syntax, "syntax");
        TypeId id = nextTypeId();
        TypeDescriptor descriptor = switch (syntax) {
            case TextTypeSyntax text -> {
                if (text.maxLength().isPresent() && text.maxLength().getAsInt() == 0)
                    yield new ErrorType(id, new Origin.DeclaredOrigin(text.range()), List.of(diagnostics.emitRule(index.byId(context).orElseThrow(),
                            "declared text maximum length must be positive", "TEXT_LENGTH", text.range())));
                yield new TextType(id, new Origin.DeclaredOrigin(text.range()), text.textKind(),
                    text.maxLength().isPresent() ? text.maxLength() : switch (text.textKind()) {
                        case NAME -> java.util.OptionalInt.of(255);
                        case URI -> java.util.OptionalInt.of(1023);
                        default -> java.util.OptionalInt.empty();
                    });
            }
            case BooleanTypeSyntax bool -> new guru.interlis.model.BooleanType(id,
                    new Origin.DeclaredOrigin(bool.range()));
            case TemporalTypeSyntax syntaxTime -> {
                String name = switch (syntaxTime.temporalKind()) {
                    case DATE -> "XMLDate"; case TIMEOFDAY -> "XMLTime"; case DATETIME -> "XMLDateTime";
                };
                var domain = g.temporal.find(index.byId(context).orElseThrow(), "INTERLIS." + name, Set.of(SymbolKind.DOMAIN)).orElseThrow();
                yield new DomainReferenceType(id, new Origin.DeclaredOrigin(syntaxTime.range()), domain);
            }
            case OidTypeSyntax oid -> {
                Optional<TypeId> carrier = oid.valueType().map(t -> buildType(t, context, source));
                if (oid.oidKind() == guru.interlis.model.OidKind.UUID) {
                    TypeId text = nextTypeId(); state.storeType(new TextType(text, new Origin.DeclaredOrigin(oid.range()), TextKind.TEXT, java.util.OptionalInt.of(36)));
                    carrier = Optional.of(text);
                }
                yield new guru.interlis.model.OidType(id, new Origin.DeclaredOrigin(oid.range()), oid.oidKind(), carrier);
            }
            case BlackboxTypeSyntax blackbox -> new guru.interlis.model.BlackboxType(id,
                    new Origin.DeclaredOrigin(blackbox.range()), blackbox.blackboxKind());
            case AlignmentTypeSyntax alignment -> new guru.interlis.model.AlignmentType(id,
                    new Origin.DeclaredOrigin(alignment.range()), alignment.alignment());
            case EnumTreeValueSyntax enumTree -> {
                List<DiagnosticId> errors = new ArrayList<>();
                Optional<SymbolId> domain = g.names.bindTarget(index.byId(context).orElseThrow(), enumTree.target(),
                        Set.of(SymbolKind.DOMAIN), ReferenceUsage.TYPE_USE, errors);
                if (domain.isEmpty()) yield new ErrorType(id, new Origin.DeclaredOrigin(enumTree.range()), errors);
                index.byId(domain.orElseThrow()).ifPresent(this::domainType);
                TypeDescriptor base=effectiveDescriptor(state.declaredType(domain.orElseThrow()));
                if(!(base instanceof EnumerationType enumeration) || enumeration.treeValues())
                    yield new ErrorType(id,new Origin.DeclaredOrigin(enumTree.range()),List.of(diagnostics.emitRule(index.byId(context).orElseThrow(),"ALL OF requires an enumeration domain","ENUM_TREE_TARGET",enumTree.range())));
                yield new EnumerationType(id,new Origin.DeclaredOrigin(enumTree.range()),enumeration.values(),enumeration.ordered(),
                        enumeration.circular(),enumeration.finalSubtrees(),Optional.of(enumeration.id()),true);
            }
            case guru.interlis.syntax.EnumArgumentTypeSyntax argument ->
                new guru.interlis.model.EnumArgumentType(id,new Origin.DeclaredOrigin(argument.range()),argument.treeValues());

            case guru.interlis.syntax.StructureValueTypeSyntax structure -> g.structures.bind(id,structure,index.byId(context).orElseThrow());
            case GenericTypeSyntax generic -> {
                if ("INHERITED".equals(generic.name())) {
                    yield new ErrorType(id, new Origin.DeclaredOrigin(generic.range()), List.of(diagnostics.emitRule(
                            index.byId(context).orElseThrow(), "a type can only be omitted on an inherited attribute",
                            "INHERITED_TYPE_CONTEXT")));
                }
                yield new GenericType(id, new Origin.DeclaredOrigin(generic.range()), generic.name());
            }
            case guru.interlis.syntax.AttributePathTypeSyntax path -> {
                Optional<guru.interlis.model.AttributePathType.Owner> owner = Optional.empty();
                if (path.argument().isPresent()) {
                    DeclInfo declaration = index.byId(context).orElseThrow();
                    String name = path.argument().orElseThrow().text();
                    int position = -1;
                    if (declaration.syntax() instanceof FunctionDeclarationSyntax function) {
                        for (int i = 0; i < function.parameters().size(); i++)
                            if (function.parameters().get(i).name().equals(name)) { position = i; break; }
                    }
                    if (position < 0) {
                        yield new ErrorType(id, new Origin.DeclaredOrigin(path.range()), List.of(diagnostics.emitRule(declaration,
                                "ATTRIBUTE OF @ requires an argument of this function", "ATTRIBUTE_ARGUMENT")));
                    }
                    TypeDescriptor argumentType = effectiveDescriptor(functionArgumentType(declaration, position));
                    if (!(argumentType instanceof guru.interlis.model.ObjectArgumentType || argumentType instanceof StructureValueType
                            || argumentType instanceof ClassValueType || argumentType instanceof guru.interlis.model.AttributePathType || argumentType instanceof ErrorType))
                        diagnostics.emitRule(declaration, "ATTRIBUTE OF @ argument must describe objects, classes or an attribute path", "ATTRIBUTE_ARGUMENT_TYPE");
                    owner = Optional.of(new guru.interlis.model.AttributePathType.Argument(context, position));
                }
                Optional<ExpressionBindings.ExpressionResult> ownerClass = path.ownerClass().map(expression -> g.expressions.buildExpression(expression, index.byId(context).orElseThrow().owner().orElse(context)));
                List<TypeId> restrictions = path.restrictions().stream().map(t -> buildType(t, context, source)).toList();
                if (ownerClass.isPresent() && !(effectiveDescriptor(ownerClass.orElseThrow().typeId()) instanceof ClassValueType))
                    diagnostics.emitRule(index.byId(context).orElseThrow(), "ATTRIBUTE OF requires a class-valued attribute path", "ATTRIBUTE_PATH_TYPE");
                yield new guru.interlis.model.AttributePathType(id, new Origin.DeclaredOrigin(path.range()),
                        ownerClass.isPresent() ? Optional.of(new guru.interlis.model.AttributePathType.ClassAttribute(ownerClass.orElseThrow().id())) : owner, restrictions);
            }
            case ClassValueTypeSyntax classValue -> {
                List<DiagnosticId> errors = new ArrayList<>();
                List<SymbolId> restrictions = new ArrayList<>();
                var kinds = classValue.structureValue() ? Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE)
                        : Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW);
                for (var name : classValue.restrictions()) {
                    g.names.bindTarget(index.byId(context).orElseThrow(), name, kinds, ReferenceUsage.TYPE_USE, errors).ifPresent(target -> {
                        SymbolId current = target;
                        Set<SymbolId> visited = new HashSet<>();
                        while (current != null && visited.add(current)) { work.checkpoint(); current = g.objectInheritanceBase(current).orElse(null); }
                        restrictions.add(target);
                    });
                }
                yield errors.isEmpty() ? new ClassValueType(id, new Origin.DeclaredOrigin(classValue.range()), classValue.structureValue(), restrictions)
                        : new ErrorType(id, new Origin.DeclaredOrigin(classValue.range()), errors);
            }
            case guru.interlis.syntax.ObjectArgumentTypeSyntax object ->
                    buildObjectArgumentType(id, object, context);
            case guru.interlis.syntax.MetaObjectTypeSyntax meta -> {
                var declaration = index.byId(context).orElseThrow();
                var target = meta.target().flatMap(name -> {
                    var predefined = g.metadata.predefined(declaration, name.text(), Set.of(SymbolKind.CLASS));
                    if (predefined.isPresent()) g.references.recordReference(declaration, name, predefined.get(), ReferenceUsage.TYPE_USE);
                    return predefined.isPresent() ? predefined : g.names.bindTarget(declaration, name, Set.of(SymbolKind.CLASS), ReferenceUsage.TYPE_USE, new ArrayList<>());
                });
                target.ifPresent(idTarget -> {
                    g.metadata.ensureBases(idTarget);
                    var root = g.metadata.predefined(declaration, "INTERLIS.METAOBJECT", Set.of(SymbolKind.CLASS)).orElseThrow();
                    if (!guru.interlis.model.internal.TypeCompatibility.specializes(idTarget, root, state::baseOf))
                        diagnostics.emitRule(declaration, "METAOBJECT OF requires a metadata class", "PARAMETER_METAOBJECT_TARGET");
                });
                yield new guru.interlis.model.MetaObjectType(id, new Origin.DeclaredOrigin(meta.range()), target);
            }
            case LineTypeSyntax line -> g.lines.buildType(id, line, index.byId(context).orElseThrow());
            case FormattedTypeSyntax formatted -> g.formats.buildFormattedType(id, formatted, context);
            case guru.interlis.syntax.NumericTypeSyntax numeric -> {
                var invalid = new ArrayList<>(g.units.numericErrors(numeric, index.byId(context).orElseThrow()));
                if (!invalid.isEmpty()) yield new ErrorType(id, new Origin.DeclaredOrigin(numeric.range()), invalid);
                var range=g.metadata.numeric(numeric,index.byId(context).orElseThrow(),invalid);
                g.units.validateNumericUnit(range,index.byId(context).orElseThrow(),numeric.range(),invalid);
                yield invalid.isEmpty()?new NumericType(id,new Origin.DeclaredOrigin(numeric.range()),range)
                        :new ErrorType(id,new Origin.DeclaredOrigin(numeric.range()),invalid);
            }
            case EnumerationTypeSyntax enumeration -> {
                DeclInfo declaration = index.byId(context).orElseThrow();
                TypeDescriptor inherited = null;
                if (declaration.kind() == SymbolKind.ATTRIBUTE && declaration.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                    SymbolId owner = declaration.owner().orElse(null);
                    AttributeSymbol baseAttribute = owner == null ? null : g.findBaseAttribute(state.baseOf(owner), declaration.name());
                    if (baseAttribute != null) inherited = effectiveDescriptor(baseAttribute.declaredType());
                }
                // Domains are merged after their base reference has been bound.
                yield declaration.kind() == SymbolKind.DOMAIN
                        ? new EnumerationType(id, new Origin.DeclaredOrigin(enumeration.range()), List.of(),
                                enumeration.ordered(), enumeration.circular(), enumeration.finalSubtrees(), Optional.empty(), false)
                        : effectiveEnumeration(id, enumeration, inherited, declaration);
            }
            case DomainReferenceSyntax reference -> {
                var declaration=index.byId(context).orElseThrow();var errors=new ArrayList<DiagnosticId>();
                var target=g.names.bindTarget(declaration,reference.name(),Set.of(SymbolKind.DOMAIN,SymbolKind.STRUCTURE),ReferenceUsage.TYPE_USE,errors);
                if(target.isEmpty())yield new ErrorType(id,new Origin.DeclaredOrigin(reference.range()),errors);
                SymbolId selected=target.orElseThrow();
                var sourceDeclaration=index.byId(selected);
                SymbolKind kind=sourceDeclaration.map(DeclInfo::kind).orElseGet(()->state.symbol(selected).kind());
                if(kind==SymbolKind.STRUCTURE)yield new StructureValueType(id,new Origin.DeclaredOrigin(reference.range()),Optional.of(selected),List.of());
                sourceDeclaration.ifPresent(this::domainType);
                yield new DomainReferenceType(id,new Origin.DeclaredOrigin(reference.range()),selected);
            }
            case ReferenceTypeSyntax reference -> {
                DeclInfo declaration = index.byId(context).orElseThrow();
                Optional<SymbolId> target = reference.target().text().equals("ANYCLASS") ? Optional.empty()
                        : Optional.of(g.bindObjectTarget(declaration, reference.target(), ReferenceUsage.TYPE_USE));
                List<SymbolId> restrictions = reference.restrictions().stream()
                        .map(name -> g.bindObjectTarget(declaration, name, ReferenceUsage.TYPE_USE)).toList();
                for (SymbolId restriction : restrictions) {
                    work.checkpoint();
                    if (target.isPresent() && !g.targetSpecializes(restriction, target.orElseThrow()))
                        diagnostics.emitRule(declaration, "reference restriction must specialize its primary target", "REFERENCE_TARGET_RESTRICTION");
                }
                yield new ReferenceType(id, new Origin.DeclaredOrigin(reference.range()), target, restrictions, reference.external());
            }
            case CollectionTypeSyntax collection -> {
                TypeId element = buildType(collection.elementType(), context, source);
                if (index.byId(context).orElseThrow().language() == guru.interlis.model.LanguageVersion.ILI23
                        && !(effectiveDescriptor(element) instanceof StructureValueType)
                        && !(effectiveDescriptor(element) instanceof ErrorType))
                    diagnostics.emitRule(index.byId(context).orElseThrow(), "INTERLIS 2.3 collections require structure elements", "COLLECTION_ELEMENT_VERSION");
                yield new CollectionType(id, new Origin.DeclaredOrigin(collection.range()),
                        collection.collectionKind(), collection.cardinality(), element);
            }
            case CoordinateTypeSyntax coordinate -> {
                List<DiagnosticId> invalid = new ArrayList<>();
                for (var axis : coordinate.axes()) invalid.addAll(g.units.numericErrors(axis, index.byId(context).orElseThrow()));
                if (!invalid.isEmpty()) yield new ErrorType(id, new Origin.DeclaredOrigin(coordinate.range()), invalid);
                var axes=new ArrayList<NumericRange>();
                for(var axis:coordinate.axes()) {
                    var range=g.metadata.numeric(axis,index.byId(context).orElseThrow(),invalid);
                    g.units.validateNumericUnit(range,index.byId(context).orElseThrow(),axis.range(),invalid);axes.add(range);
                }
                yield invalid.isEmpty()?new guru.interlis.model.CoordinateType(id,
                    new Origin.DeclaredOrigin(coordinate.range()),coordinate.dimensions(),axes,coordinate.multiCoordinate(),coordinate.referenceSystem(),coordinate.rotation())
                        :new ErrorType(id,new Origin.DeclaredOrigin(coordinate.range()),invalid);
            }
            case guru.interlis.syntax.UnsupportedTypeSyntax unsupported -> {
                if (unsupported.featureId().equals("MISSING_ATTRIBUTE_TYPE") || unsupported.featureId().equals("MISSING_FUNCTION_RETURN_TYPE")) {
                    DiagnosticId cause = null;
                    int distance = Integer.MAX_VALUE;
                    for (var diagnostic : diagnostics.syntaxErrors()) {
                        work.checkpoint();
                        var range = diagnostic.primaryRange().orElse(null);
                        if (range == null || !range.source().equals(unsupported.range().source())
                                || !range.revision().equals(unsupported.range().revision())) continue;
                        int candidate = Math.abs(range.startOffset() - unsupported.range().endOffset());
                        if (candidate < distance) { cause = diagnostic.id(); distance = candidate; }
                    }
                    if (cause == null) cause = diagnostics.emitRule(index.byId(context).orElseThrow(), "attribute type is missing", "SOURCE_MISSING_TYPE", unsupported.range());
                    yield new ErrorType(id, new Origin.DeclaredOrigin(unsupported.range()), List.of(cause));
                }
                DiagnosticId diagnostic = diagnostics.emitUnsupported(source, unsupported.range(),
                        unsupported.featureId(), unsupported.construct());
                yield new ErrorType(id, new Origin.DeclaredOrigin(unsupported.range()),
                        List.of(diagnostic));
            }
        };
        state.storeType(descriptor);
        return id;
    }

    private TypeDescriptor buildObjectArgumentType(TypeId id,
            guru.interlis.syntax.ObjectArgumentTypeSyntax syntax, SymbolId context) {
        DeclInfo owner = index.byId(context).orElseThrow();
        List<DiagnosticId> errors = new ArrayList<>();
        List<SymbolId> restrictions = new ArrayList<>();
        Optional<SymbolId> base = syntax.base().flatMap(name ->
                g.bindObjectTarget(owner, name, errors));
        for (var name : syntax.restrictions()) {
            work.checkpoint();
            Optional<SymbolId> restriction = g.bindObjectTarget(owner, name, errors);
            restriction.ifPresent(restrictions::add);
            if (base.isPresent() && restriction.isPresent()
                    && !guru.interlis.model.internal.TypeCompatibility.specializes(
                            restriction.get(), base.get(), state::baseOf)) {
                errors.add(diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0005"),
                                Severity.ERROR, Phase.TYPE_CHECKING, "diagnostic.semantics.type")
                        .at(name.range())
                        .argument(new DiagnosticArgument.TypeNameArgument("actual", name.text()))
                        .argument(new DiagnosticArgument.TypeNameArgument("expected",
                                "specialization of " + syntax.base().orElseThrow().text()))));
            }
        }
        Origin origin = new Origin.DeclaredOrigin(syntax.range());
        return errors.isEmpty()
                ? new guru.interlis.model.ObjectArgumentType(id, origin, base, restrictions, syntax.objects())
                : new ErrorType(id, origin, errors);
    }

    TypeId errorType(SourceId source, SourceRange range, String feature) {
        TypeId id = nextTypeId();
        DiagnosticId diagnostic = diagnostics.emitUnsupported(source, range, feature, feature);
        state.storeType(new ErrorType(id, new Origin.SyntheticOrigin(
                guru.interlis.model.SyntheticReason.MISSING_TYPE, Optional.of(range)),
                List.of(diagnostic)));
        return id;
    }

    TypeId builtinBoolean(guru.interlis.model.LanguageVersion language) {
        return booleanTypes.computeIfAbsent(language,version->{
            TypeId id=nextTypeId();state.storeType(new guru.interlis.model.BooleanType(id,new Origin.PredefinedOrigin(version,"BOOLEAN")));return id;
        });
    }

    TypeId nextTypeId() {
        return state.nextTypeId();
    }

    boolean isBoolean(TypeId typeId) {
        var type=effectiveDescriptor(typeId);
        return type instanceof guru.interlis.model.BooleanType || guru.interlis.model.internal.TypeCompatibility.isUndefined(type);
    }

    boolean isNumeric(TypeId typeId) {
        var type=effectiveDescriptor(typeId);
        return type instanceof NumericType || guru.interlis.model.internal.TypeCompatibility.isUndefined(type);
    }

    TypeDescriptor effectiveDescriptor(TypeId typeId) {
        TypeDescriptor descriptor = state.type(typeId);
        Set<TypeId> seen = new HashSet<>();
        while (descriptor instanceof DomainReferenceType reference && seen.add(descriptor.id())) {
            work.checkpoint();
            TypeId target = state.declaredType(reference.targetDomain());
            if (target == null) break;
            descriptor = state.type(target);
        }
        return descriptor;
    }

    String typeName(TypeId id) {
        TypeDescriptor descriptor = effectiveDescriptor(id);
        if(descriptor instanceof guru.interlis.model.EnumArgumentType argument)return argument.treeValues()?"ENUMTREEVAL":"ENUMVAL";
        if (descriptor instanceof TextType text) {
            return text.textKind().name() + (text.maxLength().isPresent() ? "*" + text.maxLength().getAsInt() : "");
        }
        return descriptor == null ? "UNKNOWN" : descriptor.kind().name();
    }

    void buildEffectiveTypes() {
        for (Map.Entry<SymbolId, TypeId> entry : state.declaredTypesInBuildOrder()) {
            work.checkpoint();
            state.setEffectiveType(entry.getKey(), effectiveType(entry.getValue()));
        }
    }

    private EffectiveType effectiveType(TypeId declared) {
        List<SymbolId> aliasPath = new ArrayList<>();
        TypeId current = declared;
        Set<TypeId> seen = new HashSet<>();
        while (true) {
            work.checkpoint();
            TypeDescriptor descriptor = state.type(current);
            if (!(descriptor instanceof DomainReferenceType reference)) {
                break;
            }
            if (!seen.add(current)) {
                break;
            }
            aliasPath.add(reference.targetDomain());
            Symbol symbol = state.symbol(reference.targetDomain());
            if (symbol instanceof DomainSymbol domain) {
                current = domain.typeId();
            } else {
                break;
            }
        }
        return new EffectiveType(declared, current, aliasPath);
    }

    List<ExpressionId> inferredDerivations(SymbolId declaration) {
        return inferredDerivations.get(declaration);
    }
}
