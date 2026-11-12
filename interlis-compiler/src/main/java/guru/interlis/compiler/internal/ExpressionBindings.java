package guru.interlis.compiler.internal;

import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.Cardinality;
import guru.interlis.model.BinaryOperator;
import guru.interlis.model.ClassValueType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DeclaredName;
import guru.interlis.model.Documentation;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.ErrorExpression;
import guru.interlis.model.ErrorType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.FormattedType;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.GenericType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceSegment;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextLengths;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.BinaryExpressionSyntax;
import guru.interlis.syntax.BuiltinFunctionExpressionSyntax;
import guru.interlis.syntax.ClassConstantExpressionSyntax;
import guru.interlis.syntax.ConstantExpressionSyntax;
import guru.interlis.syntax.DefinedExpressionSyntax;
import guru.interlis.syntax.EnumerationConstantExpressionSyntax;
import guru.interlis.syntax.ExpressionSyntax;
import guru.interlis.syntax.FunctionCallExpressionSyntax;
import guru.interlis.syntax.GenericConstantExpressionSyntax;
import guru.interlis.syntax.LiteralExpressionSyntax;
import guru.interlis.syntax.ParenthesizedExpressionSyntax;
import guru.interlis.syntax.PathExpressionSyntax;
import guru.interlis.syntax.UnaryExpressionSyntax;
import guru.interlis.syntax.UndefinedExpressionSyntax;
import guru.interlis.syntax.UnsupportedExpressionSyntax;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Binds and types source expressions in their owning scope. */
final class ExpressionBindings {
    private final SemanticGraphRead.Expressions state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final SourceSet sourceSet;
    private final ObjectPathBindings paths;
    private final Supplier<ViewBuilder> views;
    private final TypeBindings typeBindings;
    private final LineForms lineForms;
    private final MetadataBindings metadata;
    private final NameBindings names;
    private final ReferenceBindings references;
    private final FunctionBindings functions;
    private final UnitBindings units;
    private final FormattedTypeBindings formats;
    private final Consumer<FunctionSymbol> definePredefinedFunction;
    private final BiFunction<SymbolId, SymbolId, SymbolId> externalConstraintTarget;
    private final Function<SymbolId, SymbolId> graphicContext;
    private final Function<DeclInfo, Symbol> ensureSymbol;

    ExpressionBindings(SemanticGraphRead.Expressions state,
                       SemanticWork work,
                       IndexedDeclarations index,
                       SemanticDiagnostics diagnostics,
                       SourceSet sourceSet,
                       ObjectPathBindings paths,
                       Supplier<ViewBuilder> views,
                       TypeBindings typeBindings,
                       LineForms lineForms,
                       MetadataBindings metadata,
                       NameBindings names,
                       ReferenceBindings references,
                       FunctionBindings functions,
                       UnitBindings units,
                       FormattedTypeBindings formats,
                       Consumer<FunctionSymbol> definePredefinedFunction,
                       BiFunction<SymbolId, SymbolId, SymbolId> externalConstraintTarget,
                       Function<SymbolId, SymbolId> graphicContext,
                       Function<DeclInfo, Symbol> ensureSymbol) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.sourceSet = sourceSet;
        this.paths = paths;
        this.views = views;
        this.typeBindings = typeBindings;
        this.lineForms = lineForms;
        this.metadata = metadata;
        this.names = names;
        this.references = references;
        this.functions = functions;
        this.units = units;
        this.formats = formats;
        this.definePredefinedFunction = definePredefinedFunction;
        this.externalConstraintTarget = externalConstraintTarget;
        this.graphicContext = graphicContext;
        this.ensureSymbol = ensureSymbol;
    }

    ExpressionResult buildExpression(ExpressionSyntax syntax, SymbolId owner) {
        ExpressionId id = state.nextExpressionId();
        return switch (syntax) {
            case LiteralExpressionSyntax literal -> literalExpression(id, literal, owner);
            case PathExpressionSyntax path -> paths.pathExpression(id, path, owner);
            case BinaryExpressionSyntax binary -> binaryExpression(id, binary, owner);
            case UnaryExpressionSyntax unary -> unaryExpression(id, unary, owner);
            case DefinedExpressionSyntax defined -> definedExpression(id, defined, owner);
            case ConstantExpressionSyntax constant -> constantExpression(id, constant, owner);
            case UndefinedExpressionSyntax undefined -> undefinedExpression(id, undefined, owner);
            case ParenthesizedExpressionSyntax parenthesized ->
                    buildExpression(parenthesized.inner(), owner);
            case FunctionCallExpressionSyntax call -> functionCallExpression(id, call, owner);
            case BuiltinFunctionExpressionSyntax builtin -> builtinFunctionExpression(id, builtin, owner);
            case EnumerationConstantExpressionSyntax constant -> enumerationConstantExpression(id, constant);
            case ClassConstantExpressionSyntax constant -> classConstantExpression(id, constant, owner);
            case guru.interlis.syntax.ObjectSetExpressionSyntax set -> objectSetExpression(id, set, owner);
            case guru.interlis.syntax.InspectionExpressionSyntax inspection -> views.get().factor(id, inspection, owner);
            case GenericConstantExpressionSyntax constant -> genericConstantExpression(id, constant, owner);
            case guru.interlis.syntax.ParameterExpressionSyntax parameter -> parameterExpression(id, parameter, owner);
            case guru.interlis.syntax.AttributeConstantExpressionSyntax constant -> attributeConstantExpression(id, constant, owner);
            case UnsupportedExpressionSyntax unsupported -> {
                DiagnosticId diagnostic = diagnostics.emitUnsupported(ownerSource(owner), unsupported.range(),
                        unsupported.featureId(), unsupported.construct());
                TypeId error = typeBindings.errorType(ownerSource(owner), unsupported.range(),
                        unsupported.featureId());
                ErrorExpression expression = new ErrorExpression(id, error,
                        guru.interlis.model.ExpressionTypeState.ERROR, unsupported.range(),
                        List.of(), List.of(diagnostic));
                state.defineExpression(expression);
                yield new ExpressionResult(id, error, true);
            }
        };
    }

    private ExpressionResult objectSetExpression(ExpressionId id,
            guru.interlis.syntax.ObjectSetExpressionSyntax syntax, SymbolId owner) {
        TypeId type;
        if (syntax.selection().isPresent()) {
            type = typeBindings.buildType(syntax.selection().orElseThrow(), owner, ownerSource(owner));
        } else {
            SymbolId context = expressionContext(owner);
            var kind = index.byId(context).map(DeclInfo::kind).orElse(null);
            type = typeBindings.nextTypeId();
            if (kind == SymbolKind.CLASS || kind == SymbolKind.ASSOCIATION || kind == SymbolKind.VIEW) {
                typeBindings.defineType(new guru.interlis.model.ObjectArgumentType(type,
                        new Origin.DeclaredOrigin(syntax.range()), Optional.of(context), List.of(), true));
            } else {
                type = typeBindings.errorType(ownerSource(owner), syntax.range(), "ALL requires an object context");
            }
        }
        boolean error = typeBindings.effectiveDescriptor(type) instanceof ErrorType;
        state.defineExpression(new guru.interlis.model.ObjectSetExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range()));
        return new ExpressionResult(id, type, error);
    }

    SymbolId expressionContext(SymbolId owner) {
        SymbolId context = owner;
        while (index.byId(context).map(DeclInfo::kind).orElse(null) == SymbolKind.CONSTRAINT) {
            work.checkpoint();
            context = externalConstraintTarget.apply(context,
                    index.byId(context).orElseThrow().owner().orElseThrow());
        }
        return graphicContext.apply(context);
    }

    private ExpressionResult genericConstantExpression(ExpressionId id,
                                                       GenericConstantExpressionSyntax syntax,
                                                       SymbolId owner) {
        TypeId type = typeBindings.nextTypeId();
        typeBindings.defineType(new GenericType(type, new Origin.DeclaredOrigin(syntax.range()), syntax.name()));
        state.defineExpression(new guru.interlis.model.ConstantExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.name()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult classConstantExpression(ExpressionId id,
                                                     ClassConstantExpressionSyntax syntax,
                                                     SymbolId owner) {
        List<DiagnosticId> errors = new ArrayList<>();
        var declaration = index.byId(owner).orElseThrow();
        var kinds = Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE, SymbolKind.ASSOCIATION, SymbolKind.VIEW);
        var target = lineForms.predefined(declaration, syntax.target().text(), kinds);
        if (target.isEmpty()) target = metadata.predefined(declaration, syntax.target().text(), kinds);
        if (target.isEmpty()) target = names.bindTarget(declaration, syntax.target(), kinds, ReferenceUsage.EXPRESSION, errors);
        else references.recordReference(declaration, syntax.target(), target.orElseThrow(), ReferenceUsage.EXPRESSION);
        if (target.isEmpty()) return failedValueExpression(id, syntax.range(), errors);
        TypeId type = typeBindings.nextTypeId();
        boolean structure = state.symbol(target.orElseThrow()) instanceof StructureSymbol
                || index.byId(target.orElseThrow()).map(d -> d.kind() == SymbolKind.STRUCTURE).orElse(false);
        typeBindings.defineType(new ClassValueType(type, new Origin.DeclaredOrigin(syntax.range()), structure, target.stream().toList()));
        state.defineExpression(new guru.interlis.model.SymbolValueExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), target.orElseThrow()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult attributeConstantExpression(ExpressionId id, guru.interlis.syntax.AttributeConstantExpressionSyntax syntax, SymbolId owner) {
        return attributeConstantExpression(id,syntax,owner,expressionContext(owner));
    }

    ExpressionResult contextualAttributeConstant(guru.interlis.syntax.AttributeConstantExpressionSyntax syntax,SymbolId owner,SymbolId context) {
        return attributeConstantExpression(state.nextExpressionId(),syntax,owner,context);
    }

    private ExpressionResult attributeConstantExpression(ExpressionId id, guru.interlis.syntax.AttributeConstantExpressionSyntax syntax, SymbolId owner,SymbolId context) {
        var declaration = index.byId(owner).orElseThrow();
        List<DiagnosticId> errors = new ArrayList<>();
        Optional<SymbolId> viewable = syntax.viewable().isPresent()
                ? names.bindTarget(declaration, syntax.viewable().orElseThrow(), Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE, SymbolKind.ASSOCIATION, SymbolKind.VIEW), ReferenceUsage.EXPRESSION, errors)
                : Optional.of(context);
        Optional<SymbolId> attribute = viewable.flatMap(target -> paths.findMember(target, syntax.attribute().text()))
                .filter(target -> index.byId(target).map(DeclInfo::kind).orElse(null) == SymbolKind.ATTRIBUTE || state.symbol(target) instanceof AttributeSymbol);
        references.recordReference(declaration, syntax.attribute(), attribute.orElse(null), ReferenceUsage.EXPRESSION);
        if (attribute.isEmpty()) {
            errors.add(diagnostics.unresolved(syntax.attribute()));
            return failedValueExpression(id, syntax.range(), errors);
        }
        TypeId attributeType = index.byId(attribute.orElseThrow()).map(typeBindings::attributeType)
                .orElseGet(() -> ((AttributeSymbol) state.symbol(attribute.orElseThrow())).declaredType());
        TypeId type = typeBindings.nextTypeId();
        typeBindings.defineType(new guru.interlis.model.AttributePathType(type, new Origin.DeclaredOrigin(syntax.range()),
                Optional.of(new guru.interlis.model.AttributePathType.Viewable(viewable.orElseThrow())), List.of(attributeType)));
        state.defineExpression(new guru.interlis.model.SymbolValueExpression(id, type, guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), attribute.orElseThrow()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult parameterExpression(ExpressionId id, guru.interlis.syntax.ParameterExpressionSyntax syntax, SymbolId owner) {
        List<DiagnosticId> errors = new ArrayList<>();
        var target = names.bindTarget(index.byId(owner).orElseThrow(), syntax.parameter(), Set.of(SymbolKind.PARAMETER), ReferenceUsage.EXPRESSION, errors);
        if (target.isEmpty()) return failedValueExpression(id, syntax.range(), errors);
        var parameter = (guru.interlis.model.ParameterSymbol) ensureSymbol.apply(index.byId(target.orElseThrow()).orElseThrow());
        boolean error = typeBindings.effectiveDescriptor(parameter.typeId()) instanceof ErrorType;
        state.defineExpression(new guru.interlis.model.SymbolValueExpression(id, parameter.typeId(),
                error ? guru.interlis.model.ExpressionTypeState.ERROR : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), parameter.id()));
        return new ExpressionResult(id, parameter.typeId(), error);
    }

    ExpressionResult failedValueExpression(ExpressionId id, SourceRange range, List<DiagnosticId> errors) {
        TypeId type = typeBindings.nextTypeId();
        typeBindings.defineType(new ErrorType(type, new Origin.DeclaredOrigin(range), errors));
        state.defineExpression(new guru.interlis.model.ErrorExpression(id, type,
                guru.interlis.model.ExpressionTypeState.ERROR, range, List.of(), errors));
        return new ExpressionResult(id, type, true);
    }

    ExpressionResult failedFunctionArgument(SourceRange range,List<DiagnosticId> causes) {
        return failedValueExpression(state.nextExpressionId(),range,causes);
    }

    ExpressionResult blockedFunctionArgument(SourceRange range,ExpressionResult dependency,DeclInfo owner) {
        var descriptor=typeBindings.effectiveDescriptor(dependency.typeId());var source=state.expression(dependency.id()).range();
        var causes=descriptor instanceof ErrorType error?error.diagnosticIds():diagnostics.snapshot().stream()
                .filter(d->d.severity()==Severity.ERROR&&d.primaryRange().filter(r->r.source().equals(source.source())&&r.revision().equals(source.revision())
                        &&r.startOffset()<=source.endOffset()&&r.endOffset()>=source.startOffset()).isPresent()).map(guru.interlis.diagnostics.Diagnostic::id).toList();
        if(causes.isEmpty())causes=List.of(diagnostics.emitRule(owner,"invalid actual attribute owner argument","FUNCTION_ATTRIBUTE_DEPENDENCY",range));
        return failedFunctionArgument(range,causes);
    }

    private ExpressionResult enumerationConstantExpression(ExpressionId id, EnumerationConstantExpressionSyntax syntax) {
        TypeId type = typeBindings.nextTypeId();
        typeBindings.defineType(new guru.interlis.model.EnumArgumentType(type,new Origin.DeclaredOrigin(syntax.range()),false));
        state.defineExpression(new guru.interlis.model.ConstantExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.text()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult builtinFunctionExpression(ExpressionId id,
                                                       BuiltinFunctionExpressionSyntax syntax,
                                                       SymbolId owner) {
        var signature = BuiltinFunctions.find(syntax.functionName().segments().getLast().text()).orElse(null);
        var declaration = index.byId(owner).orElseThrow();
        boolean available=signature!=null && (signature.since()!=guru.interlis.model.LanguageVersion.ILI24 || declaration.language()==guru.interlis.model.LanguageVersion.ILI24);
        List<ExpressionResult> arguments=new ArrayList<>(functions.arguments(available?builtinDeclaration(signature,declaration.language()):null,
                syntax.arguments(),declaration,available?signature.rule():BuiltinFunctions.Rule.SIMPLE));
        boolean error = arguments.stream().anyMatch(ExpressionResult::error);
        TypeId type;
        if (signature == null || signature.since() == guru.interlis.model.LanguageVersion.ILI24
                && declaration.language() != guru.interlis.model.LanguageVersion.ILI24) {
            DiagnosticId cause = diagnostics.emitRule(declaration, "Unknown standard function INTERLIS." + syntax.functionName().segments().getLast().text(),
                    "BUILTIN_NAME");
            type = typeBindings.nextTypeId();
            typeBindings.defineType(new ErrorType(type, new Origin.DeclaredOrigin(syntax.range()), List.of(cause)));
            error = true;

        } else {
            type = builtinResultType(signature.result(), declaration.language());
            if (arguments.size() != signature.arguments().size()) {
                diagnostics.emitTypeMismatch(ownerSource(owner), syntax.range(),
                        arguments.size() + " arguments", signature.arguments().size() + " arguments");
                error = true;
            }
            for (int i = 0; i < Math.min(arguments.size(), signature.arguments().size()); i++) {
                work.checkpoint();
                var actual = arguments.get(i);
                var expected = signature.arguments().get(i);
                TypeId formal = builtinDeclaration(signature, declaration.language()).parameters().get(i).typeId();
                actual = contextualEnumConstant(actual, formal, owner, "ENUM_CONSTANT");
                arguments.set(i, actual);
                error |= actual.error();
                if (!actual.error() && expected!=BuiltinFunctions.Value.ATTRIBUTE
                        && !(i==1 && (signature.rule()==BuiltinFunctions.Rule.AREAS_ATTRIBUTE || signature.rule()==BuiltinFunctions.Rule.AREAS_TEXT)
                            && state.expression(actual.id()) instanceof guru.interlis.model.UndefinedExpression)
                        && !builtinArgumentMatches(actual.typeId(), expected, declaration.language())) {
                    diagnostics.emitTypeMismatch(ownerSource(owner), syntax.arguments().get(i).range(),
                            typeBindings.typeName(actual.typeId()), expected.name());
                    error = true;
                }
            }
        }
        if(!error && (signature.name().equals("isEnumSubVal") || signature.name().equals("inEnumRange")))
            error=!enumArgumentsValid(arguments,signature,declaration,syntax.range());
        if (!error && syntax.functionName().segments().getLast().text().equals("myClass")) {
            TypeDescriptor argument = typeBindings.effectiveDescriptor(arguments.getFirst().typeId());
            List<SymbolId> targets = argument instanceof ReferenceType r ? r.targetRoots()
                    : argument instanceof guru.interlis.model.ObjectArgumentType o ? (o.restrictions().isEmpty() ? o.base().stream().toList() : o.restrictions())
                    : argument instanceof StructureValueType structure ? structure.targetRoots() : List.of();
            typeBindings.replaceType(new ClassValueType(type, new Origin.DeclaredOrigin(syntax.range()),
                    argument instanceof StructureValueType || guru.interlis.model.internal.TypeCompatibility.isUndefined(argument), targets));
        }
        if (!error && signature.rule()==BuiltinFunctions.Rule.CONVERT_UNIT) {
            var from=typeBindings.effectiveDescriptor(arguments.getFirst().typeId());
            type=typeBindings.nextTypeId();
            typeBindings.defineType(new NumericType(type,new Origin.DeclaredOrigin(syntax.range()),new NumericRange(Optional.empty(),Optional.empty(),false,
                    guru.interlis.model.NumericDirection.NONE,from instanceof NumericType n?n.range().unit():Optional.empty(),Optional.empty())));
        }
        FunctionBindings.Result bound = FunctionBindings.Result.empty();
        if (!error) {
            bound = functions.bind(builtinDeclaration(signature,declaration.language()),arguments,declaration,syntax.range(),signature.rule());
            error = bound.error();
        }
        if(available)references.recordReference(declaration,syntax.functionName(),
                builtinDeclaration(signature,declaration.language()).id(),ReferenceUsage.EXPRESSION);
        state.defineExpression(new guru.interlis.model.FunctionCallExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), arguments.stream().map(ExpressionResult::id).toList(),
                "INTERLIS." + syntax.functionName().segments().getLast().text(), Optional.ofNullable(available?builtinDeclaration(signature,declaration.language()).id():null),bound.paths()));
        return new ExpressionResult(id, type, error);
    }

    private boolean enumArgumentsValid(List<ExpressionResult> arguments, BuiltinFunctions.Signature signature, DeclInfo owner, SourceRange range) {
        var anchor = arguments.stream().map(argument -> typeBindings.effectiveDescriptor(argument.typeId()))
                .filter(this::hasEnumValues).findFirst().orElse(null);
        if (anchor == null) return true; // Literal-only polymorphic calls retain symbolic values.
        for (int i = 0; i < arguments.size(); i++) {
            work.checkpoint();
            var argument = arguments.get(i);
            var actual = typeBindings.effectiveDescriptor(argument.typeId());
            if (guru.interlis.model.internal.TypeCompatibility.isUndefined(actual)) continue;
            if (unboundEnum(argument)) {
                TypeId context = anchor.id();
                if (anchor instanceof EnumerationType enumeration && !enumeration.treeValues()
                        && signature.arguments().get(i) == BuiltinFunctions.Value.ENUM_TREE) {
                    context = typeBindings.nextTypeId();
                    typeBindings.defineType(new EnumerationType(context, enumeration.origin(), enumeration.values(),
                            enumeration.ordered(), enumeration.circular(), enumeration.finalSubtrees(),
                            Optional.of(enumeration.id()), true));
                }
                var bound = contextualEnumConstant(argument, context, owner.id(), "ENUM_ARGUMENT");
                arguments.set(i, bound);
                if (bound.error()) return false;
            } else if (!compatibleEnumTypes(anchor, actual)) {
                diagnostics.emitRule(owner, "enumeration function arguments must refer to compatible values of the same enumeration",
                        "ENUM_ARGUMENT", range);
                return false;
            }
        }
        return true;
    }

    private boolean hasEnumValues(TypeDescriptor descriptor) {
        return descriptor instanceof EnumerationType || descriptor instanceof guru.interlis.model.BooleanType
                || descriptor instanceof guru.interlis.model.AlignmentType;
    }

    private boolean compatibleEnumTypes(TypeDescriptor left, TypeDescriptor right) {
        return left instanceof EnumerationType a && right instanceof EnumerationType b
                    && guru.interlis.model.internal.TypeCompatibility.relatedEnumerations(a, b, typeBindings::effectiveDescriptor)
                || left instanceof guru.interlis.model.BooleanType && right instanceof guru.interlis.model.BooleanType
                || left instanceof guru.interlis.model.AlignmentType a && right instanceof guru.interlis.model.AlignmentType b
                    && a.alignment() == b.alignment();
    }

    private boolean enumContains(TypeDescriptor descriptor, String path) {
        var values = enumerationPaths(descriptor);
        return values.contains(path) && (!(descriptor instanceof EnumerationType enumeration) || enumeration.treeValues()
                || values.stream().noneMatch(value -> value.startsWith(path + ".")));
    }

    private List<String> enumerationPaths(TypeDescriptor descriptor) {
        if(descriptor instanceof EnumerationType enumeration)return enumeration.values().stream().map(EnumerationValue::path).toList();
        if(descriptor instanceof guru.interlis.model.BooleanType)return List.of("false","true");
        if(descriptor instanceof guru.interlis.model.AlignmentType alignment)return alignment.alignment()==guru.interlis.model.AlignmentKind.HORIZONTAL
                ?List.of("Left","Center","Right"):List.of("Top","Cap","Half","Base","Bottom");
        return List.of();
    }

    FunctionSymbol builtinDeclaration(BuiltinFunctions.Signature signature, guru.interlis.model.LanguageVersion language) {
        var id=new SymbolId("predefined:"+language+":function:INTERLIS."+signature.name());
        if(state.symbol(id) instanceof FunctionSymbol function)return function;
        var parameters=new ArrayList<guru.interlis.model.FunctionParameter>();
        var names=BuiltinFunctions.parameterNames(signature.name());
        for(int i=0;i<signature.arguments().size();i++) {
            TypeId parameter;
            if(signature.arguments().get(i)==BuiltinFunctions.Value.ATTRIBUTE) {
                parameter=typeBindings.nextTypeId();
                TypeId restriction=builtinResultType(i==1?BuiltinFunctions.Value.BAG:BuiltinFunctions.Value.SURFACE,language);
                typeBindings.defineType(new guru.interlis.model.AttributePathType(parameter,new Origin.PredefinedOrigin(language,"INTERLIS."+signature.name()),
                        Optional.of(new guru.interlis.model.AttributePathType.Argument(id,i-1)),List.of(restriction)));
            } else parameter=builtinResultType(signature.arguments().get(i),language);
            parameters.add(new guru.interlis.model.FunctionParameter(names.get(i),parameter,false));
        }
        var header=new SymbolHeader(id,SymbolKind.FUNCTION,DeclaredName.synthetic("INTERLIS."+signature.name()),Optional.empty(),0,
                new Origin.PredefinedOrigin(language,"INTERLIS."+signature.name()),Optional.empty(),language,Documentation.empty(),MetaAttributeSet.empty(),Set.of(),List.of());
        var function=new FunctionSymbol(header,parameters,builtinResultType(signature.result(),language),false,Documentation.empty());definePredefinedFunction.accept(function);return function;
    }

    private TypeId builtinResultType(BuiltinFunctions.Value value,
                                    guru.interlis.model.LanguageVersion language) {
        if (value == BuiltinFunctions.Value.BOOLEAN) return typeBindings.builtinBoolean(language);
        TypeId id = typeBindings.nextTypeId();
        var origin = new Origin.PredefinedOrigin(language, value.name());
        TypeDescriptor descriptor = switch (value) {
            case NUMERIC -> new NumericType(id, origin, NumericRange.unbounded());
            case CLASS_VALUE -> new ClassValueType(id, origin, true, List.of());
            case POLYLINE, MULTIPOLYLINE, SURFACE, MULTISURFACE -> new GeometryType(id,origin,
                    guru.interlis.model.GeometryKind.valueOf(value.name()),List.of(),Optional.empty(),Optional.empty(),false,false,Optional.empty());
            case ENUM, ENUM_TREE -> new guru.interlis.model.EnumArgumentType(id,origin,value==BuiltinFunctions.Value.ENUM_TREE);
            case OBJECT -> new guru.interlis.model.ObjectArgumentType(id,origin,Optional.empty(),List.of(),false);
            case OBJECTS -> new guru.interlis.model.ObjectArgumentType(id, origin, Optional.empty(), List.of(), true);
            case ANYSTRUCTURE -> new StructureValueType(id,origin,Optional.empty(),List.of());
            case BAG -> new CollectionType(id,origin,guru.interlis.model.CollectionKind.BAG,Cardinality.unboundedFrom(0),
                    builtinResultType(BuiltinFunctions.Value.ANYSTRUCTURE,language));
            case TEXT, MTEXT -> new TextType(id, origin,
                    value == BuiltinFunctions.Value.TEXT ? TextKind.TEXT : TextKind.MTEXT,
                    java.util.OptionalInt.empty());
            default -> throw new IllegalArgumentException("unsupported builtin result " + value);
        };
        typeBindings.defineType(descriptor);
        return id;
    }

    private boolean builtinArgumentMatches(TypeId actual, BuiltinFunctions.Value expected, guru.interlis.model.LanguageVersion language) {
        return typeBindings.assignment(typeBindings.effectiveDescriptor(actual),
                typeBindings.effectiveDescriptor(builtinResultType(expected,language)), guru.interlis.model.AssignmentContext.FUNCTION_ARGUMENT)
                .decision()==guru.interlis.model.RelationDecision.ALLOWED;
    }

    private ExpressionResult functionCallExpression(ExpressionId id,
                                                    FunctionCallExpressionSyntax syntax,
                                                    SymbolId owner) {
        var selected=names.resolveName(index.byId(owner).orElseThrow(),syntax.functionName(),Set.of(SymbolKind.FUNCTION),true);
        if(selected.target().map(state::symbol).filter(f->f instanceof FunctionSymbol && f.origin() instanceof Origin.PredefinedOrigin).isPresent())
            return builtinFunctionExpression(id,new BuiltinFunctionExpressionSyntax(syntax.id(),syntax.range(),syntax.functionName(),syntax.arguments()),owner);
        List<DiagnosticId> bindingErrors = new ArrayList<>();
        Optional<SymbolId> target = names.bindTarget(index.byId(owner).orElseThrow(), syntax.functionName(),
                Set.of(SymbolKind.FUNCTION), ReferenceUsage.EXPRESSION, bindingErrors);
        FunctionSymbol function = target.flatMap(index::byId).map(ensureSymbol::apply)
                .filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).orElse(null);
        List<ExpressionResult> arguments=new ArrayList<>(functions.arguments(function,syntax.arguments(),index.byId(owner).orElseThrow(),BuiltinFunctions.Rule.SIMPLE));
        List<ExpressionId> children = arguments.stream().map(ExpressionResult::id).toList();
        boolean error = arguments.stream().anyMatch(ExpressionResult::error);
        TypeId type;
        if (function == null) {
            work.checkpoint();
            type = typeBindings.nextTypeId();
            typeBindings.defineType(new ErrorType(type, new Origin.DeclaredOrigin(syntax.range()),
                    bindingErrors));
            error = true;
        } else {
            type = function.returnType();
            if (arguments.size() != function.parameters().size()) {
                diagnostics.emitTypeMismatch(ownerSource(owner), syntax.range(),
                        arguments.size() + " arguments", function.parameters().size() + " arguments");
                error = true;
            }
            for (int i = 0; i < Math.min(arguments.size(), function.parameters().size()); i++) {
                work.checkpoint();
                var actual = arguments.get(i);
                TypeId expected = function.parameters().get(i).typeId();
                actual=contextualValue(actual,expected,owner);arguments.set(i,actual);
                if (!actual.error() && !(typeBindings.effectiveDescriptor(expected) instanceof guru.interlis.model.AttributePathType) && typeBindings.assignment(
                        typeBindings.effectiveDescriptor(actual.typeId()), typeBindings.effectiveDescriptor(expected),
                        guru.interlis.model.AssignmentContext.FUNCTION_ARGUMENT).decision()
                        != guru.interlis.model.RelationDecision.ALLOWED) {
                    diagnostics.emitTypeMismatch(ownerSource(owner), syntax.arguments().get(i).range(),
                            typeBindings.typeName(actual.typeId()), typeBindings.typeName(expected));
                    error = true;
                }
            }
        }
        if (!error) error = functions.bind(function,arguments,index.byId(owner).orElseThrow(),syntax.range(),BuiltinFunctions.Rule.SIMPLE).error();
        if (!error) type=functions.returnType(function,arguments,syntax.range());
        state.defineExpression(new guru.interlis.model.FunctionCallExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), children, syntax.functionName().text(), target,Map.of()));
        return new ExpressionResult(id, type, error);
    }

    private ExpressionResult literalExpression(ExpressionId id, LiteralExpressionSyntax syntax, SymbolId owner) {
        TypeId type;
        TypedExpression expression;
        if (syntax.literalKind() == guru.interlis.model.LiteralKind.TEXT) {
            String value = InterlisStrings.decode(syntax.lexeme()).text();
            type = typeBindings.nextTypeId();
            typeBindings.defineType(new TextType(type, new Origin.DeclaredOrigin(syntax.range()),
                    value.indexOf('\r')>=0 || value.indexOf('\n')>=0 || value.indexOf('\t')>=0 ? TextKind.MTEXT : TextKind.TEXT,
                    java.util.OptionalInt.of(TextLengths.canonicalLength(value))));
            expression = new guru.interlis.model.LiteralExpression(id, type,
                    guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                    syntax.literalKind(), syntax.lexeme(), Optional.empty(),
                    Optional.of(value));
        } else {
            type = numericLiteralType(syntax.decimal(), syntax.unit(), syntax.range(), owner);
            expression = new guru.interlis.model.LiteralExpression(id, type,
                    typeBindings.effectiveDescriptor(type) instanceof ErrorType ? guru.interlis.model.ExpressionTypeState.ERROR : guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                    syntax.literalKind(), syntax.lexeme(), syntax.decimal(), Optional.empty());
        }
        state.defineExpression(expression);
        return new ExpressionResult(id, type, typeBindings.effectiveDescriptor(type) instanceof ErrorType);
    }

    private TypeId numericLiteralType(Optional<DecimalLiteral> decimal, Optional<guru.interlis.syntax.NameSyntax> unitName, SourceRange sourceRange, SymbolId owner) {
        TypeId type = typeBindings.nextTypeId();
        Optional<SymbolId> unit = unitName.flatMap(name -> units.bindUnit(index.byId(owner).orElseThrow(), name));
        if (unitName.isPresent() && unit.isEmpty()) {
            var diagnostic = diagnostics.emitRule(index.byId(owner).orElseThrow(), "Numeric constant requires a known unit", "CONSTANT_UNIT", unitName.orElseThrow().range());
            typeBindings.defineType(new ErrorType(type, new Origin.DeclaredOrigin(sourceRange), List.of(diagnostic)));
            return type;
        }
        if (unit.isPresent() && units.extensionUnit(unit.orElseThrow()).abstractUnit()) {
            var diagnostic = diagnostics.emitRule(index.byId(owner).orElseThrow(), "Numeric constant requires a concrete unit", "CONSTANT_UNIT", unitName.orElseThrow().range());
            typeBindings.defineType(new ErrorType(type, new Origin.DeclaredOrigin(sourceRange), List.of(diagnostic)));
            return type;
        }
        NumericRange range = new NumericRange(decimal, decimal, false,
                guru.interlis.model.NumericDirection.NONE, unit, Optional.empty());
        typeBindings.defineType(new NumericType(type, new Origin.DeclaredOrigin(sourceRange), range));
        return type;
    }

    private ExpressionResult binaryExpression(ExpressionId id, BinaryExpressionSyntax syntax,
                                              SymbolId owner) {
        ExpressionResult left = buildExpression(syntax.left(), owner);
        ExpressionResult right = buildExpression(syntax.right(), owner);
        TypeId result = typeBindings.builtinBoolean(index.byId(owner).orElseThrow().language());
        boolean error = left.error() || right.error();
        switch (syntax.operator()) {
            case AND, OR, IMPLIES -> {
                left = contextualEnumConstant(left, result, owner, "ENUM_CONSTANT");
                right = contextualEnumConstant(right, result, owner, "ENUM_CONSTANT");
                error |= left.error() || right.error();
                if (!error && (!typeBindings.isBoolean(left.typeId()) || !typeBindings.isBoolean(right.typeId()))) {
                    diagnostics.emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeBindings.typeName(left.typeId()) + "," + typeBindings.typeName(right.typeId()), "BOOLEAN");
                    error = true;
                }
            }
            case EQ, NEQ, LT, LTE, GT, GTE -> {
                var comparison=syntax.operator()==guru.interlis.model.BinaryOperator.EQ || syntax.operator()==guru.interlis.model.BinaryOperator.NEQ
                        ?guru.interlis.model.ComparisonOperator.EQUALITY:guru.interlis.model.ComparisonOperator.ORDERING;
                if (!hasEnumValues(typeBindings.effectiveDescriptor(left.typeId()))
                        && !hasEnumValues(typeBindings.effectiveDescriptor(right.typeId()))) {
                    left = inferredBoolean(left, owner);
                    right = inferredBoolean(right, owner);
                }
                left = contextualNumericConstant(left, right.typeId());
                right = contextualNumericConstant(right, left.typeId());
                left = contextualValue(left, right.typeId(), owner);
                right = contextualValue(right, left.typeId(), owner);
                error |= left.error() || right.error();
                if(!error && !comparisonAllowed(left,right,comparison)) {
                    diagnostics.emitTypeMismatch(ownerSource(owner),syntax.range(),typeBindings.typeName(left.typeId())+","+typeBindings.typeName(right.typeId()),"COMPARABLE "+comparison);
                    error=true;
                }
            }
            case PLUS, MINUS, MUL, DIV -> {
                work.checkpoint();
                if (error) {
                    result = left.error() ? left.typeId() : right.typeId();
                } else if (!typeBindings.isNumeric(left.typeId()) || !typeBindings.isNumeric(right.typeId())) {
                    DiagnosticId diagnostic = diagnostics.emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeBindings.typeName(left.typeId()) + "," + typeBindings.typeName(right.typeId()), "NUMERIC");
                    result = typeBindings.nextTypeId();
                    typeBindings.defineType(new ErrorType(result, new Origin.DeclaredOrigin(syntax.range()), List.of(diagnostic)));
                    error = true;
                } else {
                    if (syntax.operator() == BinaryOperator.PLUS || syntax.operator() == BinaryOperator.MINUS) {
                        left = contextualNumericConstant(left, right.typeId());
                        right = contextualNumericConstant(right, left.typeId());
                    }
                    TypeDescriptor a = typeBindings.effectiveDescriptor(left.typeId());
                    TypeDescriptor b = typeBindings.effectiveDescriptor(right.typeId());
                    if (a instanceof NumericType x && b instanceof NumericType y) {
                        result = typeBindings.nextTypeId();
                        typeBindings.defineType(new NumericType(result, new Origin.DeclaredOrigin(syntax.range()),
                                NumericExpressionRanges.infer(syntax.operator(), x.range(), y.range(), work)));
                    } else {
                        // Numeric-family validation above leaves only UNDEFINED here.
                        result = a instanceof NumericType ? right.typeId() : left.typeId();
                    }
                }
            }
        }
        TypedExpression expression = new guru.interlis.model.BinaryExpression(id, result,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(left.id(), right.id()), syntax.operator(),
                left.id(), right.id());
        state.defineExpression(expression);
        return new ExpressionResult(id, result, error);
    }

    private ExpressionResult unaryExpression(ExpressionId id, UnaryExpressionSyntax syntax,
                                             SymbolId owner) {
        ExpressionResult operand = buildExpression(syntax.operand(), owner);
        TypeId type = typeBindings.builtinBoolean(index.byId(owner).orElseThrow().language());
        if (syntax.operator() == guru.interlis.model.UnaryOperator.NOT) {
            operand = contextualEnumConstant(operand, type, owner, "ENUM_CONSTANT");
        }
        boolean error = operand.error();
        if (!error && syntax.operator() == guru.interlis.model.UnaryOperator.NOT && !typeBindings.isBoolean(operand.typeId())) {
            diagnostics.emitTypeMismatch(ownerSource(owner), syntax.range(), typeBindings.typeName(operand.typeId()),
                    "BOOLEAN");
            error = true;
        }
        TypedExpression expression = new guru.interlis.model.UnaryExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(operand.id()), syntax.operator(), operand.id());
        state.defineExpression(expression);
        return new ExpressionResult(id, type, error);
    }

    private ExpressionResult definedExpression(ExpressionId id, DefinedExpressionSyntax syntax,
                                               SymbolId owner) {
        ExpressionResult operand = buildExpression(syntax.operand(), owner);
        TypeId type = typeBindings.builtinBoolean(index.byId(owner).orElseThrow().language());
        TypedExpression expression = new guru.interlis.model.DefinedExpression(id, type,
                operand.error() ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(operand.id()), operand.id());
        state.defineExpression(expression);
        return new ExpressionResult(id, type, operand.error());
    }

    private ExpressionResult constantExpression(ExpressionId id, ConstantExpressionSyntax syntax, SymbolId owner) {
        TypeId type = numericLiteralType(Optional.empty(), syntax.unit(), syntax.range(), owner);
        TypedExpression expression = new guru.interlis.model.ConstantExpression(id, type,
                typeBindings.effectiveDescriptor(type) instanceof ErrorType ? guru.interlis.model.ExpressionTypeState.ERROR : guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.constantName());
        state.defineExpression(expression);
        return new ExpressionResult(id, type, typeBindings.effectiveDescriptor(type) instanceof ErrorType);
    }

    private ExpressionResult undefinedExpression(ExpressionId id, UndefinedExpressionSyntax syntax, SymbolId owner) {
        TypeId type = typeBindings.nextTypeId();
        typeBindings.defineType(new guru.interlis.model.GenericType(type,
                new Origin.PredefinedOrigin(index.byId(owner).orElseThrow().language(), "UNDEFINED"),
                "UNDEFINED"));
        TypedExpression expression = new guru.interlis.model.UndefinedExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of());
        state.defineExpression(expression);
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult errorExpression(SourceId source, SourceRange range, String feature) {
        ExpressionId id = state.nextExpressionId();
        TypeId type = typeBindings.errorType(source, range, feature);
        DiagnosticId diagnostic = diagnostics.emitUnsupported(source, range, feature, feature);
        TypedExpression expression = new ErrorExpression(id, type,
                guru.interlis.model.ExpressionTypeState.ERROR, range, List.of(),
                List.of(diagnostic));
        state.defineExpression(expression);
        return new ExpressionResult(id, type, true);
    }

    private boolean formattedTextValid(ExpressionResult left, ExpressionResult right) {
        TypeDescriptor leftDescriptor = typeBindings.effectiveDescriptor(left.typeId());
        TypeDescriptor rightDescriptor = typeBindings.effectiveDescriptor(right.typeId());
        guru.interlis.model.FormattedType formatted;
        ExpressionId textId;
        if (leftDescriptor instanceof guru.interlis.model.FormattedType f) {
            formatted = f;
            textId = right.id();
        } else if (rightDescriptor instanceof guru.interlis.model.FormattedType f) {
            formatted = f;
            textId = left.id();
        } else {
            return true;
        }
        TypedExpression expression = state.expression(textId);
        if (!(expression instanceof guru.interlis.model.LiteralExpression literal)
                || literal.textValue().isEmpty()) {
            return true;
        }
        String value = literal.textValue().orElseThrow();
        return formats.formattedValues().parse(formatted, value, true).isPresent();
    }

    private boolean unboundEnum(ExpressionResult value) {
        return !value.error() && typeBindings.effectiveDescriptor(value.typeId()) instanceof guru.interlis.model.EnumArgumentType
                && state.expression(value.id()) instanceof guru.interlis.model.ConstantExpression constant
                && constant.constantName().startsWith("#");
    }

    ExpressionResult buildBooleanExpression(ExpressionSyntax syntax, SymbolId owner) {
        return contextualEnumConstant(buildExpression(syntax, owner), booleanType(owner), owner, "ENUM_CONSTANT");
    }

    private ExpressionResult inferredBoolean(ExpressionResult value, SymbolId owner) {
        if (unboundEnum(value) && state.expression(value.id()) instanceof guru.interlis.model.ConstantExpression constant
                && (constant.constantName().equals("#true") || constant.constantName().equals("#false"))) {
            return contextualEnumConstant(value, booleanType(owner), owner, "ENUM_CONSTANT");
        }
        return value;
    }

    ExpressionResult inferValue(ExpressionResult value, SymbolId owner) {
        var inferred = inferredBoolean(value, owner);
        if (!unboundEnum(inferred)) return inferred;
        var constant = (guru.interlis.model.ConstantExpression) state.expression(inferred.id());
        return failedEnumConstant(constant, diagnostics.emitTypeMismatch(ownerSource(owner), constant.range(),
                constant.constantName(), "a concrete enumeration type"));
    }

    private TypeId booleanType(SymbolId owner) {
        return typeBindings.builtinBoolean(index.byId(owner).orElseThrow().language());
    }

    /** Bind constants to their expected value type; shared inherited expressions are only checked by valueAssignable. */
    ExpressionResult contextualValue(ExpressionResult value, TypeId expected, SymbolId owner) {
        if(value.error())return value;
        value=contextualNumericConstant(value,expected);
        if (state.expression(value.id()) instanceof guru.interlis.model.FunctionCallExpression call && isUnitConversion(call)
                && typeBindings.effectiveDescriptor(expected) instanceof NumericType numeric) {
            // Unrestricted NUMERIC is a wildcard formal, not a new unitless destination.
            if(numeric.range().unit().isEmpty() && numeric.range().minimum().isEmpty() && numeric.range().maximum().isEmpty())return value;
            TypeId type=expected;boolean error=!conversionCompatible(call,numeric);
            if(error) {
                var diagnostic=diagnostics.emitRule(index.byId(owner).orElseThrow(),"convertUnit source and destination need a common conversion unit","CONVERT_UNIT",call.range());
                type=typeBindings.nextTypeId();typeBindings.defineType(new ErrorType(type,new Origin.DeclaredOrigin(call.range()),List.of(diagnostic)));
            }
            state.replaceExpression(new guru.interlis.model.FunctionCallExpression(call.id(),type,
                    error?guru.interlis.model.ExpressionTypeState.ERROR:call.typeState(),call.range(),call.children(),call.functionName(),call.targetFunction(),call.argumentPaths()));
            return new ExpressionResult(call.id(),type,error);
        }
        var result=contextualEnumConstant(value,expected,owner,"ENUM_CONSTANT");
        if(state.expression(result.id()) instanceof guru.interlis.model.LiteralExpression literal && literal.textValue().isPresent()
                && typeBindings.effectiveDescriptor(expected) instanceof FormattedType formatted
                && formats.formattedValues().parse(formatted,literal.textValue().orElseThrow(),true).isPresent()) {
            state.replaceExpression(new guru.interlis.model.LiteralExpression(literal.id(),expected,literal.typeState(),literal.range(),literal.children(),literal.literalKind(),literal.lexeme(),literal.decimal(),literal.textValue()));
            return new ExpressionResult(result.id(),expected,false);
        }
        return result;
    }

    private ExpressionResult contextualNumericConstant(ExpressionResult value, TypeId expected) {
        if(value.error() || !(typeBindings.effectiveDescriptor(expected) instanceof NumericType target) || target.range().unit().isEmpty()
                || !(typeBindings.effectiveDescriptor(value.typeId()) instanceof NumericType source) || source.range().unit().isPresent())return value;
        var expression=state.expression(value.id());
        if(!(expression instanceof guru.interlis.model.LiteralExpression literal && literal.literalKind()==guru.interlis.model.LiteralKind.NUMBER)
                && !(expression instanceof guru.interlis.model.ConstantExpression constant && (constant.constantName().equals("PI") || constant.constantName().equals("LNBASE"))))return value;
        TypeId type=typeBindings.nextTypeId();var range=source.range();
        typeBindings.defineType(new NumericType(type,source.origin(),new NumericRange(range.minimum(),range.maximum(),range.circular(),range.direction(),target.range().unit(),range.referenceSystem())));
        if(expression instanceof guru.interlis.model.LiteralExpression literal)
            state.replaceExpression(new guru.interlis.model.LiteralExpression(literal.id(),type,literal.typeState(),literal.range(),literal.children(),literal.literalKind(),literal.lexeme(),literal.decimal(),literal.textValue()));
        else {var constant=(guru.interlis.model.ConstantExpression)expression;
            state.replaceExpression(new guru.interlis.model.ConstantExpression(constant.id(),type,constant.typeState(),constant.range(),constant.children(),constant.constantName()));}
        return new ExpressionResult(value.id(),type,false);
    }

    private boolean isUnitConversion(guru.interlis.model.FunctionCallExpression call) {
        return call.targetFunction().flatMap(id->Optional.ofNullable(state.symbol(id))).filter(FunctionSymbol.class::isInstance)
                .map(Symbol::origin).filter(Origin.PredefinedOrigin.class::isInstance).map(Origin.PredefinedOrigin.class::cast)
                .map(origin->origin.key().equals("INTERLIS.convertUnit")).orElse(false);
    }

    private boolean conversionCompatible(guru.interlis.model.FunctionCallExpression call,NumericType target) {
        var descriptor=typeBindings.effectiveDescriptor(state.expression(call.children().getFirst()).typeId());
        if(guru.interlis.model.internal.TypeCompatibility.isUndefined(descriptor))return true;
        var from=(NumericType)descriptor;
        var sourceUnit=from.range().unit();var targetUnit=target.range().unit();
        if(sourceUnit.isEmpty() || targetUnit.isEmpty())return sourceUnit.isEmpty() && targetUnit.isEmpty();
        return guru.interlis.model.internal.TypeCompatibility.unitRelation(sourceUnit.orElseThrow(),targetUnit.orElseThrow(),units::extensionUnit)
                .map(relation->relation.decision()==guru.interlis.model.RelationDecision.ALLOWED).orElse(false);
    }

    boolean valueAssignable(TypedExpression value, TypeId expected) {
        var descriptor=typeBindings.effectiveDescriptor(expected);
        if(value instanceof guru.interlis.model.FunctionCallExpression call && isUnitConversion(call) && descriptor instanceof NumericType numeric)
            return conversionCompatible(call,numeric);
        if(value instanceof guru.interlis.model.ConstantExpression constant && constant.constantName().startsWith("#")) {
            var actual = typeBindings.effectiveDescriptor(value.typeId());
            if (hasEnumValues(descriptor)) {
                return (actual instanceof guru.interlis.model.EnumArgumentType || compatibleEnumTypes(actual, descriptor))
                        && enumContains(descriptor, constant.constantName().substring(1));
            }
        }
        if(value instanceof guru.interlis.model.LiteralExpression literal && literal.textValue().isPresent() && descriptor instanceof FormattedType formatted)
            return formats.formattedValues().parse(formatted,literal.textValue().orElseThrow(),true).isPresent();
        return typeBindings.assignable(value.typeId(),expected);
    }

    private ExpressionResult contextualEnumConstant(ExpressionResult value, TypeId context, SymbolId owner, String rule) {
        TypeDescriptor expected = typeBindings.effectiveDescriptor(context);
        if (!unboundEnum(value) || !hasEnumValues(expected)) return value;
        var actual = (guru.interlis.model.ConstantExpression) state.expression(value.id());
        String path = actual.constantName().substring(1);
        if (!enumContains(expected, path)) {
            var cause = rule.equals("ENUM_ARGUMENT")
                    ? diagnostics.emitRule(index.byId(owner).orElseThrow(),
                            "Value is not a member of the expected enumeration: " + actual.constantName(), rule, actual.range())
                    : diagnostics.emitTypeMismatch(ownerSource(owner), actual.range(), actual.constantName(), typeBindings.typeName(context));
            return failedEnumConstant(actual, cause);
        }
        state.replaceExpression(new guru.interlis.model.ConstantExpression(actual.id(),context,actual.typeState(),actual.range(),actual.children(),actual.constantName()));
        if(expected instanceof EnumerationType enumeration) {
            var segments=new ArrayList<ReferenceSegment>();int start=actual.range().startOffset()+1;String prefix="";
            for(String part:path.split("\\.")) {
                prefix=prefix.isEmpty()?part:prefix+"."+part;
                var target=enumMember(enumeration,prefix);
                var range=new SourceRange(actual.range().source(),actual.range().revision(),start,start+part.length());
                segments.add(new ReferenceSegment(part,range,Optional.ofNullable(target)));start+=part.length()+1;
            }
            SymbolId target=segments.getLast().target().orElse(null);
            var range=new SourceRange(actual.range().source(),actual.range().revision(),actual.range().startOffset()+1,actual.range().endOffset());
            references.recordEnumReference(owner, range, segments, target);
        }
        return new ExpressionResult(value.id(),context,value.error());
    }

    private ExpressionResult failedEnumConstant(guru.interlis.model.ConstantExpression constant, DiagnosticId cause) {
        TypeId error = typeBindings.nextTypeId();
        typeBindings.defineType(new ErrorType(error, new Origin.DeclaredOrigin(constant.range()), List.of(cause)));
        state.replaceExpression(new guru.interlis.model.ConstantExpression(constant.id(), error,
                guru.interlis.model.ExpressionTypeState.ERROR, constant.range(), constant.children(), constant.constantName()));
        return new ExpressionResult(constant.id(), error, true);
    }

    private SymbolId enumMember(EnumerationType type,String path) {
        Set<TypeId> seen=new HashSet<>();
        for(var current=type;current!=null && seen.add(current.id());) {
            for(var entry:state.declaredTypesInBuildOrder()) {
                work.checkpoint();TypeDescriptor candidate=typeBindings.effectiveDescriptor(entry.getValue());
                while(candidate instanceof CollectionType collection)candidate=typeBindings.effectiveDescriptor(collection.elementType());
                if(candidate==null || !candidate.id().equals(current.id()))continue;
                for(var child:index.children(entry.getKey()))if(child.kind()==SymbolKind.ENUM_ELEMENT && child.name().equals(path))return child.id();
            }
            var base=current.baseType().map(typeBindings::effectiveDescriptor).orElse(null);current=base instanceof EnumerationType e?e:null;
        }
        return null;
    }

    private boolean comparisonAllowed(ExpressionResult left, ExpressionResult right, guru.interlis.model.ComparisonOperator operator) {
        if(!formattedTextValid(left,right))return false;
        TypeDescriptor a=typeBindings.effectiveDescriptor(left.typeId()),b=typeBindings.effectiveDescriptor(right.typeId());
        if(a instanceof FormattedType && state.expression(right.id()) instanceof guru.interlis.model.LiteralExpression literal
                && literal.literalKind()==guru.interlis.model.LiteralKind.TEXT)b=a;
        if(b instanceof FormattedType && state.expression(left.id()) instanceof guru.interlis.model.LiteralExpression literal
                && literal.literalKind()==guru.interlis.model.LiteralKind.TEXT)a=b;
        return guru.interlis.model.internal.TypeCompatibility.compare(a,b,operator,typeBindings::effectiveDescriptor,id->typeBindings.effectiveDescriptor(state.declaredType(id))).decision()
                ==guru.interlis.model.RelationDecision.ALLOWED;
    }

    private Optional<DecimalLiteral> decimal(String lexeme) {
        try {
            return Optional.of(DecimalLiteral.parse(lexeme));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    SourceId ownerSource(SymbolId owner) {
        return index.modelOfSymbol(owner).flatMap(index::byId)
                .map(DeclInfo::sourceId).orElseGet(() -> sourceSet.sources().keySet().iterator().next());
    }

    record ExpressionResult(ExpressionId id, TypeId typeId, boolean error) {
    }

    void defineExpression(TypedExpression expression) { state.defineExpression(expression); }

    ExpressionId nextExpressionId() { return state.nextExpressionId(); }
}
