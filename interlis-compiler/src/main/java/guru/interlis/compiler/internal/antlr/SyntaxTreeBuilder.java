package guru.interlis.compiler.internal.antlr;

import guru.interlis.model.BinaryOperator;
import guru.interlis.model.Cardinality;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.LiteralKind;
import guru.interlis.model.Modifier;
import guru.interlis.model.NumericDirection;
import guru.interlis.model.NumericRange;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.TextKind;
import guru.interlis.model.UnaryOperator;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.AssociationDeclarationSyntax;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.BooleanTypeSyntax;
import guru.interlis.syntax.BinaryExpressionSyntax;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.AlignmentTypeSyntax;
import guru.interlis.syntax.BlackboxTypeSyntax;
import guru.interlis.syntax.ClassValueTypeSyntax;
import guru.interlis.syntax.CollectionTypeSyntax;
import guru.interlis.syntax.OidTypeSyntax;
import guru.interlis.syntax.ConstantExpressionSyntax;
import guru.interlis.syntax.ConstraintDeclarationSyntax;
import guru.interlis.syntax.ConstraintKind;
import guru.interlis.syntax.CoordinateTypeSyntax;
import guru.interlis.syntax.DefinedExpressionSyntax;
import guru.interlis.syntax.DeclarationSyntax;
import guru.interlis.syntax.DocumentSyntax;
import guru.interlis.syntax.DomainDeclarationSyntax;
import guru.interlis.syntax.DomainReferenceSyntax;
import guru.interlis.syntax.ExternalConstraintSyntax;
import guru.interlis.syntax.FormattedTypeSyntax;
import guru.interlis.syntax.BuiltinFunctionExpressionSyntax;
import guru.interlis.syntax.ClassConstantExpressionSyntax;
import guru.interlis.syntax.GenericConstantExpressionSyntax;
import guru.interlis.syntax.GenericTypeSyntax;
import guru.interlis.syntax.EnumTreeValueSyntax;
import guru.interlis.syntax.ExistenceConstraintSyntax;
import guru.interlis.syntax.EnumerationConstantExpressionSyntax;
import guru.interlis.syntax.FunctionCallExpressionSyntax;
import guru.interlis.syntax.FunctionDeclarationSyntax;
import guru.interlis.syntax.LineTypeSyntax;
import guru.interlis.syntax.ViewDeclarationSyntax;
import guru.interlis.syntax.EnumerationTypeSyntax;
import guru.interlis.syntax.ExpressionSyntax;
import guru.interlis.syntax.ImportSyntax;
import guru.interlis.syntax.LiteralExpressionSyntax;
import guru.interlis.syntax.ModelSyntax;
import guru.interlis.syntax.NameSyntax;
import guru.interlis.syntax.NumericTypeSyntax;
import guru.interlis.syntax.ParenthesizedExpressionSyntax;
import guru.interlis.syntax.PathExpressionSyntax;
import guru.interlis.syntax.ReferenceTypeSyntax;
import guru.interlis.syntax.RoleDeclarationSyntax;
import guru.interlis.syntax.StructureDeclarationSyntax;
import guru.interlis.syntax.SyntaxKind;
import guru.interlis.syntax.SyntaxNode;
import guru.interlis.syntax.SyntaxNodeId;
import guru.interlis.syntax.TemporalTypeSyntax;
import guru.interlis.syntax.TextTypeSyntax;
import guru.interlis.syntax.TopicSyntax;
import guru.interlis.syntax.UnitDeclarationSyntax;
import guru.interlis.syntax.TypeSyntax;
import guru.interlis.syntax.UnaryExpressionSyntax;
import guru.interlis.syntax.UndefinedExpressionSyntax;
import guru.interlis.syntax.UnsupportedDeclarationSyntax;
import guru.interlis.syntax.UnsupportedExpressionSyntax;
import guru.interlis.syntax.UnsupportedTypeSyntax;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Lowers the ANTLR result into the project's own syntax types. Every source
 * construct either becomes a typed node or an explicit unsupported node plus
 * feature id; no production is silently dropped.
 */
final class SyntaxTreeBuilder {
    private final SourceText source;
    private final AntlrOffsetMap offsets;
    private final CommonTokenStream tokenStream;
    private final LanguageVersion language;
    private final guru.interlis.compiler.AnalysisBudget budget;
    private long nodeCounter;
    private final Set<String> unsupportedFeatures = new LinkedHashSet<>();
    private final List<SourceRange> recoveredRanges = new ArrayList<>();
    private final List<LanguageProfile.Feature> versionViolations = new ArrayList<>();
    private final List<SourceRange> versionViolationRanges = new ArrayList<>();
    private final List<guru.interlis.diagnostics.DiagnosticDraft> propertyDiagnostics = new ArrayList<>();

    List<guru.interlis.diagnostics.DiagnosticDraft> propertyDiagnostics() { return List.copyOf(propertyDiagnostics); }

    private void addModifier(Set<Modifier> modifiers, Modifier value, Token token) {
        if (!modifiers.add(value)) propertyDiagnostics.add(guru.interlis.diagnostics.DiagnosticDraft.of(
                new guru.interlis.diagnostics.DiagnosticCode("ILI-SYN-0007"), guru.interlis.diagnostics.Severity.WARNING,
                guru.interlis.diagnostics.Phase.PARSING, "diagnostic.syntax.redundantProperty")
                .at(tokenRange(token)).argument(new guru.interlis.diagnostics.DiagnosticArgument.TokenArgument("property", token.getText())));
    }

    SyntaxTreeBuilder(SourceText source, AntlrOffsetMap offsets,
                      CommonTokenStream tokenStream, LanguageVersion language, guru.interlis.compiler.AnalysisBudget budget) {
        this.source = Objects.requireNonNull(source, "source");
        this.offsets = Objects.requireNonNull(offsets, "offsets");
        this.tokenStream = Objects.requireNonNull(tokenStream, "tokenStream");
        this.language = Objects.requireNonNull(language, "language");
        this.budget=Objects.requireNonNull(budget,"budget");
    }

    Set<String> unsupportedFeatures() {
        return Set.copyOf(unsupportedFeatures);
    }

    List<SourceRange> recoveredRanges() {
        return List.copyOf(recoveredRanges);
    }

    List<LanguageProfile.Feature> versionViolations() {
        return List.copyOf(versionViolations);
    }

    List<SourceRange> versionViolationRanges() {
        return List.copyOf(versionViolationRanges);
    }

    DocumentSyntax build(InterlisParser.Interlis2defContext context) {
        List<ModelSyntax> models = new ArrayList<>();
        if (context != null && context.modeldef() != null) {
            for (InterlisParser.ModeldefContext model : context.modeldef()) {
                models.add(buildModel(model));
            }
        }
        return new DocumentSyntax(nextId(), range(context), models);
    }

    private ModelSyntax buildModel(InterlisParser.ModeldefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-model>" : nameToken.getText();
        String issuer = context.STRING().isEmpty() ? "" : unquote(context.STRING(0).getText());
        String version = context.STRING().size() < 2 ? "" : unquote(context.STRING(1).getText());
        List<ImportSyntax> imports = buildImports(context);
        List<SyntaxNode> members = buildModelMembers(context);
        guru.interlis.model.ModelKind kind = context.TYPE() != null
                ? guru.interlis.model.ModelKind.TYPE
                : context.REFSYSTEM() != null ? guru.interlis.model.ModelKind.REFSYSTEM
                : context.SYMBOLOGY() != null ? guru.interlis.model.ModelKind.SYMBOLOGY
                : guru.interlis.model.ModelKind.NORMAL;
        return new ModelSyntax(nextId(), range(context), name, kind,
                context.CONTRACTED() != null, version, issuer, language,
                translationSource(context), translationVersion(context),
                endName(context.END(), context), imports, members, modelOptions(context));
    }

    private guru.interlis.model.ModelOptions modelOptions(InterlisParser.ModeldefContext context) {
        return new guru.interlis.model.ModelOptions(modelLanguage(context).map(value -> new guru.interlis.model.ModelOptions.TextValue(value,
                new guru.interlis.model.Origin.DeclaredOrigin(tokenRange(context.LPAR().getSymbol(), context.RPAR().getSymbol())))), Optional.ofNullable(context.NOINCREMENTALTRANSFER())
                .map(token -> new guru.interlis.model.Origin.DeclaredOrigin(tokenRange(token.getSymbol()))),
                modelTextOption(context, context.CHARSET()), modelTextOption(context, context.XMLNS()));
    }

    private Optional<guru.interlis.model.ModelOptions.TextValue> modelTextOption(InterlisParser.ModeldefContext context, TerminalNode keyword) {
        if (keyword == null) return Optional.empty();
        return context.STRING().stream().filter(value -> value.getSymbol().getTokenIndex() > keyword.getSymbol().getTokenIndex())
                .findFirst().map(value -> new guru.interlis.model.ModelOptions.TextValue(unquote(value.getText()),
                        new guru.interlis.model.Origin.DeclaredOrigin(tokenRange(keyword.getSymbol(), value.getSymbol()))));
    }

    private Optional<String> modelLanguage(InterlisParser.ModeldefContext context) {
        if (context.LPAR() == null || context.RPAR() == null) {
            return Optional.empty();
        }
        int start = context.LPAR().getSymbol().getTokenIndex() + 1;
        int end = context.RPAR().getSymbol().getTokenIndex();
        for (int i = start; i < end; i++) {
            Token token = tokenStream.get(i);
            if (token.getType() == InterlisParser.Name) {
                return Optional.of(token.getText());
            }
        }
        return Optional.empty();
    }

    private Optional<NameSyntax> translationSource(InterlisParser.ModeldefContext context) {
        if (context.TRANSLATION() == null) {
            return Optional.empty();
        }
        int stop = context.getStop().getTokenIndex();
        int i = context.TRANSLATION().getSymbol().getTokenIndex() + 1;
        while (i <= stop && tokenStream.get(i).getType() != InterlisParser.OF) {
            i++;
        }
        i++;
        if (i > stop || tokenStream.get(i).getType() != InterlisParser.Name) {
            return Optional.empty();
        }
        Token token = tokenStream.get(i);
        return Optional.of(new NameSyntax(nextId(), tokenRange(token), List.of(
                new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token)))));
    }

    private Optional<String> translationVersion(InterlisParser.ModeldefContext context) {
        if (context.TRANSLATION() == null) {
            return Optional.empty();
        }
        int stop = context.getStop().getTokenIndex();
        int i = context.TRANSLATION().getSymbol().getTokenIndex() + 1;
        while (i <= stop && tokenStream.get(i).getType() != InterlisParser.LSBR) {
            i++;
        }
        i++;
        if (i > stop || tokenStream.get(i).getType() != InterlisParser.STRING) {
            return Optional.empty();
        }
        return Optional.of(unquote(tokenStream.get(i).getText()));
    }

    private List<guru.interlis.syntax.OidRuleSyntax> oidRules(ParserRuleContext context) {
        List<guru.interlis.syntax.OidRuleSyntax> result = new ArrayList<>();
        if (context.children != null) for (var child : context.children) {
            if (child instanceof InterlisParser.BasketOidRuleContext rule) {
                result.add(new guru.interlis.syntax.OidRuleSyntax(true,
                        Optional.of(nameFromContext(rule.oidDomainRef())), range(rule)));
            } else if (child instanceof InterlisParser.ObjectOidRuleContext rule) {
                result.add(new guru.interlis.syntax.OidRuleSyntax(false,
                        rule.oidDomainRef() == null ? Optional.empty() : Optional.of(nameFromContext(rule.oidDomainRef())), range(rule)));
            }
        }
        return List.copyOf(result);
    }

    private Optional<String> endName(TerminalNode end, ParserRuleContext context) {
        if (end == null) {
            return Optional.empty();
        }
        int endIndex = end.getSymbol().getTokenIndex();
        for (int i = endIndex + 1; i <= context.getStop().getTokenIndex(); i++) {
            Token token = tokenStream.get(i);
            if (token.getType() == InterlisParser.Name) {
                return Optional.of(token.getText());
            }
        }
        return Optional.empty();
    }

    private List<ImportSyntax> buildImports(InterlisParser.ModeldefContext context) {
        List<ImportSyntax> result = new ArrayList<>();
        int start = context.getStart().getTokenIndex();
        int stop = context.getStop().getTokenIndex();
        int cursor = start;
        while (cursor <= stop) {
            Token token = tokenStream.get(cursor);
            if (token.getType() == InterlisParser.IMPORTS) {
                int index = cursor + 1;
                boolean unqualified = false;
                while (index <= stop) {
                    Token current = tokenStream.get(index);
                    if (current.getType() == InterlisParser.SEMI) {
                        break;
                    }
                    if (current.getType() == InterlisParser.UNQUALIFIED) unqualified = true;
                    if (current.getType() == InterlisParser.Name
                            || current.getType() == InterlisParser.INTERLIS) {
                        result.add(new ImportSyntax(nextId(), tokenRange(current),
                                current.getText(), unqualified, Optional.empty()));
                        unqualified = false;
                    }
                    index++;
                }
                cursor = index;
            }
            cursor++;
        }
        return result;
    }

    private List<SyntaxNode> buildModelMembers(InterlisParser.ModeldefContext context) {
        List<Member> members = new ArrayList<>();
        for (InterlisParser.TopicDefContext topic : context.topicDef()) {
            members.add(new Member(topic.getStart().getTokenIndex(), buildTopic(topic)));
        }
        for (InterlisParser.ClassDefContext clazz : context.classDef()) {
            members.add(new Member(clazz.getStart().getTokenIndex(), buildClass(clazz)));
        }
        for (InterlisParser.StructureDefContext structure : context.structureDef()) {
            members.add(new Member(structure.getStart().getTokenIndex(), buildStructure(structure)));
        }
        for (InterlisParser.DomainDefContext domain : context.domainDef()) {
            int domainIndex = domain.getStart().getTokenIndex();
            for (DomainDeclarationSyntax declaration : buildDomains(domain)) {
                members.add(new Member(domainIndex++, declaration));
            }
        }
        for (InterlisParser.UnitDefContext unit : context.unitDef()) {
            members.add(new Member(unit.getStart().getTokenIndex(),
                    isDomainEnumeration(unit) ? buildDomainFromUnit(unit) : buildUnit(unit)));
        }
        for (InterlisParser.FunctionDefContext function : context.functionDef()) {
            members.add(new Member(function.getStart().getTokenIndex(), buildFunction(function)));
        }
        for (var parameter : context.runTimeParameterDef()) {
            members.add(new Member(parameter.getStart().getTokenIndex(),
                    new guru.interlis.syntax.ParameterDeclarationSyntax(nextId(), range(parameter), parameter.Name().getText(),
                            Optional.of(tokenRange(parameter.Name().getSymbol())), parameter.attrTypeDef().MANDATORY() != null, Set.of(),
                            buildAttrTypeDef(parameter.attrTypeDef()).type())));
        }
        for (var basket : context.metaDataBasketDef()) {
            members.add(new Member(basket.getStart().getTokenIndex(),
                    buildMetadataBasket(basket)));
        }
        for (var definition : context.contextDef()) {
            members.add(new Member(definition.getStart().getTokenIndex(),
                    buildContext(definition)));
        }
        for (var lineForm : context.lineFormTypeDef()) {
            members.add(new Member(lineForm.getStart().getTokenIndex(),
                    new guru.interlis.syntax.LineFormDeclarationSyntax(nextId(), range(lineForm),
                            lineForm.Name(0).getText(), Optional.of(tokenRange(lineForm.Name(0).getSymbol())),
                            nameSyntax(List.of(lineForm.Name(1).getSymbol())))));
        }
        members.sort(Comparator.comparingInt(Member::index));
        return members.stream().map(Member::node).toList();
    }

    private guru.interlis.syntax.MetadataBasketDeclarationSyntax buildMetadataBasket(InterlisParser.MetaDataBasketDefContext context) {
        List<guru.interlis.syntax.MetadataBasketDeclarationSyntax.ObjectDeclaration> objects = new ArrayList<>();
        for (var group : context.metaObjectGroup()) {
            for (int i = 1; i < group.Name().size(); i++) {
                var name = group.Name(i).getSymbol();
                objects.add(new guru.interlis.syntax.MetadataBasketDeclarationSyntax.ObjectDeclaration(nextId(), tokenRange(name),
                        name.getText(), nameSyntax(List.of(group.Name(0).getSymbol()))));
            }
        }
        return new guru.interlis.syntax.MetadataBasketDeclarationSyntax(nextId(), range(context), context.Name()==null?"<missing-basket>":context.Name().getText(),
                context.Name()==null?Optional.empty():Optional.of(tokenRange(context.Name().getSymbol())), context.FINAL() == null ? Set.of() : Set.of(Modifier.FINAL),
                context.REFSYSTEM() != null ? guru.interlis.model.MetadataBasketSymbol.Kind.REFSYSTEM : guru.interlis.model.MetadataBasketSymbol.Kind.SIGN,
                nameFromContext(context.topicRef()), Optional.ofNullable(context.metaDataBasketRef()).map(this::nameFromContext), objects);
    }

    private SyntaxNode buildExternalConstraints(InterlisParser.ConstraintsDefContext context) {
        NameSyntax target = nameFromContext(context.classOrAssociationRef());
        List<SyntaxNode> children = new ArrayList<>();
        for (InterlisParser.ConstraintDefContext constraint : context.constraintDef()) {
            ConstraintDeclarationSyntax parsed = buildConstraint(constraint);
            children.add(new ExternalConstraintSyntax(nextId(), range(constraint), target, parsed));
        }
        return new guru.interlis.syntax.RecoveredSyntax(nextId(), range(context), children,
                "external-constraints");
    }

    private ViewDeclarationSyntax.Base viewBase(InterlisParser.RenamedViewableRefContext c, boolean orNull) {
        NameSyntax target = nameFromContext(c.viewableRef());
        NameSyntax alias = c.Name() == null ? new NameSyntax(nextId(), target.segments().getLast().range(),
                List.of(target.segments().getLast())) : nameFromToken(c.Name().getSymbol());
        return new ViewDeclarationSyntax.Base(alias, target, c.Name() != null, orNull);
    }

    private NameSyntax nameFromToken(Token token) {
        return new NameSyntax(nextId(), tokenRange(token), List.of(new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token))));
    }

    private Set<Modifier> finalViewAttributeModifiers(ParserRuleContext context) {
        var result = new java.util.HashSet<>(directModifiers(context));
        result.add(Modifier.FINAL); return Set.copyOf(result);
    }

    private ViewDeclarationSyntax buildView(InterlisParser.ViewDefContext c) {
        var kind = ViewDeclarationSyntax.FormationKind.PROJECTION;
        List<ViewDeclarationSyntax.Base> bases = new ArrayList<>();
        List<PathExpressionSyntax> paths = new ArrayList<>();
        var f = c.formationDef();
        if (f != null) {
            if (f.projection() != null) bases.add(viewBase(f.projection().renamedViewableRef(), false));
            else if (f.join() != null) {
                kind = ViewDeclarationSyntax.FormationKind.JOIN;
                var refs = f.join().renamedViewableRef();
                for (int i = 0; i < refs.size(); i++) {
                    int end = i + 1 < refs.size() ? refs.get(i + 1).getStart().getTokenIndex() : f.join().getStop().getTokenIndex() + 1;
                    boolean nullable = false;
                    for (int j = refs.get(i).getStop().getTokenIndex() + 1; j < end; j++) nullable |= tokenStream.get(j).getType() == InterlisParser.NULL;
                    bases.add(viewBase(refs.get(i), nullable));
                }
            } else if (f.union() != null) {
                kind = ViewDeclarationSyntax.FormationKind.UNION;
                f.union().renamedViewableRef().forEach(x -> bases.add(viewBase(x, false)));
            } else if (f.aggregation() != null) {
                kind = ViewDeclarationSyntax.FormationKind.AGGREGATION;
                bases.add(viewBase(f.aggregation().renamedViewableRef(), false));
                for (var key : f.aggregation().uniqueEl()) {
                    var expression = pathExpression(key.objectOrAttributePath());
                    if (expression instanceof PathExpressionSyntax path) paths.add(path);
                }
            } else if (f.inspection() != null) {
                var inspection = inspectionFormation(f.inspection());
                kind = inspection.kind(); bases.addAll(inspection.bases()); paths.addAll(inspection.paths());
            }
        }
        List<ViewDeclarationSyntax.BaseExtension> extensions = new ArrayList<>();
        for (var e : c.baseExtensionDef()) extensions.add(new ViewDeclarationSyntax.BaseExtension(nameFromToken(e.Name().getSymbol()),
                e.renamedViewableRef().stream().map(x -> viewBase(x, false)).toList()));
        List<ViewDeclarationSyntax.Member> members = new ArrayList<>();
        if (c.viewAttributes() != null) for (var a : c.viewAttributes().viewAttribute()) {
            if (a.ALL() != null) members.add(new ViewDeclarationSyntax.AllOf(nameFromToken(a.Name().getSymbol())));
            else if (a.attributeDef() != null) members.add(new ViewDeclarationSyntax.Attribute(buildAttribute(a.attributeDef())));
            else members.add(new ViewDeclarationSyntax.Attribute(new AttributeDeclarationSyntax(nextId(), range(a), a.Name().getText(),
                    Optional.of(tokenRange(a.Name().getSymbol())), false, a.TRANSIENT().size() > 0, true, false, false,
                    finalViewAttributeModifiers(a), new GenericTypeSyntax(nextId(), range(a), "INFERRED"), List.of(buildExpression(a.expression())))));
        }
        return new ViewDeclarationSyntax(nextId(), range(c), c.Name(0)==null?"<missing-view>":c.Name(0).getText(),
                c.Name(0)==null?Optional.empty():Optional.of(tokenRange(c.Name(0).getSymbol())),
                directModifiers(c), c.viewRef() == null ? Optional.empty() : Optional.of(nameFromContext(c.viewRef())),
                f == null ? Optional.empty() : Optional.of(new ViewDeclarationSyntax.Formation(kind, bases, paths)), extensions,
                c.selection().stream().map(x -> buildExpression(x.expression())).toList(), members,
                c.constraintDef().stream().map(this::buildConstraint).toList(),
                c.Name().size() > 1 ? Optional.of(c.Name(c.Name().size() - 1).getText()) : Optional.empty());
    }

    private FunctionDeclarationSyntax buildFunction(InterlisParser.FunctionDefContext context) {
        checkFunctionList(context.argumentDef().size(), context.LPAR(), context.RPAR(), LanguageProfile.Feature.EMPTY_FUNCTION_PARAMETERS);
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-function>" : nameToken.getText();
        List<FunctionDeclarationSyntax.FunctionParameterSyntax> parameters = new ArrayList<>();
        for (InterlisParser.ArgumentDefContext argument : context.argumentDef()) {
            String parameterName = argument.Name() == null ? "<missing>" : argument.Name().getText();
            parameters.add(new FunctionDeclarationSyntax.FunctionParameterSyntax(parameterName,
                    buildArgumentType(argument.argumentType()), mandatoryArgument(argument.argumentType())));
        }
        TypeSyntax returnType;
        if (context.BOOLEAN() != null) {
            returnType = new BooleanTypeSyntax(nextId(), tokenRange(context.BOOLEAN().getSymbol()));
        } else if (context.argumentType() != null) {
            returnType = buildArgumentType(context.argumentType());
        } else if(context.Name().isEmpty()) {
            returnType=new UnsupportedTypeSyntax(nextId(),range(context),"MISSING_FUNCTION_RETURN_TYPE","missing function result type");
        } else {
            Token returnName = context.Name().getLast().getSymbol();
            NameSyntax reference = new NameSyntax(nextId(), tokenRange(returnName),
                    List.of(new NameSyntax.NameSegmentSyntax(returnName.getText(), tokenRange(returnName))));
            returnType = new DomainReferenceSyntax(nextId(), reference.range(), reference);
        }
        return new FunctionDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), parameters, returnType, mandatoryArgument(context.argumentType()),
                Optional.ofNullable(context.Explanation()).map(node->new guru.interlis.syntax.SourceMetadata.Text(node.getText().substring(2,node.getText().length()-2).strip(),tokenRange(node.getSymbol()))));
    }

    private boolean mandatoryArgument(InterlisParser.ArgumentTypeContext context) {
        return context!=null && context.attrTypeDef()!=null && context.attrTypeDef().MANDATORY()!=null;
    }

    /** A missing/recovered delimiter already has a syntax error; only closed source lists have a profile. */
    private void checkFunctionList(int count, TerminalNode opening, TerminalNode closing, LanguageProfile.Feature feature) {
        if (count != 0 || LanguageProfile.supports(language, feature) || opening == null || closing == null
                || opening.getSymbol().getTokenIndex() < 0 || closing.getSymbol().getTokenIndex() < 0) return;
        versionViolations.add(feature);
        versionViolationRanges.add(tokenRange(opening.getSymbol(), closing.getSymbol()));
    }

    private TypeSyntax buildArgumentType(InterlisParser.ArgumentTypeContext context) {
        if (context.attrTypeDef() != null) {
            return buildAttrTypeDef(context.attrTypeDef()).type();
        }
        if (context.OBJECT() != null || context.OBJECTS() != null) {
            var restricted = context.restrictedClassOrAssRef();
            if (restricted == null) {
                return new guru.interlis.syntax.ObjectArgumentTypeSyntax(nextId(), range(context),
                        Optional.of(nameFromContext(context.viewRef())), List.of(), context.OBJECTS() != null);
            }
            var names = restricted.classOrAssociationRef();
            boolean anyClass = restricted.ANYCLASS() != null;
            return new guru.interlis.syntax.ObjectArgumentTypeSyntax(nextId(), range(context),
                    anyClass ? Optional.empty() : Optional.of(nameFromContext(names.getFirst())),
                    names.stream().skip(anyClass ? 0 : 1).map(this::nameFromContext).toList(),
                    context.OBJECTS() != null);
        }
        if(context.ENUMVAL()!=null || context.ENUMTREEVAL()!=null)
            return new guru.interlis.syntax.EnumArgumentTypeSyntax(nextId(),range(context),context.ENUMTREEVAL()!=null);
        // These argument families need their own bound semantics; do not turn them
        // into a default BOOLEAN or a class-value type with a different meaning.
        return unsupportedType(context, "FUNCTION_ARGUMENT_TYPE", "function argument type");
    }

    private UnitDeclarationSyntax buildUnit(InterlisParser.UnitDefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-unit>" : nameToken.getText();
        Optional<NameSyntax> base = context.unitRef() == null ? Optional.empty()
                : Optional.of(nameFromContext(context.unitRef()));
        String definition = "";
        if (context.EQ() != null && context.SEMI() != null) {
            int eq = context.EQ().getSymbol().getTokenIndex();
            int semi = context.SEMI().getSymbol().getTokenIndex();
            if (eq + 1 < semi) {
                int start = tokenStream.get(eq + 1).getStartIndex();
                int stop = tokenStream.get(semi - 1).getStopIndex();
                if (start >= 0 && stop >= start) {
                    var definitionRange = offsets.range(start, stop);
                    definition = source.text().substring(definitionRange.startOffset(), definitionRange.endOffset());
                }
            }
        }
        Token binding = context.LSBR() == null ? nameToken : context.Name(1).getSymbol();
        var components = new ArrayList<UnitDeclarationSyntax.Component>();
        if (context.composedUnit() != null) {
            var operator = guru.interlis.model.UnitSymbol.Operator.MULTIPLY;
            for (var child : context.composedUnit().children) {
                if (child instanceof TerminalNode token && token.getSymbol().getType() == InterlisParser.DIV)
                    operator = guru.interlis.model.UnitSymbol.Operator.DIVIDE;
                else if (child instanceof TerminalNode token && token.getSymbol().getType() == InterlisParser.MUL)
                    operator = guru.interlis.model.UnitSymbol.Operator.MULTIPLY;
                else if (child instanceof InterlisParser.UnitRefContext ref)
                    components.add(new UnitDeclarationSyntax.Component(operator, nameFromContext(ref)));
            }
        }
        Optional<NameSyntax> derived = context.derivedUnit() == null ? Optional.empty()
                : Optional.of(nameFromContext(context.derivedUnit().unitRef()));
        var factors = new ArrayList<guru.interlis.model.UnitSymbol.ConversionFactor>();
        Optional<String> function = Optional.empty();
        if (context.derivedUnit() != null) {
            var conversion = context.derivedUnit();
            if (conversion.FUNCTION() != null) {
                function = Optional.ofNullable(conversion.Explanation()).map(ParseTree::getText);
            } else {
                var operator = guru.interlis.model.UnitSymbol.Operator.MULTIPLY;
                boolean invalidConstant = false;
                for (var child : conversion.children) {
                    if (child instanceof TerminalNode token && token.getSymbol().getType() == InterlisParser.DIV)
                        operator = guru.interlis.model.UnitSymbol.Operator.DIVIDE;
                    else if (child instanceof TerminalNode token && token.getSymbol().getType() == InterlisParser.MUL)
                        operator = guru.interlis.model.UnitSymbol.Operator.MULTIPLY;
                    else if (child instanceof InterlisParser.DecConstContext value) {
                        var kind = value.PI() != null ? guru.interlis.model.UnitSymbol.ConstantKind.PI
                                : value.LNBASE() != null ? guru.interlis.model.UnitSymbol.ConstantKind.LNBASE
                                : guru.interlis.model.UnitSymbol.ConstantKind.DECIMAL;
                        Optional<DecimalLiteral> decimal = kind == guru.interlis.model.UnitSymbol.ConstantKind.DECIMAL
                                ? decimal(value.getText(),range(value)) : Optional.empty();
                        if (kind != guru.interlis.model.UnitSymbol.ConstantKind.DECIMAL || decimal.isPresent())
                            factors.add(new guru.interlis.model.UnitSymbol.ConversionFactor(operator, kind, decimal, range(value)));
                        else invalidConstant = true;
                    }
                }
                // The numeric-literal guard emitted by decimal() prevents a schema.
                // Do not publish a partial formula as though its factors were complete.
                if (invalidConstant) factors.clear();
            }
        }
        return new UnitDeclarationSyntax(nextId(), range(context), binding == null ? name : binding.getText(),
                optionalRange(binding, context), name, context.ABSTRACT() != null, base, derived, components, factors, function, definition);
    }

    private guru.interlis.syntax.ContextDeclarationSyntax buildContext(InterlisParser.ContextDefContext context) {
        var mappings = context.contextMapping().stream().map(m ->
                new guru.interlis.syntax.ContextDeclarationSyntax.Mapping(nameFromContext(m.domainRef(0)),
                        m.domainRef().subList(1, m.domainRef().size()).stream().map(this::nameFromContext).toList())).toList();
        return new guru.interlis.syntax.ContextDeclarationSyntax(nextId(), range(context), context.Name().getText(),
                Optional.of(tokenRange(context.Name().getSymbol())), mappings);
    }

    private TopicSyntax buildTopic(InterlisParser.TopicDefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-topic>" : nameToken.getText();
        Set<Modifier> modifiers = directModifiers(context);
        if (context.VIEW() != null) {
            modifiers.add(Modifier.VIEW);
        }
        Optional<NameSyntax> base = Optional.empty();
        if (context.EXTENDS() != null && !context.topicRef().isEmpty()) {
            base = Optional.of(nameFromContext(context.topicRef(0)));
        }
        List<NameSyntax> dependencies = new ArrayList<>();
        if (!context.DEPENDS().isEmpty()) {
            int skip = context.EXTENDS() != null ? 1 : 0;
            for (int i = skip; i < context.topicRef().size(); i++) {
                dependencies.add(nameFromContext(context.topicRef(i)));
            }
        }
        List<SyntaxNode> members = new ArrayList<>();
        for (InterlisParser.DefinitionsContext definition : context.definitions()) {
            members.add(buildDefinition(definition));
        }
        return new TopicSyntax(nextId(), range(context), name, modifiers,
                endName(context.END(), context), base, dependencies, members, oidRules(context),
                context.genericRef().stream().map(this::nameFromContext).toList());
    }

    private SyntaxNode buildDefinition(InterlisParser.DefinitionsContext context) {
        if ((context.DOMAIN() != null && context.domainDef() == null)
                || (context.UNIT() != null && context.unitDef() == null)
                || (context.CONTEXT() != null && context.contextDef() == null)) {
            // The grammar has standalone keyword alternatives and the real
            // declarations; the actual declaration is emitted separately.
            return new guru.interlis.syntax.RecoveredSyntax(nextId(), range(context),
                    List.of(), "standalone-declaration-keyword");
        }
        if (context.classDef() != null) {
            return buildClass(context.classDef());
        }
        if (context.structureDef() != null) {
            return buildStructure(context.structureDef());
        }
        if (context.associationDef() != null) {
            return buildAssociation(context.associationDef());
        }
        if (context.domainDef() != null) {
            List<DomainDeclarationSyntax> domains = buildDomains(context.domainDef());
            if (domains.size() == 1) {
                return domains.getFirst();
            }
            // Multiple domain declarations are represented by a recovered
            // container; each declaration remains separately addressable.
            List<SyntaxNode> children = new ArrayList<>(domains);
            return new guru.interlis.syntax.RecoveredSyntax(nextId(), range(context), children,
                    "multiple-domain-declarations");
        }
        if (context.viewDef() != null) {
            return buildView(context.viewDef());
        }
        if (context.graphicDef() != null) {
            return buildGraphic(context.graphicDef());
        }
        if (context.constraintsDef() != null) {
            return buildExternalConstraints(context.constraintsDef());
        }
        if (context.functionDef() != null) {
            return buildFunction(context.functionDef());
        }
        if (context.unitDef() != null) {
            if (isDomainEnumeration(context.unitDef())) {
                return buildDomainFromUnit(context.unitDef());
            }
            return buildUnit(context.unitDef());
        }
        if (context.metaDataBasketDef() != null) {
            return buildMetadataBasket(context.metaDataBasketDef());
        }
        if (context.contextDef() != null) {
            return buildContext(context.contextDef());
        }
        return unsupportedDeclaration(context, "DECLARATION", "unknown declaration");
    }

    /** A single enum element and a composed unit share a grammar shape; the section decides. */
    private boolean isDomainEnumeration(InterlisParser.UnitDefContext context) {
        var value = context.composedUnit();
        if (value == null || !value.MUL().isEmpty() || !value.DIV().isEmpty()) return false;
        ParseTree boundary = context;
        ParserRuleContext scope = context.getParent();
        if (scope instanceof InterlisParser.DefinitionsContext) {
            boundary = scope;
            scope = scope.getParent();
        }
        boolean domain = false;
        for (int i = 0; i < scope.getChildCount(); i++) {
            ParseTree child = scope.getChild(i);
            if (child == boundary) break;
            Token start = child instanceof TerminalNode terminal ? terminal.getSymbol()
                    : child instanceof ParserRuleContext rule ? rule.getStart() : null;
            if (start != null && start.getType() == InterlisParser.DOMAIN) domain = true;
            if (start != null && start.getType() == InterlisParser.UNIT) domain = false;
        }
        return context.UNIT() == null && domain;
    }

    private DomainDeclarationSyntax buildDomainFromUnit(InterlisParser.UnitDefContext context) {
        Token name = context.Name(0).getSymbol();
        Optional<NameSyntax> base = context.unitRef() == null ? Optional.empty()
                : Optional.of(nameFromContext(context.unitRef()));
        NameSyntax value = nameFromContext(context.composedUnit());
        EnumerationTypeSyntax type = new EnumerationTypeSyntax(nextId(), range(context.composedUnit()),
                List.of(new EnumerationTypeSyntax.EnumerationValueSyntax(value.text(), value.range(),
                        false, value.segments().size() > 1)), false, false, Set.of());
        return new DomainDeclarationSyntax(nextId(), range(context), name.getText(),
                optionalRange(name, context), directModifiers(context), base, type, List.of());
    }

    private ClassDeclarationSyntax buildClass(InterlisParser.ClassDefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-class>" : nameToken.getText();
        Optional<NameSyntax> base = context.EXTENDS() == null || context.classOrStructureRef() == null
                ? Optional.empty() : Optional.of(nameFromContext(context.classOrStructureRef()));
        List<SyntaxNode> members = buildClassMembers(context.classOrStructureDef());
        return new ClassDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), modifiers(context), base, members,
                oidRules(context), endName(context.END(), context));
    }

    private StructureDeclarationSyntax buildStructure(InterlisParser.StructureDefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-structure>" : nameToken.getText();
        Optional<NameSyntax> base = context.EXTENDS() == null || context.structureRef() == null
                ? Optional.empty() : Optional.of(nameFromContext(context.structureRef()));
        List<SyntaxNode> members = buildClassMembers(context.classOrStructureDef());
        return new StructureDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), modifiers(context), base, members);
    }

    private guru.interlis.syntax.GraphicDeclarationSyntax buildGraphic(InterlisParser.GraphicDefContext context) {
        List<guru.interlis.syntax.GraphicDeclarationSyntax.Rule> rules=new ArrayList<>();
        for(var rule:context.drawingRule()) {
            List<guru.interlis.syntax.GraphicDeclarationSyntax.Clause> clauses=new ArrayList<>();
            for(var clause:rule.condSignParamAssignment()) {
                List<guru.interlis.syntax.GraphicDeclarationSyntax.Assignment> assignments=new ArrayList<>();
                for(var assignment:clause.signParamAssignment()) {
                    guru.interlis.syntax.GraphicDeclarationSyntax.Value value;
                    if(assignment.metaObjectRef()!=null) value=new guru.interlis.syntax.GraphicDeclarationSyntax.MetaObjectValue(nameFromContext(assignment.metaObjectRef()));
                    else if(assignment.factor()!=null) value=new guru.interlis.syntax.GraphicDeclarationSyntax.ExpressionValue(buildFactor(assignment.factor()));
                    else {
                        List<guru.interlis.syntax.GraphicDeclarationSyntax.EnumAssignment> cases=new ArrayList<>();
                        for(var entry:assignment.enumAssignment()) {
                            var range=entry.enumRange();
                            guru.interlis.syntax.GraphicDeclarationSyntax.Value v=entry.metaObjectRef()!=null
                                    ? new guru.interlis.syntax.GraphicDeclarationSyntax.MetaObjectValue(nameFromContext(entry.metaObjectRef()))
                                    : new guru.interlis.syntax.GraphicDeclarationSyntax.ExpressionValue(constantExpression(entry.constant()));
                            cases.add(new guru.interlis.syntax.GraphicDeclarationSyntax.EnumAssignment(range(entry),range.enumerationConst(0).getText().substring(1),range.enumerationConst().size()>1?Optional.of(range.enumerationConst(1).getText().substring(1)):Optional.empty(),v));
                        }
                        value=new guru.interlis.syntax.GraphicDeclarationSyntax.AccordingValue(pathExpression(assignment.attributePath().objectOrAttributePath()),cases);
                    }
                    assignments.add(new guru.interlis.syntax.GraphicDeclarationSyntax.Assignment(range(assignment),nameSyntax(List.of(assignment.Name().getSymbol())),value));
                }
                clauses.add(new guru.interlis.syntax.GraphicDeclarationSyntax.Clause(range(clause),Optional.ofNullable(clause.expression()).map(this::buildExpression),assignments));
            }
            rules.add(new guru.interlis.syntax.GraphicDeclarationSyntax.Rule(nextId(),range(rule),rule.Name().getText(),Optional.of(tokenRange(rule.Name().getSymbol())),modifiers(rule),Optional.ofNullable(rule.classRef()).map(this::nameFromContext),clauses));
        }
        return new guru.interlis.syntax.GraphicDeclarationSyntax(nextId(),range(context),context.Name(0)==null?"<missing-graphic>":context.Name(0).getText(),context.Name(0)==null?Optional.empty():Optional.of(tokenRange(context.Name(0).getSymbol())),modifiers(context),Optional.ofNullable(context.graphicRef()).map(this::nameFromContext),Optional.ofNullable(context.viewableRef()).map(this::nameFromContext),context.selection().stream().map(x->buildExpression(x.expression())).toList(),rules,endName(context.END(),context));
    }

    private List<SyntaxNode> buildClassMembers(InterlisParser.ClassOrStructureDefContext context) {
        if (context == null) {
            return List.of();
        }
        List<Member> members = new ArrayList<>();
        for (InterlisParser.AttributeDefContext attribute : context.attributeDef()) {
            members.add(new Member(attribute.getStart().getTokenIndex(), buildAttribute(attribute)));
        }
        for (InterlisParser.ConstraintDefContext constraint : context.constraintDef()) {
            members.add(new Member(constraint.getStart().getTokenIndex(), buildConstraint(constraint)));
        }
        for (var parameter : context.parameterDef()) {
            var type = parameter.attrTypeDef() != null ? buildAttrTypeDef(parameter.attrTypeDef()).type()
                    : new guru.interlis.syntax.MetaObjectTypeSyntax(nextId(), range(parameter), Optional.ofNullable(parameter.classRef()).map(this::nameFromContext));
            members.add(new Member(parameter.getStart().getTokenIndex(), new guru.interlis.syntax.ParameterDeclarationSyntax(
                    nextId(), range(parameter), parameter.Name().getText(), Optional.of(tokenRange(parameter.Name().getSymbol())),
                    parameter.attrTypeDef() != null && parameter.attrTypeDef().MANDATORY() != null, modifiers(parameter), type)));
        }
        members.sort(Comparator.comparingInt(Member::index));
        return members.stream().map(Member::node).toList();
    }

    private AttributeDeclarationSyntax buildAttribute(InterlisParser.AttributeDefContext context) {
        Token nameToken = context.Name() != null ? context.Name().getSymbol()
                : (context.UUIDOID() != null ? context.UUIDOID().getSymbol() : null);
        String name = nameToken == null ? "<missing-attribute>" : nameToken.getText();
        // The recovery grammar retains both flags; the language only permits
        // CONTINUOUS as part of CONTINUOUS SUBDIVISION.
        if(context.CONTINUOUS()!=null && context.SUBDIVISION()==null && nameToken!=null)
            propertyDiagnostics.add(guru.interlis.diagnostics.DiagnosticDraft.of(
                    new guru.interlis.diagnostics.DiagnosticCode("ILI-SYN-0002"),guru.interlis.diagnostics.Severity.ERROR,
                    guru.interlis.diagnostics.Phase.PARSING,"diagnostic.syntax.unexpected")
                    .at(tokenRange(nameToken)).argument(new guru.interlis.diagnostics.DiagnosticArgument.TokenArgument("actual",name))
                    .argument(new guru.interlis.diagnostics.DiagnosticArgument.IdentifierListArgument("expected",List.of("SUBDIVISION"))));
        TypeResult type;
        if (context.attrTypeDef() != null) {
            type = buildAttrTypeDef(context.attrTypeDef());
        } else if (context.lineType() != null) {
            type = new TypeResult(unsupportedType(context.lineType(), "GEOMETRY_LINE",
                    "geometry line type"), false);
        } else {
            type = new TypeResult(new UnsupportedTypeSyntax(nextId(), range(context),
                    "MISSING_ATTRIBUTE_TYPE", "missing attribute type"), false);
            unsupportedFeatures.add("MISSING_ATTRIBUTE_TYPE");
        }
        boolean transientAttribute = context.TRANSIENT() != null && !context.TRANSIENT().isEmpty();
        Set<Modifier> attributeModifiers = directModifiers(context);
        return new AttributeDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), type.mandatory, transientAttribute,
                context.ASSIGN() != null, context.CONTINUOUS() != null,
                context.SUBDIVISION() != null, attributeModifiers, type.type, context.expression().stream().map(this::buildExpression).toList());
    }

    private List<DomainDeclarationSyntax> buildDomains(InterlisParser.DomainDefContext context) {
        List<DomainDeclarationSyntax> result = new ArrayList<>();
        List<Token> semis = context.SEMI().stream().map(TerminalNode::getSymbol).toList();
        int segmentStart = context.getStart().getTokenIndex();
        for (Token semi : semis) {
            int segmentEnd = semi.getTokenIndex();
            DomainDeclarationSyntax declaration = buildDomainSegment(context, segmentStart, segmentEnd);
            if (declaration != null) {
                result.add(declaration);
            }
            segmentStart = segmentEnd + 1;
        }
        return result;
    }

    private DomainDeclarationSyntax buildDomainSegment(InterlisParser.DomainDefContext context,
                                                       int startIndex, int endIndex) {
        Token nameToken = null;
        int eqIndex = -1;
        for (int i = startIndex; i <= endIndex && i < tokenStream.size(); i++) {
            Token token = tokenStream.get(i);
            if (token.getType() == InterlisParser.SEMI) {
                break;
            }
            if (nameToken == null && (token.getType() == InterlisParser.Name
                    || token.getType() == InterlisParser.UUIDOID)) {
                nameToken = token;
            }
            if (token.getType() == InterlisParser.EQ && eqIndex < 0) {
                eqIndex = i;
            }
        }
        if (nameToken == null || eqIndex < 0) {
            return null;
        }
        String name = nameToken.getText();
        Optional<NameSyntax> base = Optional.empty();
        for (InterlisParser.DomainRefContext candidate : context.domainRef()) {
            int index = candidate.getStart().getTokenIndex();
            if (index >= startIndex && index < eqIndex) {
                base = Optional.of(nameFromContext(candidate));
                break;
            }
        }
        TypeSyntax type = null;
        for (InterlisParser.TypeContext candidate : context.type()) {
            int index = candidate.getStart().getTokenIndex();
            if (index > eqIndex && index <= endIndex) {
                type = buildType(candidate);
                break;
            }
        }
        if (type == null) {
            for (InterlisParser.NumericContext candidate : context.numeric()) {
                int index = candidate.getStart().getTokenIndex();
                if (index > eqIndex && index <= endIndex) {
                    type = numericType(candidate);
                    break;
                }
            }
        }
        if (type == null) {
            for (InterlisParser.EnumerationContext candidate : context.enumeration()) {
                int index = candidate.getStart().getTokenIndex();
                if (index > eqIndex && index <= endIndex) {
                    type = enumerationType(candidate);
                    break;
                }
            }
        }
        if (type == null) {
            List<Token> bounds = new ArrayList<>();
            for (int i = eqIndex + 1; i <= endIndex; i++)
                if (tokenStream.get(i).getType() == InterlisParser.STRING) bounds.add(tokenStream.get(i));
            if (bounds.size() == 2) type = new FormattedTypeSyntax(nextId(), range(context), Optional.empty(),
                    false, false, List.of(), Optional.of(unquote(bounds.get(0).getText())), Optional.of(unquote(bounds.get(1).getText())));
        }
        if (type == null) {
            for (int i = eqIndex + 1; i <= endIndex; i++) {
                int tokenType = tokenStream.get(i).getType();
                if (tokenType == InterlisParser.CLASS) {
                    int typeStart = eqIndex;
                    var restrictions = context.classOrAssociationRef().stream()
                            .filter(r -> r.getStart().getTokenIndex() > typeStart && r.getStop().getTokenIndex() <= endIndex)
                            .map(this::nameFromContext).toList();
                    type = new ClassValueTypeSyntax(nextId(), tokenRange(tokenStream.get(i)), false, restrictions);
                    break;
                }
                if (tokenType == InterlisParser.STRUCTURE) {
                    type = new GenericTypeSyntax(nextId(), tokenRange(tokenStream.get(i)), "STRUCTURE");
                    break;
                }
            }
        }
        if (type == null) {
            boolean mandatoryOnly = false;
            for (int i = eqIndex + 1; i <= endIndex; i++) {
                if (tokenStream.get(i).getType() == InterlisParser.MANDATORY) {
                    mandatoryOnly = true;
                }
            }
            if (mandatoryOnly) {
                type = new GenericTypeSyntax(nextId(), tokenRange(tokenStream.get(eqIndex + 1)), "INHERITED");
            } else {
                type = new UnsupportedTypeSyntax(nextId(), range(context),
                        "UNSUPPORTED_DOMAIN_TYPE", "unsupported domain type");
                unsupportedFeatures.add("UNSUPPORTED_DOMAIN_TYPE");
            }
        }
        List<ConstraintDeclarationSyntax> constraints = new ArrayList<>();
        for (int i = 2; i < context.getChildCount(); i++) {
            if (context.getChild(i) instanceof InterlisParser.ExpressionContext condition
                    && condition.getStart().getTokenIndex() > eqIndex
                    && condition.getStop().getTokenIndex() < endIndex) {
                Token constraintName = ((TerminalNode) context.getChild(i - 2)).getSymbol();
                SourceRange constraintRange = tokenRange(constraintName, condition.getStop());
                constraints.add(new ConstraintDeclarationSyntax(nextId(), constraintRange,
                        Optional.of(constraintName.getText()), ConstraintKind.DOMAIN,
                        Optional.of(buildExpression(condition)), List.of(), Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty()));
                if (!LanguageProfile.supports(language, LanguageProfile.Feature.DOMAIN_CONSTRAINT)) {
                    versionViolations.add(LanguageProfile.Feature.DOMAIN_CONSTRAINT);
                    versionViolationRanges.add(constraintRange);
                }
            }
        }
        SourceRange declarationRange = tokenRange(tokenStream.get(startIndex),
                tokenStream.get(endIndex));
        return new DomainDeclarationSyntax(nextId(), declarationRange, name,
                Optional.of(tokenRange(nameToken)), domainModifiers(startIndex, eqIndex, endIndex), base, type, constraints);
    }

    private Set<Modifier> domainModifiers(int start, int end, int declarationEnd) {
        Set<Modifier> result = new LinkedHashSet<>();
        for (int i = start; i < end; i++) switch (tokenStream.get(i).getType()) {
            case InterlisParser.ABSTRACT -> addModifier(result, Modifier.ABSTRACT, tokenStream.get(i));
            case InterlisParser.FINAL -> addModifier(result, Modifier.FINAL, tokenStream.get(i));
            case InterlisParser.GENERIC -> addModifier(result, Modifier.GENERIC, tokenStream.get(i));
            default -> { }
        }
        for (int i = end + 1; i <= declarationEnd; i++) {
            Token token = tokenStream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) continue;
            if (token.getType() == InterlisParser.MANDATORY) result.add(Modifier.MANDATORY);
            break;
        }
        return result;
    }

    private AssociationDeclarationSyntax buildAssociation(
            InterlisParser.AssociationDefContext context) {
        Token nameToken = associationNameToken(context);
        String name = nameToken == null ? "<anonymous-association>" : nameToken.getText();
        Optional<SourceRange> nameRange = nameToken == null ? Optional.empty()
                : Optional.of(tokenRange(nameToken));
        List<RoleDeclarationSyntax> roles = new ArrayList<>();
        List<AttributeDeclarationSyntax> attributes = new ArrayList<>();
        for (InterlisParser.RoleDefContext role : context.roleDef()) {
            if (role.COLON() != null) {
                attributes.add(buildAssociationAttribute(role));
            } else {
                roles.add(buildRole(role));
            }
        }
        for (InterlisParser.AttributeDefContext attribute : context.attributeDef()) {
            attributes.add(buildAttribute(attribute));
        }
        List<ConstraintDeclarationSyntax> constraints = new ArrayList<>();
        for (InterlisParser.ConstraintDefContext constraint : context.constraintDef()) {
            constraints.add(buildConstraint(constraint));
        }
        Optional<String> endName = Optional.empty();
        if (context.END() != null) {
            int endIndex = context.END().getSymbol().getTokenIndex();
            for (int i = endIndex + 1; i <= context.getStop().getTokenIndex(); i++) {
                Token token = tokenStream.get(i);
                if (token.getType() == InterlisParser.Name) {
                    endName = Optional.of(token.getText());
                    break;
                }
            }
        }
        Optional<NameSyntax> baseAssociation = context.associationRef() == null ? Optional.empty()
                : Optional.of(nameFromContext(context.associationRef()));
        Set<Modifier> associationModifiers = modifiers(context);
        if (context.objectOidRule() != null && context.objectOidRule().NO() != null) associationModifiers.remove(Modifier.OID);
        return new AssociationDeclarationSyntax(nextId(), range(context), name, nameRange,
                endName, baseAssociation, associationModifiers, roles, attributes, constraints,
                oidRules(context), context.cardinality() == null ? Optional.empty()
                        : Optional.of(cardinality(context.cardinality())), context.renamedViewableRef() == null
                        ? Optional.empty() : Optional.of(viewBase(context.renamedViewableRef(), false)));
    }

    private AttributeDeclarationSyntax buildAssociationAttribute(
            InterlisParser.RoleDefContext context) {
        Token nameToken = context.Name() == null ? null : context.Name().getSymbol();
        String name = nameToken == null ? "<missing-attribute>" : nameToken.getText();
        TypeResult type;
        if (context.attrTypeDef() != null) {
            type = buildAttrTypeDef(context.attrTypeDef());
        } else if (context.enumeration() != null) {
            type = new TypeResult(enumerationType(context.enumeration()), false);
        } else if (context.numeric() != null) {
            type = new TypeResult(numericType(context.numeric()), false);
        } else {
            type = new TypeResult(new UnsupportedTypeSyntax(nextId(), range(context),
                    "MISSING_ATTRIBUTE_TYPE", "missing association attribute type"), false);
            unsupportedFeatures.add("MISSING_ATTRIBUTE_TYPE");
        }
        return new AttributeDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), context.MANDATORY() != null || type.mandatory,
                false, context.ASSIGN() != null, false, false, directModifiers(context), type.type, List.of());
    }

    private Token associationNameToken(InterlisParser.AssociationDefContext context) {
        if (context.Name().isEmpty()) {
            return null;
        }
        Token candidate = context.Name(0).getSymbol();
        int firstRole = context.roleDef().isEmpty()
                ? Integer.MAX_VALUE : context.roleDef(0).getStart().getTokenIndex();
        return candidate.getTokenIndex() < firstRole ? candidate : null;
    }

    private RoleDeclarationSyntax buildRole(InterlisParser.RoleDefContext context) {
        Token nameToken = context.Name() == null ? null : context.Name().getSymbol();
        String name = nameToken == null ? "<missing-role>" : nameToken.getText();
        RoleAggregation aggregation = context.HASH() != null ? RoleAggregation.COMPOSITION
                : context.LT() != null ? RoleAggregation.AGGREGATION : RoleAggregation.NONE;
        if (context.COLON() != null) {
            unsupportedFeatures.add("ATTRIBUTE_STYLE_ROLE");
            NameSyntax target = syntheticName("UNSUPPORTED_ROLE", range(context));
            return new RoleDeclarationSyntax(nextId(), range(context), name,
                    optionalRange(nameToken, context), Cardinality.unboundedFrom(0), List.of(new RoleDeclarationSyntax.Target(target, List.of())),
                    modifiers(context), aggregation, false, Optional.empty());
        }
        boolean explicitCardinality = context.cardinality() != null;
        Cardinality cardinality;
        if (explicitCardinality) {
            cardinality = cardinality(context.cardinality());
        } else if (aggregation == RoleAggregation.COMPOSITION) {
            cardinality = language == LanguageVersion.ILI23
                    ? new Cardinality(BigInteger.ZERO, CardinalityUpper.finite(1))
                    : new Cardinality(BigInteger.ONE, CardinalityUpper.finite(1));
        } else {
            cardinality = Cardinality.unboundedFrom(0);
        }
        List<RoleDeclarationSyntax.Target> targets = new ArrayList<>();
        for (var target : context.restrictedClassOrAssRef()) {
            var names = target.classOrAssociationRef();
            boolean any = target.ANYCLASS() != null;
            NameSyntax base = any
                    ? syntheticName("ANYCLASS", tokenRange(target.ANYCLASS().getSymbol()))
                    : nameFromContext(names.getFirst());
            targets.add(new RoleDeclarationSyntax.Target(base, names.stream().skip(any ? 0 : 1)
                    .map(this::nameFromContext).toList()));
        }
        if (targets.isEmpty()) {
            targets.add(new RoleDeclarationSyntax.Target(syntheticName("UNRESOLVED_ROLE_TARGET", range(context)), List.of()));
            unsupportedFeatures.add("MISSING_ROLE_TARGET");
        }
        return new RoleDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), cardinality, targets, modifiers(context),
                aggregation, explicitCardinality, context.factor() == null ? Optional.empty() : Optional.of(buildFactor(context.factor())));
    }

    private ConstraintDeclarationSyntax buildConstraint(InterlisParser.ConstraintDefContext context) {
        if (context.mandatoryConstraint() != null) {
            InterlisParser.MandatoryConstraintContext mandatory = context.mandatoryConstraint();
            if(language==LanguageVersion.ILI23 && (mandatory.Name()!=null)) {
                versionViolations.add(LanguageProfile.Feature.NAMED_OR_BASKET_CONSTRAINT);versionViolationRanges.add(range(mandatory));
            }
            Optional<String> name = mandatory.Name() == null ? Optional.empty()
                    : Optional.of(mandatory.Name().getText());
            Optional<ExpressionSyntax> expression = mandatory.expression() == null ? Optional.empty()
                    : Optional.of(buildExpression(mandatory.expression()));
            return new ConstraintDeclarationSyntax(nextId(), range(context), name,
                    ConstraintKind.MANDATORY, expression, List.of(), Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty());
        }
        if (context.uniquenessConstraint() != null) {
            InterlisParser.UniquenessConstraintContext unique = context.uniquenessConstraint();
            Optional<String> name = unique.Name() == null ? Optional.empty()
                    : Optional.of(unique.Name().getText());
            Optional<ExpressionSyntax> where = unique.expression() == null ? Optional.empty()
                    : Optional.of(buildExpression(unique.expression()));
            List<ExpressionSyntax> uniqueKeys = new ArrayList<>();
            Optional<PathExpressionSyntax> prefix = Optional.empty();
            if (unique.globalUniqueness() != null) {
                for (var key : unique.globalUniqueness().uniqueEl()) uniqueKeys.add(pathExpression(key.objectOrAttributePath()));
            } else if (unique.localUniqueness() != null) {
                var local = unique.localUniqueness();
                prefix = Optional.of(tokenPath(local.prefix));
                for (Token field : local.fields) uniqueKeys.add(tokenPath(List.of(field)));
            }
            if (language == LanguageVersion.ILI23 && (unique.Name() != null || unique.BASKET() != null)) {
                versionViolations.add(LanguageProfile.Feature.NAMED_OR_BASKET_CONSTRAINT);
                versionViolationRanges.add(range(unique));
            }
            return new ConstraintDeclarationSyntax(nextId(), range(context), name,
                    ConstraintKind.UNIQUENESS, where, uniqueKeys, Optional.empty(), prefix, unique.BASKET() != null, Optional.empty(), Optional.empty());
        }

        if (context.plausibilityConstraint() != null) {
            InterlisParser.PlausibilityConstraintContext plausibility = context.plausibilityConstraint();
            if(language==LanguageVersion.ILI23 && (plausibility.Name()!=null)) {
                versionViolations.add(LanguageProfile.Feature.NAMED_OR_BASKET_CONSTRAINT);versionViolationRanges.add(range(plausibility));
            }
            Optional<String> name = plausibility.Name() == null ? Optional.empty()
                    : Optional.of(plausibility.Name().getText());
            Optional<ExpressionSyntax> expression = plausibility.expression() == null
                    ? Optional.empty() : Optional.of(buildExpression(plausibility.expression()));
            return new ConstraintDeclarationSyntax(nextId(), range(context), name,
                    ConstraintKind.PLAUSIBILITY, expression, List.of(), Optional.empty(), Optional.empty(), false,
                    plausibility.percentage==null?Optional.empty():decimal(plausibility.percentage.getText(),tokenRange(plausibility.percentage)).map(value->new ConstraintDeclarationSyntax.Plausibility(value,
                            plausibility.GTEQ()!=null?guru.interlis.model.BinaryOperator.GTE:guru.interlis.model.BinaryOperator.LTE,
                            tokenRange(plausibility.percentage))), Optional.empty());
        }
        if (context.existenceConstraint() != null) {
            InterlisParser.ExistenceConstraintContext existence = context.existenceConstraint();
            ExpressionSyntax source = pathExpression(existence.attributePath(0).objectOrAttributePath());
            List<ExistenceConstraintSyntax.ExistenceTargetSyntax> targets = new ArrayList<>();
            for (int i = 0; i < existence.viewableRef().size(); i++) {
                NameSyntax viewable = nameFromContext(existence.viewableRef(i));
                ExpressionSyntax path = pathExpression(existence.attributePath(i + 1).objectOrAttributePath());
                targets.add(new ExistenceConstraintSyntax.ExistenceTargetSyntax(viewable, path));
            }
            if (language == LanguageVersion.ILI23 && existence.Name() != null) {
                versionViolations.add(LanguageProfile.Feature.NAMED_OR_BASKET_CONSTRAINT);
                versionViolationRanges.add(range(existence));
            }
            return new ConstraintDeclarationSyntax(nextId(), range(context),
                    Optional.ofNullable(existence.Name()).map(TerminalNode::getText), ConstraintKind.EXISTENCE, Optional.empty(), List.of(),
                    Optional.of(new ExistenceConstraintSyntax(nextId(), range(context), source, targets)),
                    Optional.empty(), false, Optional.empty(), Optional.empty());
        }
        if (context.setConstraint() != null) {
            InterlisParser.SetConstraintContext set = context.setConstraint();
            if(language==LanguageVersion.ILI23 && (set.Name()!=null || set.BASKET()!=null)) {
                versionViolations.add(LanguageProfile.Feature.NAMED_OR_BASKET_CONSTRAINT);versionViolationRanges.add(range(set));
            }
            Optional<String> name = set.Name() == null ? Optional.empty()
                    : Optional.of(set.Name().getText());
            Optional<ExpressionSyntax> condition = set.expression().isEmpty() ? Optional.empty()
                    : Optional.of(buildExpression(set.expression(set.expression().size() - 1)));
            return new ConstraintDeclarationSyntax(nextId(), range(context), name,
                    ConstraintKind.SET, condition, List.of(), Optional.empty(), Optional.empty(), set.BASKET()!=null, Optional.empty(),
                    set.WHERE()!=null && set.expression().size()>1?Optional.of(buildExpression(set.expression(0))):Optional.empty());
        }
        unsupportedFeatures.add("CONSTRAINT_UNKNOWN");
        return new ConstraintDeclarationSyntax(nextId(), range(context), Optional.empty(),
                ConstraintKind.MANDATORY, Optional.empty(), List.of(), Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty());
    }

    private TypeResult buildAttrTypeDef(InterlisParser.AttrTypeDefContext context) {
        boolean mandatory = context.MANDATORY() != null;
        if (context.attrType() != null) {
            return new TypeResult(buildAttrType(context.attrType()), mandatory);
        }
        if (context.enumeration() != null) {
            return new TypeResult(enumerationType(context.enumeration()), mandatory);
        }
        if (context.numeric() != null) {
            return new TypeResult(numericType(context.numeric()), mandatory);
        }
        if (context.NUMERIC() != null) {
            return new TypeResult(numericTypeUnbounded(context), mandatory);
        }
        if (context.BAG() != null || context.LIST() != null) {
            return new TypeResult(collectionType(context), mandatory);
        }
        if (mandatory) {
            return new TypeResult(new GenericTypeSyntax(nextId(), range(context), "INHERITED"), true);
        }
        UnsupportedTypeSyntax unsupported = new UnsupportedTypeSyntax(nextId(), range(context),
                "MISSING_ATTRIBUTE_TYPE", "missing attribute type");
        unsupportedFeatures.add("MISSING_ATTRIBUTE_TYPE");
        return new TypeResult(unsupported, mandatory);
    }

    private ReferenceTypeSyntax referenceType(InterlisParser.ReferenceAttrContext context) {
        var restricted = context.restrictedClassOrAssRef();
        var names = restricted.classOrAssociationRef();
        boolean any = restricted.ANYCLASS() != null;
        NameSyntax target = any ? syntheticName("ANYCLASS", tokenRange(restricted.ANYCLASS().getSymbol()))
                : nameFromContext(names.getFirst());
        return new ReferenceTypeSyntax(nextId(), range(context), target,
                names.stream().skip(any ? 0 : 1).map(this::nameFromContext).toList(), context.EXTERNAL() != null);
    }

    private TypeSyntax buildAttrType(InterlisParser.AttrTypeContext context) {
        if (context.type() != null) {
            return buildType(context.type());
        }
        if (context.domainRef() != null) {
            return new DomainReferenceSyntax(nextId(), range(context),
                    nameFromContext(context.domainRef()));
        }
        if (context.referenceAttr() != null) {
            return referenceType(context.referenceAttr());
        }
        if (context.restrictedStructureRef() != null) {
            InterlisParser.RestrictedStructureRefContext restricted = context.restrictedStructureRef();
            // CLASS/STRUCTURE type restrictions also contain structureRef children.
            // Preserve the enclosing type instead of mistaking its first restriction for the type.
            if (restricted.type() != null) {
                var type = buildType(restricted.type());
                if (type instanceof ClassValueTypeSyntax value && !restricted.structureRef().isEmpty()) {
                    var restrictions = new ArrayList<>(value.restrictions());
                    restricted.structureRef().stream().map(this::nameFromContext).forEach(restrictions::add);
                    return new ClassValueTypeSyntax(value.id(), range(restricted), value.structureValue(), restrictions);
                }
                return type;
            }
            if (restricted.ANYSTRUCTURE() != null || !restricted.structureRef().isEmpty()) return structureValueType(restricted);
            if (restricted.numeric() != null) return numericType(restricted.numeric());
            if (restricted.enumeration() != null) return enumerationType(restricted.enumeration());
            if (restricted.referenceAttr() != null) {
                return referenceType(restricted.referenceAttr());
            }

        }
        UnsupportedTypeSyntax unsupported = new UnsupportedTypeSyntax(nextId(), range(context),
                "UNSUPPORTED_ATTRIBUTE_TYPE", "unsupported attribute type");
        unsupportedFeatures.add("UNSUPPORTED_ATTRIBUTE_TYPE");
        return unsupported;
    }

    private TypeSyntax buildType(InterlisParser.TypeContext context) {
        if (context.baseType() != null) {
            return buildBaseType(context.baseType());
        }
        if (context.lineType() != null) {
            return lineType(context.lineType());
        }
        if (context.STRING() != null && context.STRING().size() >= 2) {
            return new FormattedTypeSyntax(nextId(), range(context), Optional.empty(), false, false, List.of(),
                    Optional.of(unquote(context.STRING(0).getText())), Optional.of(unquote(context.STRING(1).getText())));
        }
        unsupportedFeatures.add("UNSUPPORTED_TYPE");
        return unsupportedType(context, "UNSUPPORTED_TYPE", "unsupported type");
    }

    private TextTypeSyntax textType(InterlisParser.TextTypeContext text) {
        TextKind kind = text.MTEXT() != null ? TextKind.MTEXT : text.TEXT() != null ? TextKind.TEXT
                : text.NAME() != null ? TextKind.NAME : TextKind.URI;
        OptionalInt length = text.PosNumber() == null ? OptionalInt.empty()
                : OptionalInt.of(Integer.parseInt(text.PosNumber().getText()));
        return new TextTypeSyntax(nextId(), range(text), kind, length);
    }

    private TypeSyntax buildBaseType(InterlisParser.BaseTypeContext context) {
        if (context.textType() != null) {
            return textType(context.textType());
        }
        if (context.booleanType() != null) {
            return new BooleanTypeSyntax(nextId(), range(context));
        }
        if (context.numericType() != null) {
            InterlisParser.NumericTypeContext numericType = context.numericType();
            if (numericType.numeric() != null) {
                return numericType(numericType.numeric());
            }
            return numericTypeUnbounded(context.numericType());
        }
        if (context.coordinateType() != null) {
            return coordinateType(context.coordinateType());
        }
        if (context.oIDType() != null) {
            InterlisParser.OIDTypeContext oid = context.oIDType();
            if (oid.UUIDOID() != null) {
                return new DomainReferenceSyntax(nextId(), range(oid), nameFromContext(oid));
            }
            guru.interlis.model.OidKind kind = oid.ANY() != null ? guru.interlis.model.OidKind.ANY
                    : oid.NUMERIC() != null || oid.numeric() != null
                    ? guru.interlis.model.OidKind.NUMERIC
                    : guru.interlis.model.OidKind.TEXT;
            Optional<TypeSyntax> carrier = oid.numeric() != null ? Optional.of(numericType(oid.numeric()))
                    : oid.textType() != null ? Optional.of(textType(oid.textType()))
                    : oid.NUMERIC() != null ? Optional.of(new NumericTypeSyntax(nextId(), range(oid),
                            Optional.empty(), Optional.empty(), false, guru.interlis.model.NumericDirection.NONE, Optional.empty(), Optional.empty()))
                    : Optional.empty();
            return new OidTypeSyntax(nextId(), range(oid), kind, carrier);
        }
        if (context.blackboxType() != null) {
            InterlisParser.BlackboxTypeContext blackbox = context.blackboxType();
            return new BlackboxTypeSyntax(nextId(), range(blackbox),
                    blackbox.BINARY() != null ? guru.interlis.model.BlackboxKind.BINARY
                    : guru.interlis.model.BlackboxKind.XML);
        }
        if (context.alignmentType() != null) {
            InterlisParser.AlignmentTypeContext alignment = context.alignmentType();
            return new AlignmentTypeSyntax(nextId(), range(alignment),
                    alignment.HALIGNMENT() != null ? guru.interlis.model.AlignmentKind.HORIZONTAL
                    : guru.interlis.model.AlignmentKind.VERTICAL);
        }
        if (context.classType() != null) {
            InterlisParser.ClassTypeContext classType = context.classType();
            List<NameSyntax> restrictions = new ArrayList<>();
            for (InterlisParser.ViewableRefContext restriction : classType.viewableRef()) {
                restrictions.add(nameFromContext(restriction));
            }
            for (InterlisParser.ClassOrStructureRefContext restriction :
                    classType.classOrStructureRef()) {
                restrictions.add(nameFromContext(restriction));
            }
            return new ClassValueTypeSyntax(nextId(), range(classType),
                    classType.STRUCTURE() != null, restrictions);
        }
        if (context.formattedType() != null) {
            InterlisParser.FormattedTypeContext formatted = context.formattedType();
            NameSyntax base = formatted.structureRef() != null ? nameFromContext(formatted.structureRef())
                    : formatted.domainRef() != null ? nameFromContext(formatted.domainRef())
                    : nameSyntax(List.of(formatted.INTERLIS().getSymbol(), formatted.Name().getSymbol()));
            List<FormattedTypeSyntax.Part> parts = new ArrayList<>();
            if (formatted.formatDef() != null) for (ParseTree child : formatted.formatDef().children) {
                if (child instanceof TerminalNode literal && literal.getSymbol().getType() == InterlisParser.STRING)
                    parts.add(new FormattedTypeSyntax.Part(Optional.empty(), Optional.empty(), Optional.empty(),
                            unquote(literal.getText()), tokenRange(literal.getSymbol())));
                else if (child instanceof InterlisParser.BaseAttrRefContext attribute)
                    parts.add(new FormattedTypeSyntax.Part(Optional.of(nameSyntax(List.of(attribute.Name().getSymbol()))),
                            attribute.PosNumber() == null ? Optional.empty() : Optional.of(new BigInteger(attribute.PosNumber().getText())),
                            attribute.domainRef() == null ? Optional.empty() : Optional.of(nameFromContext(attribute.domainRef())),
                            "", range(attribute)));
            }
            return new FormattedTypeSyntax(nextId(), range(formatted), Optional.of(base), formatted.structureRef() != null,
                    formatted.formatDef() != null && formatted.formatDef().INHERITANCE() != null, parts,
                    formatted.STRING().isEmpty() ? Optional.empty() : Optional.of(unquote(formatted.STRING(0).getText())),
                    formatted.STRING().size() < 2 ? Optional.empty() : Optional.of(unquote(formatted.STRING(1).getText())));
        }

        if (context.dateTimeType() != null) {
            InterlisParser.DateTimeTypeContext temporal = context.dateTimeType();
            guru.interlis.model.TemporalKind kind = temporal.DATE() != null
                    ? guru.interlis.model.TemporalKind.DATE
                    : temporal.TIMEOFDAY() != null ? guru.interlis.model.TemporalKind.TIMEOFDAY
                    : guru.interlis.model.TemporalKind.DATETIME;
            return new TemporalTypeSyntax(nextId(), range(temporal), kind);
        }
        if (context.attributeType() != null) {
            var attribute = context.attributeType();
            return new guru.interlis.syntax.AttributePathTypeSyntax(nextId(), range(attribute),
                    attribute.attributePath() == null ? Optional.empty() : Optional.of(pathExpression(attribute.attributePath().objectOrAttributePath())),
                    attribute.Name() == null ? Optional.empty() : Optional.of(nameFromToken(attribute.Name().getSymbol())),
                    attribute.attrTypeDef().stream().map(t -> buildAttrTypeDef(t).type()).toList());
        }
        if (context.enumTreeValueType() != null) {
            return new EnumTreeValueSyntax(nextId(), range(context),
                    nameFromContext(context.enumTreeValueType().domainRef()));
        }
        String feature = context.alignmentType() != null ? "ALIGNMENT"
                : context.classType() != null ? "CLASS_VALUE"
                : context.attributeType() != null ? "ATTRIBUTE_TYPE"
                : "UNSUPPORTED_BASE_TYPE";
        unsupportedFeatures.add(feature);
        return unsupportedType(context, feature, "unsupported base type");
    }

    private LineTypeSyntax lineType(InterlisParser.LineTypeContext context) {
        guru.interlis.model.GeometryKind kind = context.POLYLINE() != null
                ? guru.interlis.model.GeometryKind.POLYLINE
                : context.SURFACE() != null ? guru.interlis.model.GeometryKind.SURFACE
                : context.AREA() != null ? guru.interlis.model.GeometryKind.AREA
                : context.MULTIPOLYLINE() != null ? guru.interlis.model.GeometryKind.MULTIPOLYLINE
                : context.MULTISURFACE() != null ? guru.interlis.model.GeometryKind.MULTISURFACE
                : guru.interlis.model.GeometryKind.MULTIAREA;
        List<NameSyntax> lineForms = new ArrayList<>();
        if (context.lineForm() != null) for (var form : context.lineForm().lineFormType()) {
            List<Token> names = form.Name().stream().map(TerminalNode::getSymbol).toList();
            lineForms.add(nameSyntax(names.isEmpty() ? List.of(form.getStart()) : names));
        }
        Optional<NameSyntax> vertexDomain = Optional.empty();
        if (context.controlPoints() != null && !context.controlPoints().Name().isEmpty()) {
            List<Token> names = context.controlPoints().Name().stream()
                    .map(TerminalNode::getSymbol).toList();
            vertexDomain = Optional.of(nameSyntax(names));
        }
        Optional<NameSyntax> lineAttributes = context.LINE() != null && context.Name() != null
                ? Optional.of(nameSyntax(List.of(context.Name().getSymbol()))) : Optional.empty();
        Optional<String> overlap = Optional.empty();
        if (context.intersectionDef() != null) {
            if (context.intersectionDef().Dec() != null) {
                overlap = Optional.of(context.intersectionDef().Dec().getText());
            } else if (context.intersectionDef().Number() != null) {
                overlap = Optional.of(context.intersectionDef().Number().getText());
            } else if (context.intersectionDef().PosNumber() != null) {
                overlap = Optional.of(context.intersectionDef().PosNumber().getText());
            }
        }
        return new LineTypeSyntax(nextId(), range(context), kind, lineForms, vertexDomain,
                lineAttributes, overlap, context.DIRECTED() != null, context.intersectionDef() != null);
    }

    private CoordinateTypeSyntax coordinateType(InterlisParser.CoordinateTypeContext context) {
        boolean multi = context.MULTICOORD() != null;
        List<NumericTypeSyntax> axes = new ArrayList<>();
        for (ParseTree child : context.children) {
            if (child instanceof InterlisParser.NumericContext numeric) axes.add(numericType(numeric));
            else if (child instanceof InterlisParser.NumericTypeContext numeric)
                axes.add(numeric.numeric() == null ? numericTypeUnbounded(numeric) : numericType(numeric.numeric()));
        }
        if (axes.isEmpty()) {
            unsupportedFeatures.add("COORDINATE_WITHOUT_AXES");
            axes.add(numericTypeUnbounded(context));
        }
        if (axes.size() > 3) {
            unsupportedFeatures.add("COORDINATE_TOO_MANY_AXES");
        }
        List<NumericTypeSyntax> limited = axes.size() > 3 ? axes.subList(0, 3) : axes;
        Optional<String> referenceSystem = context.REFSYS() != null && context.STRING() != null
                ? Optional.of(unquote(context.STRING().getText())) : Optional.empty();
        return new CoordinateTypeSyntax(nextId(), range(context), limited.size(), limited, multi,
                referenceSystem, Optional.ofNullable(context.rotationDef()).map(rotation -> new guru.interlis.model.CoordinateType.Rotation(
                        Integer.parseInt(rotation.PosNumber(0).getText()), Integer.parseInt(rotation.PosNumber(1).getText()))));
    }

    private NumericTypeSyntax numericType(InterlisParser.NumericContext context) {
        List<Token> numbers = numericTokens(context);
        Optional<DecimalLiteral> minimum = numbers.isEmpty() ? Optional.empty() : numericBoundary(numbers.getFirst());
        Optional<DecimalLiteral> maximum = numbers.size() < 2 ? Optional.empty() : numericBoundary(numbers.get(1));
        Optional<NameSyntax> unit = context.unitRef() == null ? Optional.empty()
                : Optional.of(nameFromContext(context.unitRef()));
        return new NumericTypeSyntax(nextId(), range(context), minimum, maximum,
                context.CIRCULAR() != null, context.CLOCKWISE() != null ? NumericDirection.CLOCKWISE
                        : context.COUNTERCLOCKWISE() != null ? NumericDirection.COUNTERCLOCKWISE : NumericDirection.NONE,
                unit, referenceSystem(context.refSys()));
    }

    /** Scaled type boundaries use Float's mantissa production. Ordinary constants
     * and unit factors use decimal(), whose independent lexical profile is retained. */
    private Optional<DecimalLiteral> numericBoundary(Token token) {
        String text=token.getText();
        var value=decimal(text,tokenRange(token));
        if(value.isEmpty())return value;
        int exponent=Math.max(text.indexOf('e'),text.indexOf('E'));
        if(exponent>=0) {
            int start=text.startsWith("+") || text.startsWith("-")?1:0;
            boolean canonical=exponent>start+2 && text.charAt(start)=='0' && text.charAt(start+1)=='.';
            // Float explicitly permits a zero mantissa of any declared precision.
            if(canonical && text.charAt(start+2)=='0') {
                for(int i=start+2;i<exponent;i++)if(text.charAt(i)!='0') {canonical=false;break;}
            }
            if(!canonical)propertyDiagnostics.add(guru.interlis.diagnostics.DiagnosticDraft.of(
                    new guru.interlis.diagnostics.DiagnosticCode("ILI-SYN-0002"),guru.interlis.diagnostics.Severity.ERROR,
                    guru.interlis.diagnostics.Phase.PARSING,"diagnostic.syntax.unexpected")
                    .at(tokenRange(token)).argument(new guru.interlis.diagnostics.DiagnosticArgument.TokenArgument("actual",text))
                    .argument(new guru.interlis.diagnostics.DiagnosticArgument.IdentifierListArgument("expected",
                            List.of("scaled boundary with 0.nonzero-digits or 0.zeros mantissa"))));
        }
        return value;
    }

    private NumericTypeSyntax numericTypeUnbounded(ParserRuleContext context) {
        var numeric = context instanceof InterlisParser.NumericTypeContext n ? n : null;
        return new NumericTypeSyntax(nextId(), range(context), Optional.empty(), Optional.empty(),
                numeric != null && numeric.CIRCULAR() != null,
                numeric != null && numeric.CLOCKWISE() != null ? NumericDirection.CLOCKWISE
                        : numeric != null && numeric.COUNTERCLOCKWISE() != null ? NumericDirection.COUNTERCLOCKWISE : NumericDirection.NONE,
                numeric == null ? Optional.empty() : Optional.ofNullable(numeric.unitRef()).map(this::nameFromContext),
                numeric == null ? Optional.empty() : referenceSystem(numeric.refSys()));
    }

    private Optional<NumericTypeSyntax.ReferenceSystem> referenceSystem(InterlisParser.RefSysContext context) {
        if (context == null) return Optional.empty();
        return Optional.of(new NumericTypeSyntax.ReferenceSystem(nameFromContext(
                context.domainRef() != null ? context.domainRef() : context.metaObjectRef()), context.domainRef() != null,
                context.PosNumber() == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(Integer.parseInt(context.PosNumber().getText()))));
    }

    private List<Token> numericTokens(ParserRuleContext context) {
        List<Token> tokens = new ArrayList<>();
        for (ParseTree child : context.children) {
            if (child instanceof TerminalNode terminal) {
                int type = terminal.getSymbol().getType();
                if (type == InterlisParser.Number || type == InterlisParser.PosNumber
                        || type == InterlisParser.Dec || type == InterlisParser.Float) {
                    tokens.add(terminal.getSymbol());
                }
            }
        }
        tokens.sort(Comparator.comparingInt(Token::getTokenIndex));
        return tokens;
    }

    private EnumerationTypeSyntax enumerationType(InterlisParser.EnumerationContext context) {
        List<EnumerationTypeSyntax.EnumerationValueSyntax> values = new ArrayList<>();
        for (InterlisParser.EnumElementContext element : context.enumElement()) {
            collectEnumValues(element, "", values);
        }
        Set<String> finals = new java.util.LinkedHashSet<>();
        collectEnumFinals(context, "", finals);
        return new EnumerationTypeSyntax(nextId(), range(context), values,
                context.ORDERED() != null, context.CIRCULAR() != null, finals);
    }

    private void collectEnumFinals(InterlisParser.EnumerationContext context, String parent, Set<String> finals) {
        if (context.FINAL() != null) finals.add(parent);
        for (var element : context.enumElement()) {
            String name = element.Name().stream().map(TerminalNode::getText).collect(java.util.stream.Collectors.joining("."));
            String path = parent.isEmpty() ? name : parent + "." + name;
            if (element.enumeration() != null) collectEnumFinals(element.enumeration(), path, finals);
        }
    }

    private void collectEnumValues(InterlisParser.EnumElementContext context, String prefix,
                                   List<EnumerationTypeSyntax.EnumerationValueSyntax> values) {
        List<Token> names = new ArrayList<>();
        for (TerminalNode node : context.Name()) {
            names.add(node.getSymbol());
        }
        if (names.isEmpty()) {
            return;
        }
        Token first = names.getFirst();
        String ownPath = names.stream().map(Token::getText)
                .collect(java.util.stream.Collectors.joining("."));
        String path = prefix.isEmpty() ? ownPath : prefix + "." + ownPath;
        boolean nested = context.enumeration() != null
                && (!context.enumeration().enumElement().isEmpty()
                || context.enumeration().FINAL() != null);
        values.add(new EnumerationTypeSyntax.EnumerationValueSyntax(path, tokenRange(first), nested, names.size() > 1));
        if (context.enumeration() != null) {
            for (InterlisParser.EnumElementContext child : context.enumeration().enumElement()) {
                collectEnumValues(child, path, values);
            }
        }
    }

    private guru.interlis.syntax.StructureValueTypeSyntax structureValueType(InterlisParser.RestrictedStructureRefContext context) {
        boolean any=context.ANYSTRUCTURE()!=null;
        var names=context.structureRef();
        return new guru.interlis.syntax.StructureValueTypeSyntax(nextId(),range(context),
                any?Optional.empty():Optional.of(nameFromContext(names.getFirst())),
                names.stream().skip(any?0:1).map(this::nameFromContext).toList());
    }

    private CollectionTypeSyntax collectionType(InterlisParser.AttrTypeDefContext context) {
        CollectionKind kind = context.BAG() != null ? CollectionKind.BAG : CollectionKind.LIST;
        Cardinality cardinality = context.cardinality() == null
                ? Cardinality.unboundedFrom(0) : cardinality(context.cardinality());
        InterlisParser.RestrictedStructureRefContext restricted = context.restrictedStructureRef();
        TypeSyntax element;
        if (context.enumeration() != null) {
            element = enumerationType(context.enumeration());
        } else if (restricted == null) {
            element = new UnsupportedTypeSyntax(nextId(), range(context),
                    "MISSING_COLLECTION_ELEMENT", "missing collection element type");
            unsupportedFeatures.add("MISSING_COLLECTION_ELEMENT");
        } else if (restricted.type() != null) {
            element = buildType(restricted.type());
        } else if (language == LanguageVersion.ILI24 && restricted.ANYSTRUCTURE() == null
                && restricted.RESTRICTION() == null && restricted.structureRef().size() == 1) {
            // In 2.4 OF accepts AttrType, so an unqualified/qualified named element
            // may denote either a domain or a structure. Keep its actual identity;
            // structure restrictions and ANYSTRUCTURE retain their dedicated binder.
            element = new DomainReferenceSyntax(nextId(), range(restricted),
                    nameFromContext(restricted.structureRef(0)));
        } else if (restricted.ANYSTRUCTURE() != null || !restricted.structureRef().isEmpty()) {
            element = structureValueType(restricted);
        } else if (restricted.referenceAttr() != null) {
            element = referenceType(restricted.referenceAttr());
        } else if (restricted.numeric() != null) {
            element = numericType(restricted.numeric());
        } else if (restricted.enumeration() != null) {
            if (!LanguageProfile.supports(language,
                    LanguageProfile.Feature.INLINE_ENUM_COLLECTION)) {
                versionViolations.add(LanguageProfile.Feature.INLINE_ENUM_COLLECTION);
                versionViolationRanges.add(range(restricted.enumeration()));
            }
            element = enumerationType(restricted.enumeration());
        } else {
            element = new UnsupportedTypeSyntax(nextId(), range(restricted),
                    "ANYSTRUCTURE", "ANYSTRUCTURE element");

        }
        return new CollectionTypeSyntax(nextId(), range(context), kind, cardinality, element);
    }

    private Cardinality cardinality(InterlisParser.CardinalityContext context) {
        List<Token> numbers = new ArrayList<>();
        for (ParseTree child : context.children) {
            if (child instanceof TerminalNode terminal
                    && terminal.getSymbol().getType() == InterlisParser.PosNumber) {
                numbers.add(terminal.getSymbol());
            }
        }
        boolean unbounded = context.MUL() != null;
        if (numbers.isEmpty()) {
            return unbounded ? Cardinality.unboundedFrom(0)
                    : new Cardinality(BigInteger.ZERO, CardinalityUpper.unbounded());
        }
        BigInteger lower = new BigInteger(numbers.getFirst().getText());
        if (numbers.size() == 1 && context.DOTDOT() != null) {
            return CardinialityUnbounded(lower);
        }
        if (numbers.size() == 1 && unbounded) {
            return CardinialityUnbounded(lower);
        }
        BigInteger upper = numbers.size() > 1 ? new BigInteger(numbers.get(1).getText()) : lower;
        return new Cardinality(lower, new CardinalityUpper.Finite(upper));
    }

    private static Cardinality CardinialityUnbounded(BigInteger lower) {
        return new Cardinality(lower, CardinalityUpper.unbounded());
    }

    ExpressionSyntax buildExpressionFragment(InterlisParser.ExpressionContext context) {
        return buildExpression(context);
    }

    ExpressionSyntax buildPathFragment(InterlisParser.ObjectOrAttributePathContext context) {
        return pathExpression(context);
    }

    AttributeDeclarationSyntax buildAttributeFragment(InterlisParser.AttributeDefContext context) {
        return buildAttribute(context);
    }

    private ExpressionSyntax buildExpression(InterlisParser.ExpressionContext context) {
        return buildTerm(context.term());
    }

    private ExpressionSyntax buildTerm(InterlisParser.TermContext context) {
        if (context.term0().size() == 2) {
            return new BinaryExpressionSyntax(nextId(), range(context),
                    guru.interlis.model.BinaryOperator.IMPLIES,
                    buildTerm0(context.term0(0)), buildTerm0(context.term0(1)));
        }
        return buildTerm0(context.term0(0));
    }

    private ExpressionSyntax buildTerm0(InterlisParser.Term0Context context) {
        List<ExpressionSyntax> operands = new ArrayList<>();
        List<BinaryOperator> operators = new ArrayList<>();
        for (ParseTree child : context.children) {
            if (child instanceof InterlisParser.Term1Context term1) {
                operands.add(buildTerm1(term1));
            } else if (child instanceof TerminalNode terminal) {
                BinaryOperator operator = switch (terminal.getSymbol().getType()) {
                    case InterlisParser.OR -> BinaryOperator.OR;
                    case InterlisParser.PLUS -> BinaryOperator.PLUS;
                    case InterlisParser.MINUS -> BinaryOperator.MINUS;
                    default -> null;
                };
                if (operator != null) {
                    operators.add(operator);
                }
            }
        }
        if (operands.isEmpty()) {
            return unsupportedExpression(context, "EMPTY_EXPRESSION", "empty expression");
        }
        ExpressionSyntax result = operands.getFirst();
        for (int i = 0; i < operators.size() && i + 1 < operands.size(); i++) {
            result = new BinaryExpressionSyntax(nextId(), range(context), operators.get(i),
                    result, operands.get(i + 1));
        }
        return result;
    }

    private ExpressionSyntax buildTerm1(InterlisParser.Term1Context context) {
        List<ExpressionSyntax> operands = new ArrayList<>();
        List<BinaryOperator> operators = new ArrayList<>();
        for (ParseTree child : context.children) {
            if (child instanceof InterlisParser.Term2Context term2) {
                operands.add(buildTerm2(term2));
            } else if (child instanceof TerminalNode terminal) {
                BinaryOperator operator = switch (terminal.getSymbol().getType()) {
                    case InterlisParser.AND -> BinaryOperator.AND;
                    case InterlisParser.MUL -> BinaryOperator.MUL;
                    case InterlisParser.DIV -> BinaryOperator.DIV;
                    case InterlisParser.POW -> BinaryOperator.POW;
                    default -> null;
                };
                if (operator != null) {
                    operators.add(operator);
                }
            }
        }
        if (operands.isEmpty()) {
            return unsupportedExpression(context, "EMPTY_EXPRESSION", "empty expression");
        }
        ExpressionSyntax result = operands.getFirst();
        for (int i = 0; i < operators.size() && i + 1 < operands.size(); i++) {
            result = new BinaryExpressionSyntax(nextId(), range(context), operators.get(i),
                    result, operands.get(i + 1));
        }
        return result;
    }

    private ExpressionSyntax buildTerm2(InterlisParser.Term2Context context) {
        if (context.relation() != null && context.predicate().size() >= 2) {
            BinaryOperator operator = context.relation().LT()!=null && context.relation().GT()!=null
                    ? BinaryOperator.NEQ : relationOperator(context.relation().getStart().getType());
            return new BinaryExpressionSyntax(nextId(), range(context), operator,
                    buildPredicate(context.predicate(0)), buildPredicate(context.predicate(1)));
        }
        if (context.predicate().isEmpty()) {
            return unsupportedExpression(context, "EMPTY_EXPRESSION", "empty expression");
        }
        return buildPredicate(context.predicate(0));
    }

    private BinaryOperator relationOperator(int tokenType) {
        return switch (tokenType) {
            case InterlisParser.EQ -> BinaryOperator.EQ;
            case InterlisParser.NOT_EQ -> BinaryOperator.NEQ;
            case InterlisParser.LT -> BinaryOperator.LT;
            case InterlisParser.LTEQ -> BinaryOperator.LTE;
            case InterlisParser.GT -> BinaryOperator.GT;
            case InterlisParser.GTEQ -> BinaryOperator.GTE;
            default -> BinaryOperator.EQ;
        };
    }

    private ExpressionSyntax buildPredicate(InterlisParser.PredicateContext context) {
        if (context.DEFINED() != null && context.factor() != null) {
            return new DefinedExpressionSyntax(nextId(), range(context), buildFactor(context.factor()));
        }
        if (context.expression() != null) {
            ExpressionSyntax inner = buildExpression(context.expression());
            if (context.NOT() != null) {
                return new UnaryExpressionSyntax(nextId(), range(context), UnaryOperator.NOT, inner);
            }
            return new ParenthesizedExpressionSyntax(nextId(), range(context), inner);
        }
        if (context.factor() != null) {
            ExpressionSyntax factor = buildFactor(context.factor());
            if (context.NOT() != null) {
                return new UnaryExpressionSyntax(nextId(), range(context), UnaryOperator.NOT, factor);
            }
            return factor;
        }
        return unsupportedExpression(context, "UNSUPPORTED_PREDICATE", "unsupported predicate");
    }

    private ViewDeclarationSyntax.Formation inspectionFormation(InterlisParser.InspectionContext context) {
        var path = new PathExpressionSyntax(nextId(), range(context), context.Name().stream()
                .map(n -> new PathExpressionSyntax.Segment(nameFromToken(n.getSymbol()), false,
                    Optional.empty(), Optional.empty(), tokenRange(n.getSymbol()))).toList());
        return new ViewDeclarationSyntax.Formation(context.AREA() == null
                ? ViewDeclarationSyntax.FormationKind.INSPECTION : ViewDeclarationSyntax.FormationKind.AREA_INSPECTION,
                List.of(viewBase(context.renamedViewableRef(), false)), List.of(path));
    }

    private ExpressionSyntax buildFactor(InterlisParser.FactorContext context) {
        if (context.inspection() != null || context.INSPECTION() != null) {
            return new guru.interlis.syntax.InspectionExpressionSyntax(nextId(), range(context),
                    Optional.ofNullable(context.viewableRef()).map(this::nameFromContext),
                    Optional.ofNullable(context.inspection()).map(this::inspectionFormation),
                    Optional.ofNullable(context.objectOrAttributePath()).map(this::pathExpression));
        }
        if (context.ALL() != null && context.objectOrAttributePath() != null) {
            return unsupportedExpression(context, "ALL_OF_PATH", "ALL OF path requires view semantics");
        }
        if (context.objectOrAttributePath() != null) {
            return pathExpression(context.objectOrAttributePath());
        }
        if (context.constant() != null) {
            return constantExpression(context.constant());
        }
        if (context.Number() != null) {
            Token token = context.Number().getSymbol();
            return new LiteralExpressionSyntax(nextId(), range(context), LiteralKind.NUMBER,
                    token.getText(), decimal(token.getText(),tokenRange(token)), Optional.empty());
        }
        if (context.functionCall() != null) {
            InterlisParser.FunctionCallContext call = context.functionCall();
            checkFunctionList(call.argument().size(), call.LPAR(), call.RPAR(), LanguageProfile.Feature.EMPTY_FUNCTION_ARGUMENTS);
            List<ExpressionSyntax> arguments = new ArrayList<>();
            for (InterlisParser.ArgumentContext argument : call.argument()) {
                arguments.add(buildArgument(argument));
            }
            List<NameSyntax.NameSegmentSyntax> segments = call.Name().stream()
                    .map(n -> new NameSyntax.NameSegmentSyntax(n.getText(), tokenRange(n.getSymbol())))
                    .toList();
            SourceRange nameRange = new SourceRange(source.id(), source.revision(),
                    segments.getFirst().range().startOffset(), segments.getLast().range().endOffset());
            NameSyntax name = new NameSyntax(nextId(), nameRange, segments);
            return new FunctionCallExpressionSyntax(nextId(), range(context), name, arguments);
        }
        if (context.ALL() != null) {
            if (context.objectOrAttributePath() != null) {
                return unsupportedExpression(context, "ALL_OF_PATH", "ALL OF path requires view semantics");
            }
            return new guru.interlis.syntax.ObjectSetExpressionSyntax(nextId(), range(context), Optional.empty());
        }
        if (context.INTERLIS() != null) {
            checkFunctionList(context.argument().size(), context.LPAR(), context.RPAR(), LanguageProfile.Feature.EMPTY_FUNCTION_ARGUMENTS);
            List<ExpressionSyntax> arguments = new ArrayList<>();
            for (InterlisParser.ArgumentContext argument : context.argument()) {
                arguments.add(buildArgument(argument));
            }
            Token nameToken=context.Name().isEmpty() ? context.URI()!=null?context.URI().getSymbol():context.INTERLIS().getSymbol()
                    :context.Name(context.Name().size()-1).getSymbol();
            var name=nameSyntax(List.of(context.INTERLIS().getSymbol(),nameToken));
            return new BuiltinFunctionExpressionSyntax(nextId(),range(context),name,arguments);
        }
        if (context.PARAMETER() != null) {
            var segments = context.Name().stream().map(n -> new NameSyntax.NameSegmentSyntax(n.getText(), tokenRange(n.getSymbol()))).toList();
            var nameRange = new SourceRange(source.id(), source.revision(), segments.getFirst().range().startOffset(), segments.getLast().range().endOffset());
            return new guru.interlis.syntax.ParameterExpressionSyntax(nextId(), range(context), new NameSyntax(nextId(), nameRange, segments));
        }
        unsupportedFeatures.add("UNSUPPORTED_EXPRESSION");
        return unsupportedExpression(context, "UNSUPPORTED_EXPRESSION", "unsupported expression");
    }

    private ExpressionSyntax buildArgument(InterlisParser.ArgumentContext context) {
        // ALL is also accepted by the factor grammar. Explicit selection belongs here.
        if (context.ALL() == null) return buildExpression(context.expression());
        Optional<guru.interlis.syntax.ObjectArgumentTypeSyntax> selection = Optional.empty();
        if (context.restrictedClassOrAssRef() != null) {
            var restricted = context.restrictedClassOrAssRef();
            var names = restricted.classOrAssociationRef();
            boolean any = restricted.ANYCLASS() != null;
            selection = Optional.of(new guru.interlis.syntax.ObjectArgumentTypeSyntax(nextId(),
                    range(restricted), any ? Optional.empty() : Optional.of(nameFromContext(names.getFirst())),
                    names.stream().skip(any ? 0 : 1).map(this::nameFromContext).toList(), true));
        } else if (context.viewableRef() != null) {
            selection = Optional.of(new guru.interlis.syntax.ObjectArgumentTypeSyntax(nextId(),
                    range(context.viewableRef()), Optional.of(nameFromContext(context.viewableRef())),
                    List.of(), true));
        }
        return new guru.interlis.syntax.ObjectSetExpressionSyntax(nextId(), range(context), selection);
    }

    private ExpressionSyntax constantExpression(InterlisParser.ConstantContext context) {
        if (context.UNDEFINED() != null) {
            return new UndefinedExpressionSyntax(nextId(), range(context));
        }
        if (context.textConst() != null) {
            String lexeme = context.textConst().getText();
            return new LiteralExpressionSyntax(nextId(), range(context), LiteralKind.TEXT,
                    lexeme, Optional.empty(), Optional.empty());
        }
        if (context.numericConst() != null) {
            InterlisParser.NumericConstContext numeric = context.numericConst();
            String text = numeric.decConst().getText();
            Optional<NameSyntax> unit = Optional.ofNullable(numeric.unitRef()).map(this::nameFromContext);
            if ("PI".equals(text) || "LNBASE".equals(text)) {
                return new ConstantExpressionSyntax(nextId(), range(context), text, unit);
            }
            if (numeric.decConst() != null && numeric.decConst().getStart() != null) {
                String lexeme = numeric.decConst().getText();
                return new LiteralExpressionSyntax(nextId(), range(context), LiteralKind.NUMBER,
                        lexeme, decimal(lexeme,range(numeric.decConst())), unit);
            }
            unsupportedFeatures.add("NUMERIC_CONSTANT");
            return unsupportedExpression(context, "NUMERIC_CONSTANT", "numeric constant");
        }
        if (context.classConst() != null) {
            return new ClassConstantExpressionSyntax(nextId(), range(context),
                    nameFromContext(context.classConst().viewableRef()));
        }
        if (context.attributePathConst() != null) {
            var constant = context.attributePathConst();
            return new guru.interlis.syntax.AttributeConstantExpressionSyntax(nextId(), range(context),
                    constant.viewableRef() == null ? Optional.empty() : Optional.of(nameFromContext(constant.viewableRef())),
                    nameFromToken(constant.Name().getSymbol()));
        }
        if (context.enumerationConst() != null) {
            return new EnumerationConstantExpressionSyntax(nextId(), range(context),
                    context.enumerationConst().getText());
        }
        unsupportedFeatures.add("CONSTANT_KIND");
        return unsupportedExpression(context, "CONSTANT_KIND", "unsupported constant");
    }

    private boolean containsToken(ParserRuleContext context, int tokenType) {
        for (int i = context.getStart().getTokenIndex(); i <= context.getStop().getTokenIndex(); i++) {
            if (tokenStream.get(i).getType() == tokenType) {
                return true;
            }
        }
        return false;
    }

    private PathExpressionSyntax tokenPath(List<Token> tokens) {
        List<PathExpressionSyntax.Segment> segments = new ArrayList<>();
        for (Token token : tokens) {
            var name = new NameSyntax(nextId(), tokenRange(token), List.of(
                    new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token))));
            segments.add(new PathExpressionSyntax.Segment(name, false, Optional.empty(), Optional.empty(), name.range()));
        }
        return new PathExpressionSyntax(nextId(), tokenRange(tokens.getFirst(), tokens.getLast()), segments);
    }

    private ExpressionSyntax pathExpression(InterlisParser.ObjectOrAttributePathContext context) {
        List<PathExpressionSyntax.Segment> segments = new ArrayList<>();
        for (InterlisParser.PathElContext element : context.pathEl()) {
            if (element.COLON() != null || !element.EQ().isEmpty()) {
                return unsupportedExpression(context, "INSPECTION_PATH",
                        "inspection context is not yet represented");
            }
            Token token = firstMeaningfulToken(element);
            if (token == null) {
                continue;
            }
            Optional<guru.interlis.model.PathIndex> selection = Optional.empty();
            if (element.attributeRef() != null && element.attributeRef().LSBR() != null) {
                var attribute = element.attributeRef();
                var kind = attribute.FIRST() != null ? guru.interlis.model.PathIndex.Kind.FIRST
                        : attribute.LAST() != null ? guru.interlis.model.PathIndex.Kind.LAST
                        : guru.interlis.model.PathIndex.Kind.POSITION;
                selection = Optional.of(new guru.interlis.model.PathIndex(kind,
                        attribute.PosNumber() == null ? Optional.empty()
                                : Optional.of(new java.math.BigInteger(attribute.PosNumber().getText()))));
            }
            segments.add(new PathExpressionSyntax.Segment(new NameSyntax(nextId(), tokenRange(token), List.of(
                    new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token)))),
                    element.associationPath() != null && element.associationPath().BACKSLASH() != null,
                    element.LSBR() == null ? Optional.empty() : Optional.of(new NameSyntax(nextId(),
                            tokenRange(element.Name(1).getSymbol()), List.of(new NameSyntax.NameSegmentSyntax(
                            element.Name(1).getText(), tokenRange(element.Name(1).getSymbol()))))), selection, range(element)));
        }
        if (segments.isEmpty()) {
            unsupportedFeatures.add("EMPTY_PATH");
            return unsupportedExpression(context, "EMPTY_PATH", "empty object path");
        }
        return new PathExpressionSyntax(nextId(), range(context), segments);
    }

    private Token firstMeaningfulToken(ParserRuleContext context) {
        if (context.getStart() == null || context.getStop() == null) return null;
        for (int i = context.getStart().getTokenIndex(); i <= context.getStop().getTokenIndex(); i++) {
            Token token = tokenStream.get(i);
            if (token.getType() == InterlisParser.Name
                    || token.getType() == InterlisParser.THIS
                    || token.getType() == InterlisParser.PARENT
                    || token.getType() == InterlisParser.THISAREA
                    || token.getType() == InterlisParser.THATAREA
                    || token.getType() == InterlisParser.AGGREGATES) {
                return token;
            }
        }
        return context.getStart();
    }

    private NameSyntax nameFromContext(ParserRuleContext context) {
        List<Token> names = new ArrayList<>();
        collectNameTokens(context, names);
        return names.isEmpty() ? syntheticName("<missing-name>", range(context))
                : nameSyntax(names);
    }

    private void collectNameTokens(ParseTree tree, List<Token> target) {
        if (tree instanceof TerminalNode terminal) {
            int type = terminal.getSymbol().getType();
            if (type == InterlisParser.Name || type == InterlisParser.INTERLIS
                    || type == InterlisParser.UUIDOID || type == InterlisParser.ANYOID
                    || type == InterlisParser.HALIGNMENT || type == InterlisParser.VALIGNMENT
                    || type == InterlisParser.REFSYSTEM || type == InterlisParser.SIGN || type == InterlisParser.METAOBJECT) {
                target.add(terminal.getSymbol());
            }
            return;
        }
        if (tree instanceof ParserRuleContext rule) {
            if(rule.children!=null) for (ParseTree child : rule.children) {
                collectNameTokens(child, target);
            }
        }
    }

    private NameSyntax nameSyntax(List<Token> tokens) {
        List<Token> sorted = new ArrayList<>(tokens);
        sorted.sort(Comparator.comparingInt(Token::getTokenIndex));
        List<NameSyntax.NameSegmentSyntax> segments = sorted.stream()
                .map(t -> new NameSyntax.NameSegmentSyntax(t.getText(), tokenRange(t)))
                .toList();
        Token first = sorted.getFirst();
        Token last = sorted.getLast();
        return new NameSyntax(nextId(), tokenRange(first, last), segments);
    }

    private NameSyntax syntheticName(String text, SourceRange range) {
        return new NameSyntax(nextId(), range, List.of(new NameSyntax.NameSegmentSyntax(text, range)));
    }

    private Set<Modifier> modifiers(ParserRuleContext context) {
        return directModifiers(context);
    }

    private Set<Modifier> directModifiers(ParserRuleContext context) {
        Set<Modifier> result = new LinkedHashSet<>();
        for (ParseTree child : context.children) {
            if (!(child instanceof TerminalNode terminal)) {
                continue;
            }
            switch (terminal.getSymbol().getType()) {
                case InterlisParser.ABSTRACT -> addModifier(result, Modifier.ABSTRACT, terminal.getSymbol());
                case InterlisParser.EXTENDED -> addModifier(result, Modifier.EXTENDED, terminal.getSymbol());
                case InterlisParser.FINAL -> addModifier(result, Modifier.FINAL, terminal.getSymbol());
                case InterlisParser.TRANSIENT -> addModifier(result, Modifier.TRANSIENT, terminal.getSymbol());
                case InterlisParser.MANDATORY -> addModifier(result, Modifier.MANDATORY, terminal.getSymbol());
                case InterlisParser.ORDERED -> addModifier(result, Modifier.ORDERED, terminal.getSymbol());
                case InterlisParser.EXTERNAL -> addModifier(result, Modifier.EXTERNAL, terminal.getSymbol());
                case InterlisParser.HIDING -> addModifier(result, Modifier.HIDING, terminal.getSymbol());
                case InterlisParser.GENERIC -> addModifier(result, Modifier.GENERIC, terminal.getSymbol());
                case InterlisParser.VIEW -> addModifier(result, Modifier.VIEW, terminal.getSymbol());
                case InterlisParser.OID -> addModifier(result, Modifier.OID, terminal.getSymbol());
                case InterlisParser.DERIVED -> addModifier(result, Modifier.DERIVED, terminal.getSymbol());
                default -> {
                }
            }
        }
        return result;
    }

    private UnsupportedDeclarationSyntax unsupportedDeclaration(ParserRuleContext context, String feature,
                                                                String construct) {
        unsupportedFeatures.add(feature);
        return new UnsupportedDeclarationSyntax(nextId(), range(context), feature, construct);
    }

    private UnsupportedDeclarationSyntax unsupportedToken(Token token, String feature,
                                                          String construct) {
        unsupportedFeatures.add(feature);
        return new UnsupportedDeclarationSyntax(nextId(), tokenRange(token), feature, construct);
    }

    private UnsupportedTypeSyntax unsupportedType(ParserRuleContext context, String feature,
                                                       String construct) {
        unsupportedFeatures.add(feature);
        return new UnsupportedTypeSyntax(nextId(), range(context), feature, construct);
    }

    private UnsupportedExpressionSyntax unsupportedExpression(ParserRuleContext context, String feature,
                                                                String construct) {
        unsupportedFeatures.add(feature);
        return new UnsupportedExpressionSyntax(nextId(), range(context), feature, construct);
    }

    private Optional<DecimalLiteral> decimal(String lexeme,SourceRange numericRange) {
        int digits=0;
        for(int i=0;i<lexeme.length();i++)if(lexeme.charAt(i)>='0' && lexeme.charAt(i)<='9' && ++digits>budget.maxNumericLiteralDigits())
            throw new guru.interlis.compiler.internal.AnalysisBudgetExceededException("maxNumericLiteralDigits",budget.maxNumericLiteralDigits());
        int e=Math.max(lexeme.indexOf('e'),lexeme.indexOf('E'));
        if(e>=0) {
            long exponent=0;int start=e+1;
            if(start<lexeme.length() && (lexeme.charAt(start)=='+' || lexeme.charAt(start)=='-'))start++;
            for(int i=start;i<lexeme.length();i++) {
                char c=lexeme.charAt(i);if(c<'0' || c>'9')break;
                exponent=exponent*10+c-'0';
                if(exponent>budget.maxAbsoluteDecimalExponent())throw new guru.interlis.compiler.internal.AnalysisBudgetExceededException("maxAbsoluteDecimalExponent",budget.maxAbsoluteDecimalExponent());
            }
        }
        try {
            return Optional.of(DecimalLiteral.parse(lexeme));
        } catch (NumberFormatException | ArithmeticException invalid) {
            propertyDiagnostics.add(guru.interlis.diagnostics.DiagnosticDraft.of(new guru.interlis.diagnostics.DiagnosticCode("ILI-SYN-0002"),
                    guru.interlis.diagnostics.Severity.ERROR,guru.interlis.diagnostics.Phase.PARSING,"diagnostic.syntax.unexpected")
                    .at(numericRange).argument(new guru.interlis.diagnostics.DiagnosticArgument.TokenArgument("actual",lexeme))
                    .argument(new guru.interlis.diagnostics.DiagnosticArgument.IdentifierListArgument("expected",List.of("decimal literal"))));
            return Optional.empty();
        }
    }

    private String unquote(String text) {
        return guru.interlis.compiler.internal.InterlisStrings.decode(text).text();
    }

    private Optional<SourceRange> optionalRange(Token token, ParserRuleContext context) {
        return token == null ? Optional.empty() : Optional.of(tokenRange(token));
    }

    private SyntaxNodeId nextId() {
        if(++nodeCounter>budget.maxSyntaxNodes())throw new guru.interlis.compiler.internal.AnalysisBudgetExceededException("maxSyntaxNodes",budget.maxSyntaxNodes());
        return new SyntaxNodeId("syntax-" + nodeCounter);
    }

    private SourceRange range(ParserRuleContext context) {
        if (context == null || context.getStart() == null) {
            return offsets.eofRange();
        }
        Token stop = context.getStop();
        return tokenRange(context.getStart(), stop);
    }

    private SourceRange tokenRange(Token token) {
        return offsets.tokenRange(token);
    }

    private SourceRange tokenRange(Token start, Token stopInclusive) {
        if (start == null) {
            return offsets.eofRange();
        }
        if (stopInclusive == null || stopInclusive.getTokenIndex() < start.getTokenIndex()) {
            return tokenRange(start);
        }
        int startCp = Math.max(0, start.getStartIndex());
        int stopCp = stopInclusive.getStopIndex();
        if (stopCp < startCp) {
            return tokenRange(start);
        }
        return offsets.range(startCp, stopCp);
    }

    private record Member(int index, SyntaxNode node) {
    }

    private record TypeResult(TypeSyntax type, boolean mandatory) {
    }
}
