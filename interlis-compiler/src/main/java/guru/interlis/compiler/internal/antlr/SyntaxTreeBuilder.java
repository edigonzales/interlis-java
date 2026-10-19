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
    private long nodeCounter;
    private final Set<String> unsupportedFeatures = new LinkedHashSet<>();
    private final List<SourceRange> recoveredRanges = new ArrayList<>();

    SyntaxTreeBuilder(SourceText source, AntlrOffsetMap offsets,
                      CommonTokenStream tokenStream, LanguageVersion language) {
        this.source = Objects.requireNonNull(source, "source");
        this.offsets = Objects.requireNonNull(offsets, "offsets");
        this.tokenStream = Objects.requireNonNull(tokenStream, "tokenStream");
        this.language = Objects.requireNonNull(language, "language");
    }

    Set<String> unsupportedFeatures() {
        return Set.copyOf(unsupportedFeatures);
    }

    List<SourceRange> recoveredRanges() {
        return List.copyOf(recoveredRanges);
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
                modelLanguage(context), translationSource(context), translationVersion(context),
                endName(context.END(), context), imports, members);
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

    private Optional<String> oidRule(ParserRuleContext context) {
        int start = -1;
        int end = -1;
        for (int i = context.getStart().getTokenIndex(); i <= context.getStop().getTokenIndex(); i++) {
            Token token = tokenStream.get(i);
            if (start < 0 && token.getType() == InterlisParser.BASKET
                    && i + 1 <= context.getStop().getTokenIndex()
                    && tokenStream.get(i + 1).getType() == InterlisParser.OID) {
                start = i;
            }
            if (start < 0 && token.getType() == InterlisParser.OID) {
                start = i;
            }
            if (start >= 0 && token.getType() == InterlisParser.SEMI) {
                end = i;
                break;
            }
        }
        if (start < 0) {
            return Optional.empty();
        }
        int last = end > start ? end - 1 : start;
        int from = tokenStream.get(start).getStartIndex();
        int to = tokenStream.get(last).getStopIndex();
        return from >= 0 && to >= from
                ? Optional.of(source.text().substring(from, to + 1).trim()) : Optional.empty();
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
                if (index <= stop && tokenStream.get(index).getType() == InterlisParser.UNQUALIFIED) {
                    unqualified = true;
                    index++;
                }
                while (index <= stop) {
                    Token current = tokenStream.get(index);
                    if (current.getType() == InterlisParser.SEMI) {
                        break;
                    }
                    if (current.getType() == InterlisParser.Name
                            || current.getType() == InterlisParser.INTERLIS) {
                        result.add(new ImportSyntax(nextId(), tokenRange(current),
                                current.getText(), unqualified, Optional.empty()));
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
            members.add(new Member(unit.getStart().getTokenIndex(), buildUnit(unit)));
        }
        for (InterlisParser.FunctionDefContext function : context.functionDef()) {
            members.add(new Member(function.getStart().getTokenIndex(), buildFunction(function)));
        }
        Set<String> declaredParameters = new LinkedHashSet<>();
        for (InterlisParser.RunTimeParameterDefContext parameter
                : context.runTimeParameterDef()) {
            if (!declaredParameters.add(parameter.Name().getText())) {
                unsupportedFeatures.add("DUPLICATE_PARAMETER");
            }
        }
        if (context.NOINCREMENTALTRANSFER() != null) {
            unsupportedFeatures.add("NOINCREMENTALTRANSFER");
        }
        if (context.CHARSET() != null) {
            unsupportedFeatures.add("CHARSET");
        }
        if (context.XMLNS() != null) {
            unsupportedFeatures.add("XMLNS");
        }
        members.sort(Comparator.comparingInt(Member::index));
        return members.stream().map(Member::node).toList();
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

    private ViewDeclarationSyntax buildView(InterlisParser.ViewDefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-view>" : nameToken.getText();
        Optional<NameSyntax> baseView = context.EXTENDS() != null && context.viewRef() != null
                ? Optional.of(nameFromContext(context.viewRef())) : Optional.empty();
        Optional<String> formation = context.formationDef() == null ? Optional.empty()
                : Optional.of(source.text().substring(
                        context.formationDef().getStart().getStartIndex(),
                        context.formationDef().getStop().getStopIndex() + 1).trim());
        Optional<String> body = Optional.empty();
        if (context.EQ() != null && context.END() != null) {
            int from = context.EQ().getSymbol().getStopIndex() + 1;
            int to = context.END().getSymbol().getStartIndex() - 1;
            if (from <= to) {
                body = Optional.of(source.text().substring(from, to + 1).trim());
            }
        }
        Optional<String> endName = Optional.empty();
        if (context.END() != null && context.Name().size() > 1) {
            endName = Optional.of(context.Name(context.Name().size() - 1).getText());
        }
        List<ViewDeclarationSyntax.BaseExtension> baseExtensions = new ArrayList<>();
        for (InterlisParser.BaseExtensionDefContext extension : context.baseExtensionDef()) {
            List<NameSyntax> targets = new ArrayList<>();
            for (InterlisParser.RenamedViewableRefContext target : extension.renamedViewableRef()) {
                targets.add(nameFromContext(target));
            }
            Token baseToken = extension.Name().getSymbol();
            NameSyntax baseName = new NameSyntax(nextId(), tokenRange(baseToken), List.of(
                    new NameSyntax.NameSegmentSyntax(baseToken.getText(),
                            tokenRange(baseToken))));
            baseExtensions.add(new ViewDeclarationSyntax.BaseExtension(baseName, targets));
        }
        List<NameSyntax> allOf = new ArrayList<>();
        List<String> attributeNames = new ArrayList<>();
        if (context.viewAttributes() != null) {
            int start = context.viewAttributes().getStart().getTokenIndex();
            int stop = context.viewAttributes().getStop().getTokenIndex();
            for (int i = start; i <= stop; i++) {
                if (tokenStream.get(i).getType() == InterlisParser.ALL && i + 2 <= stop
                        && tokenStream.get(i + 1).getType() == InterlisParser.OF
                        && tokenStream.get(i + 2).getType() == InterlisParser.Name) {
                    Token token = tokenStream.get(i + 2);
                    allOf.add(new NameSyntax(nextId(), tokenRange(token), List.of(
                            new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token)))));
                }
            }
            for (InterlisParser.AttributeDefContext attribute :
                    context.viewAttributes().attributeDef()) {
                if (attribute.Name() != null) {
                    attributeNames.add(attribute.Name().getText());
                }
            }
        }
        return new ViewDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), directModifiers(context), baseView, formation, body,
                baseExtensions, allOf, attributeNames, endName);
    }

    private FunctionDeclarationSyntax buildFunction(InterlisParser.FunctionDefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-function>" : nameToken.getText();
        List<FunctionDeclarationSyntax.FunctionParameterSyntax> parameters = new ArrayList<>();
        for (InterlisParser.ArgumentDefContext argument : context.argumentDef()) {
            String parameterName = argument.Name() == null ? "<missing>" : argument.Name().getText();
            String typeName = argument.argumentType() == null ? ""
                    : source.text().substring(argument.argumentType().getStart().getStartIndex(),
                    argument.argumentType().getStop().getStopIndex() + 1).trim();
            parameters.add(new FunctionDeclarationSyntax.FunctionParameterSyntax(parameterName,
                    typeName));
        }
        int colon = context.COLON().getSymbol().getTokenIndex();
        int semicolon = context.getStop().getTokenIndex();
        String returnType = "";
        if (colon + 1 < semicolon) {
            int from = tokenStream.get(colon + 1).getStartIndex();
            int to = tokenStream.get(semicolon - 1).getStopIndex();
            if (from >= 0 && to >= from) {
                returnType = source.text().substring(from, to + 1).trim();
            }
        }
        return new FunctionDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), parameters, returnType);
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
                    definition = source.text().substring(start, stop + 1);
                }
            }
        }
        return new UnitDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), context.ABSTRACT() != null, base, definition);
    }

    private TopicSyntax buildTopic(InterlisParser.TopicDefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-topic>" : nameToken.getText();
        Set<Modifier> modifiers = directModifiers(context);
        if (context.VIEW() != null) {
            modifiers.add(Modifier.VIEW);
        }
        if (!context.OID().isEmpty()) {
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
                endName(context.END(), context), base, dependencies, members, oidRule(context));
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
            return new UnitDeclarationSyntax(nextId(), range(context),
                    "<graphic-" + context.graphicDef().getStart().getTokenIndex() + ">",
                    Optional.empty(), false, Optional.empty(), "");
        }
        if (context.constraintsDef() != null) {
            return buildExternalConstraints(context.constraintsDef());
        }
        if (context.functionDef() != null) {
            return buildFunction(context.functionDef());
        }
        if (context.unitDef() != null) {
            String unitText = context.unitDef().getText();
            if (unitText.contains("=(") && !unitText.contains("[")) {
                return buildDomainFromUnit(context.unitDef());
            }
            return buildUnit(context.unitDef());
        }
        if (context.metaDataBasketDef() != null) {
            return new UnitDeclarationSyntax(nextId(), range(context),
                    "<metaobject-" + context.metaDataBasketDef().getStart().getTokenIndex() + ">",
                    Optional.empty(), false, Optional.empty(), "");
        }
        if (context.contextDef() != null) {
            return new UnitDeclarationSyntax(nextId(), range(context),
                    "<context-" + context.contextDef().getStart().getTokenIndex() + ">",
                    Optional.empty(), false, Optional.empty(), "");
        }
        return unsupportedDeclaration(context, "DECLARATION", "unknown declaration");
    }

    private DomainDeclarationSyntax buildDomainFromUnit(InterlisParser.UnitDefContext context) {
        String text = context.getText();
        int eq = text.indexOf('=');
        String header = eq < 0 ? text : text.substring(0, eq);
        int extendsIndex = header.toUpperCase(java.util.Locale.ROOT).indexOf("EXTENDS");
        String name = header;
        Optional<NameSyntax> base = Optional.empty();
        if (extendsIndex >= 0) {
            name = header.substring(0, extendsIndex).trim();
            String baseText = header.substring(extendsIndex + "EXTENDS".length()).trim();
            List<NameSyntax.NameSegmentSyntax> segments = new ArrayList<>();
            for (String part : baseText.split("\\.")) {
                segments.add(new NameSyntax.NameSegmentSyntax(part, range(context)));
            }
            base = Optional.of(new NameSyntax(new SyntaxNodeId(
                    "domain-base-" + context.getStart().getTokenIndex()), range(context), segments));
        }
        int open = text.indexOf('(', eq + 1);
        int close = text.lastIndexOf(')');
        List<EnumerationTypeSyntax.EnumerationValueSyntax> values = new ArrayList<>();
        if (open >= 0 && close > open) {
            for (String value : text.substring(open + 1, close).split(",")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    values.add(new EnumerationTypeSyntax.EnumerationValueSyntax(
                            trimmed, range(context), false));
                }
            }
        }
        EnumerationTypeSyntax type = new EnumerationTypeSyntax(nextId(), range(context),
                values, false, false);
        return new DomainDeclarationSyntax(nextId(), range(context), name.strip(),
                Optional.empty(), Set.of(), base, type, List.of());
    }

    private ClassDeclarationSyntax buildClass(InterlisParser.ClassDefContext context) {
        Token nameToken = context.Name().isEmpty() ? null : context.Name(0).getSymbol();
        String name = nameToken == null ? "<missing-class>" : nameToken.getText();
        Optional<NameSyntax> base = context.EXTENDS() == null || context.classOrStructureRef() == null
                ? Optional.empty() : Optional.of(nameFromContext(context.classOrStructureRef()));
        if (context.OID() != null) {
        }
        List<SyntaxNode> members = buildClassMembers(context.classOrStructureDef());
        return new ClassDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), modifiers(context), base, members,
                oidRule(context), endName(context.END(), context));
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
        members.sort(Comparator.comparingInt(Member::index));
        return members.stream().map(Member::node).toList();
    }

    private AttributeDeclarationSyntax buildAttribute(InterlisParser.AttributeDefContext context) {
        Token nameToken = context.Name() != null ? context.Name().getSymbol()
                : (context.UUIDOID() != null ? context.UUIDOID().getSymbol() : null);
        String name = nameToken == null ? "<missing-attribute>" : nameToken.getText();
        TypeResult type;
        if (context.attrTypeDef() != null
                && directModifiers(context).contains(Modifier.EXTENDED)
                && context.attrTypeDef().attrType() == null
                && context.attrTypeDef().enumeration() == null
                && context.attrTypeDef().numeric() == null
                && context.attrTypeDef().BAG() == null
                && context.attrTypeDef().LIST() == null) {
            type = new TypeResult(new GenericTypeSyntax(nextId(), range(context), "INHERITED"),
                    context.attrTypeDef().MANDATORY() != null);
        } else if (context.attrTypeDef() != null) {
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
        if (type.type() instanceof UnsupportedTypeSyntax
                && attributeModifiers.contains(Modifier.EXTENDED)) {
            type = new TypeResult(new GenericTypeSyntax(nextId(), range(context), "INHERITED"),
                    type.mandatory);
        }
        return new AttributeDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), type.mandatory, transientAttribute,
                context.ASSIGN() != null, context.CONTINUOUS() != null,
                context.SUBDIVISION() != null, attributeModifiers, type.type);
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
        for (int i = startIndex; i < eqIndex; i++) {
            Token token = tokenStream.get(i);
            if (token.getType() == InterlisParser.EXTENDS) {
                List<Token> names = new ArrayList<>();
                for (int j = i + 1; j < eqIndex; j++) {
                    Token candidate = tokenStream.get(j);
                    if (candidate.getType() == InterlisParser.Name
                            || candidate.getType() == InterlisParser.INTERLIS) {
                        names.add(candidate);
                    }
                }
                if (!names.isEmpty()) {
                    base = Optional.of(nameSyntax(names));
                }
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
            for (int i = eqIndex + 1; i <= endIndex; i++) {
                int tokenType = tokenStream.get(i).getType();
                if (tokenType == InterlisParser.CLASS) {
                    type = new GenericTypeSyntax(nextId(), tokenRange(tokenStream.get(i)), "CLASS");
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
                type = new UnsupportedTypeSyntax(nextId(),
                        tokenRange(tokenStream.get(eqIndex + 1)), "DOMAIN_MANDATORY_ONLY",
                        "domain without explicit value type");
                unsupportedFeatures.add("DOMAIN_MANDATORY_ONLY");
            } else {
                type = new UnsupportedTypeSyntax(nextId(), range(context),
                        "UNSUPPORTED_DOMAIN_TYPE", "unsupported domain type");
                unsupportedFeatures.add("UNSUPPORTED_DOMAIN_TYPE");
            }
        }
        List<ConstraintDeclarationSyntax> constraints = new ArrayList<>();
        boolean domainConstraints = false;
        for (int i = startIndex; i <= endIndex; i++) {
            if (tokenStream.get(i).getType() == InterlisParser.CONSTRAINTS) {
                domainConstraints = true;
                break;
            }
        }
        if (domainConstraints && type instanceof GenericTypeSyntax generic
                && (generic.name().equals("CLASS") || generic.name().equals("STRUCTURE"))) {
            unsupportedFeatures.add("DOMAIN_CONSTRAINT_CLASS");
        }
        SourceRange declarationRange = tokenRange(tokenStream.get(startIndex),
                tokenStream.get(endIndex));
        return new DomainDeclarationSyntax(nextId(), declarationRange, name,
                Optional.of(tokenRange(nameToken)), modifiers(context), base, type, constraints);
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
        if (!context.OID().isEmpty()) {
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
        return new AssociationDeclarationSyntax(nextId(), range(context), name, nameRange,
                endName, baseAssociation, modifiers(context), roles, attributes, constraints,
                oidRule(context));
    }

    private AttributeDeclarationSyntax buildAssociationAttribute(
            InterlisParser.RoleDefContext context) {
        Token nameToken = context.Name() == null ? null : context.Name().getSymbol();
        String name = nameToken == null ? "<missing-attribute>" : nameToken.getText();
        TypeResult type;
        if (context.attrTypeDef() != null
                && directModifiers(context).contains(Modifier.EXTENDED)
                && context.attrTypeDef().attrType() == null
                && context.attrTypeDef().enumeration() == null
                && context.attrTypeDef().numeric() == null
                && context.attrTypeDef().BAG() == null
                && context.attrTypeDef().LIST() == null) {
            type = new TypeResult(new GenericTypeSyntax(nextId(), range(context), "INHERITED"),
                    context.attrTypeDef().MANDATORY() != null);
        } else if (context.attrTypeDef() != null) {
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
                false, context.ASSIGN() != null, false, false, directModifiers(context), type.type);
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
                    optionalRange(nameToken, context), Cardinality.unboundedFrom(0), target,
                    modifiers(context), aggregation, false);
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
        NameSyntax target;
        if (!context.restrictedClassOrAssRef().isEmpty()) {
            target = nameFromContext(context.restrictedClassOrAssRef(0));
        } else {
            target = syntheticName("UNRESOLVED_ROLE_TARGET", range(context));
            unsupportedFeatures.add("MISSING_ROLE_TARGET");
        }
        return new RoleDeclarationSyntax(nextId(), range(context), name,
                optionalRange(nameToken, context), cardinality, target, modifiers(context),
                aggregation, explicitCardinality);
    }

    private ConstraintDeclarationSyntax buildConstraint(InterlisParser.ConstraintDefContext context) {
        if (context.mandatoryConstraint() != null) {
            InterlisParser.MandatoryConstraintContext mandatory = context.mandatoryConstraint();
            Optional<String> name = mandatory.Name() == null ? Optional.empty()
                    : Optional.of(mandatory.Name().getText());
            Optional<ExpressionSyntax> expression = mandatory.expression() == null ? Optional.empty()
                    : Optional.of(buildExpression(mandatory.expression()));
            return new ConstraintDeclarationSyntax(nextId(), range(context), name,
                    ConstraintKind.MANDATORY, expression, List.of(), Optional.empty(), false);
        }
        if (context.uniquenessConstraint() != null) {
            InterlisParser.UniquenessConstraintContext unique = context.uniquenessConstraint();
            Optional<String> name = unique.Name() == null ? Optional.empty()
                    : Optional.of(unique.Name().getText());
            Optional<ExpressionSyntax> where = unique.expression() == null ? Optional.empty()
                    : Optional.of(buildExpression(unique.expression()));
            List<NameSyntax> uniqueKeys = new ArrayList<>();
            for (InterlisParser.GlobalUniquenessContext global : unique.globalUniqueness()) {
                for (InterlisParser.UniqueElContext uniqueEl : global.uniqueEl()) {
                    Token token = firstMeaningfulToken(uniqueEl.objectOrAttributePath());
                    if (token != null) {
                        uniqueKeys.add(new NameSyntax(nextId(), tokenRange(token), List.of(
                                new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token)))));
                    }
                }
            }
            for (InterlisParser.LocalUniquenessContext local : unique.localUniqueness()) {
                for (TerminalNode nameNode : local.Name()) {
                    Token token = nameNode.getSymbol();
                    uniqueKeys.add(new NameSyntax(nextId(), tokenRange(token), List.of(
                            new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token)))));
                }
            }
            return new ConstraintDeclarationSyntax(nextId(), range(context), name,
                    ConstraintKind.UNIQUENESS, where, uniqueKeys, Optional.empty(),
                    !unique.localUniqueness().isEmpty()
                            || containsToken(unique, InterlisParser.LOCAL));
        }
        if (context.plausibilityConstraint() != null) {
            InterlisParser.PlausibilityConstraintContext plausibility = context.plausibilityConstraint();
            Optional<String> name = plausibility.Name() == null ? Optional.empty()
                    : Optional.of(plausibility.Name().getText());
            Optional<ExpressionSyntax> expression = plausibility.expression() == null
                    ? Optional.empty() : Optional.of(buildExpression(plausibility.expression()));
            return new ConstraintDeclarationSyntax(nextId(), range(context), name,
                    ConstraintKind.PLAUSIBILITY, expression, List.of(), Optional.empty(), false);
        }
        if (context.existenceConstraint() != null) {
            InterlisParser.ExistenceConstraintContext existence = context.existenceConstraint();
            NameSyntax source = existence.attributePath().isEmpty()
                    ? syntheticName("<missing-source>", range(existence))
                    : pathName(existence.attributePath(0).objectOrAttributePath());
            List<ExistenceConstraintSyntax.ExistenceTargetSyntax> targets = new ArrayList<>();
            for (int i = 0; i < existence.viewableRef().size(); i++) {
                NameSyntax viewable = nameFromContext(existence.viewableRef(i));
                NameSyntax path = i + 1 < existence.attributePath().size()
                        ? pathName(existence.attributePath(i + 1).objectOrAttributePath())
                        : syntheticName("<missing-path>", range(existence));
                targets.add(new ExistenceConstraintSyntax.ExistenceTargetSyntax(viewable, path));
            }
            return new ConstraintDeclarationSyntax(nextId(), range(context), Optional.empty(),
                    ConstraintKind.EXISTENCE, Optional.empty(), List.of(),
                    Optional.of(new ExistenceConstraintSyntax(nextId(), range(context), source, targets)),
                    false);
        }
        if (context.setConstraint() != null) {
            InterlisParser.SetConstraintContext set = context.setConstraint();
            Optional<String> name = set.Name() == null ? Optional.empty()
                    : Optional.of(set.Name().getText());
            Optional<ExpressionSyntax> condition = set.expression().isEmpty() ? Optional.empty()
                    : Optional.of(buildExpression(set.expression(set.expression().size() - 1)));
            return new ConstraintDeclarationSyntax(nextId(), range(context), name,
                    ConstraintKind.SET, condition, List.of(), Optional.empty(), false);
        }
        unsupportedFeatures.add("CONSTRAINT_UNKNOWN");
        return new ConstraintDeclarationSyntax(nextId(), range(context), Optional.empty(),
                ConstraintKind.MANDATORY, Optional.empty(), List.of(), Optional.empty(), false);
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
        UnsupportedTypeSyntax unsupported = new UnsupportedTypeSyntax(nextId(), range(context),
                "MISSING_ATTRIBUTE_TYPE", "missing attribute type");
        unsupportedFeatures.add("MISSING_ATTRIBUTE_TYPE");
        return new TypeResult(unsupported, mandatory);
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
            boolean external = context.referenceAttr().EXTERNAL() != null;
            var restricted = context.referenceAttr().restrictedClassOrAssRef();
            NameSyntax target = restricted.classOrAssociationRef().isEmpty()
                    ? nameFromContext(restricted)
                    : nameFromContext(restricted.classOrAssociationRef().getFirst());
            return new ReferenceTypeSyntax(nextId(), range(context), target, external);
        }
        if (context.restrictedStructureRef() != null) {
            InterlisParser.RestrictedStructureRefContext restricted = context.restrictedStructureRef();
            if (!restricted.structureRef().isEmpty()) {
                return new DomainReferenceSyntax(nextId(), range(context),
                        nameFromContext(restricted.structureRef().getFirst()));
            }
            if (restricted.type() != null) {
                return buildType(restricted.type());
            }
            if (restricted.referenceAttr() != null) {
                return new ReferenceTypeSyntax(nextId(), range(context),
                        nameFromContext(restricted.referenceAttr().restrictedClassOrAssRef()),
                        restricted.referenceAttr().EXTERNAL() != null);
            }
            unsupportedFeatures.add("ANYSTRUCTURE");
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
            return new TextTypeSyntax(nextId(), range(context),
                    guru.interlis.model.TextKind.TEXT, java.util.OptionalInt.empty());
        }
        unsupportedFeatures.add("UNSUPPORTED_TYPE");
        return unsupportedType(context, "UNSUPPORTED_TYPE", "unsupported type");
    }

    private TypeSyntax buildBaseType(InterlisParser.BaseTypeContext context) {
        if (context.textType() != null) {
            InterlisParser.TextTypeContext text = context.textType();
            TextKind kind;
            if (text.MTEXT() != null) {
                kind = TextKind.MTEXT;
            } else if (text.TEXT() != null) {
                kind = TextKind.TEXT;
            } else if (text.NAME() != null) {
                kind = TextKind.NAME;
            } else {
                kind = TextKind.URI;
            }
            OptionalInt length = OptionalInt.empty();
            if (text.PosNumber() != null) {
                length = OptionalInt.of(Integer.parseInt(text.PosNumber().getText()));
            }
            return new TextTypeSyntax(nextId(), range(context), kind, length);
        }
        if (context.booleanType() != null) {
            return new BooleanTypeSyntax(nextId(), range(context));
        }
        if (context.numericType() != null) {
            InterlisParser.NumericTypeContext numericType = context.numericType();
            if (numericType.numeric() != null) {
                return numericType(numericType.numeric());
            }
            return numericTypeUnbounded(context);
        }
        if (context.coordinateType() != null) {
            return coordinateType(context.coordinateType());
        }
        if (context.oIDType() != null) {
            InterlisParser.OIDTypeContext oid = context.oIDType();
            guru.interlis.model.OidKind kind = oid.UUIDOID() != null
                    ? guru.interlis.model.OidKind.UUID
                    : oid.ANY() != null ? guru.interlis.model.OidKind.ANY
                    : oid.NUMERIC() != null || oid.numeric() != null
                    ? guru.interlis.model.OidKind.NUMERIC
                    : guru.interlis.model.OidKind.TEXT;
            return new OidTypeSyntax(nextId(), range(oid), kind);
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
            List<String> restrictions = new ArrayList<>();
            for (InterlisParser.ViewableRefContext restriction : classType.viewableRef()) {
                restrictions.add(nameFromContext(restriction).text());
            }
            for (InterlisParser.ClassOrStructureRefContext restriction :
                    classType.classOrStructureRef()) {
                restrictions.add(nameFromContext(restriction).text());
            }
            return new ClassValueTypeSyntax(nextId(), range(classType),
                    classType.STRUCTURE() != null, restrictions);
        }
        if (context.formattedType() != null) {
            InterlisParser.FormattedTypeContext formatted = context.formattedType();
            String formatName;
            if (formatted.INTERLIS() != null && formatted.Name() != null) {
                formatName = "INTERLIS." + formatted.Name().getText();
            } else if (formatted.domainRef() != null) {
                formatName = nameFromContext(formatted.domainRef()).text();
            } else {
                formatName = source.text().substring(formatted.getStart().getStartIndex(),
                        formatted.getStop().getStopIndex() + 1).trim();
            }
            Optional<String> minimum = formatted.STRING().isEmpty() ? Optional.empty()
                    : Optional.of(unquote(formatted.STRING(0).getText()));
            Optional<String> maximum = formatted.STRING().size() < 2 ? Optional.empty()
                    : Optional.of(unquote(formatted.STRING(1).getText()));
            return new FormattedTypeSyntax(nextId(), range(formatted), formatName, minimum, maximum);
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
            return new GenericTypeSyntax(nextId(), range(context), "ATTRIBUTE");
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
        Optional<String> lineForm = Optional.empty();
        if (context.lineForm() != null && !context.lineForm().lineFormType().isEmpty()) {
            List<String> forms = new ArrayList<>();
            for (InterlisParser.LineFormTypeContext form : context.lineForm().lineFormType()) {
                forms.add(source.text().substring(form.getStart().getStartIndex(),
                        form.getStop().getStopIndex() + 1).trim());
            }
            lineForm = Optional.of(String.join(",", forms));
        }
        Optional<NameSyntax> vertexDomain = Optional.empty();
        if (context.controlPoints() != null && !context.controlPoints().Name().isEmpty()) {
            List<Token> names = context.controlPoints().Name().stream()
                    .map(TerminalNode::getSymbol).toList();
            vertexDomain = Optional.of(nameSyntax(names));
        }
        Optional<String> lineAttributes = context.LINE() != null && context.Name() != null
                ? Optional.of(context.Name().getText()) : Optional.empty();
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
        return new LineTypeSyntax(nextId(), range(context), kind, lineForm, vertexDomain,
                lineAttributes, overlap);
    }

    private CoordinateTypeSyntax coordinateType(InterlisParser.CoordinateTypeContext context) {
        boolean multi = context.MULTICOORD() != null;
        List<NumericRange> axes = new ArrayList<>();
        for (InterlisParser.NumericContext numeric : context.numeric()) {
            axes.add(numericRange(numeric));
        }
        for (InterlisParser.NumericTypeContext numericType : context.numericType()) {
            if (numericType.numeric() != null) {
                axes.add(numericRange(numericType.numeric()));
            } else {
                axes.add(NumericRange.unbounded());
            }
        }
        if (axes.isEmpty()) {
            unsupportedFeatures.add("COORDINATE_WITHOUT_AXES");
            axes.add(NumericRange.unbounded());
        }
        if (axes.size() > 3) {
            unsupportedFeatures.add("COORDINATE_TOO_MANY_AXES");
        }
        List<NumericRange> limited = axes.size() > 3 ? axes.subList(0, 3) : axes;
        Optional<String> referenceSystem = context.REFSYS() != null && context.STRING() != null
                ? Optional.of(unquote(context.STRING().getText())) : Optional.empty();
        return new CoordinateTypeSyntax(nextId(), range(context), limited.size(), limited, multi,
                referenceSystem);
    }

    private NumericTypeSyntax numericType(InterlisParser.NumericContext context) {
        NumericRange range = numericRange(context);
        Optional<NameSyntax> unit = context.unitRef() == null ? Optional.empty()
                : Optional.of(nameFromContext(context.unitRef()));
        return new NumericTypeSyntax(nextId(), range(context), range.minimum(), range.maximum(),
                range.circular(), range.direction(), unit);
    }

    private NumericTypeSyntax numericTypeUnbounded(ParserRuleContext context) {
        return new NumericTypeSyntax(nextId(), range(context), Optional.empty(), Optional.empty(),
                false, NumericDirection.NONE, Optional.empty());
    }

    private NumericRange numericRange(InterlisParser.NumericContext context) {
        List<Token> numbers = numericTokens(context);
        Optional<DecimalLiteral> minimum = Optional.empty();
        Optional<DecimalLiteral> maximum = Optional.empty();
        if (!numbers.isEmpty()) {
            minimum = decimal(numbers.getFirst().getText());
        }
        if (numbers.size() > 1) {
            maximum = decimal(numbers.get(1).getText());
        }
        NumericDirection direction = context.CLOCKWISE() != null ? NumericDirection.CLOCKWISE
                : context.COUNTERCLOCKWISE() != null ? NumericDirection.COUNTERCLOCKWISE
                : NumericDirection.NONE;
        boolean circular = context.CIRCULAR() != null;
        return new NumericRange(minimum, maximum, circular, direction, Optional.empty(),
                Optional.empty());
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
        boolean ordered = context.ORDERED() != null;
        boolean circular = context.CIRCULAR() != null;
        if (!ordered && context.getStop() != null) {
            for (int i = context.getStop().getTokenIndex() + 1; i < tokenStream.size(); i++) {
                Token token = tokenStream.get(i);
                if (token.getType() == InterlisParser.SEMI) {
                    break;
                }
                if (token.getType() == InterlisParser.ORDERED) {
                    ordered = true;
                    break;
                }
                if (token.getType() == InterlisParser.CIRCULAR) {
                    circular = true;
                }
            }
        }
        return new EnumerationTypeSyntax(nextId(), range(context), values, ordered, circular);
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
        values.add(new EnumerationTypeSyntax.EnumerationValueSyntax(path, tokenRange(first), nested));
        if (context.enumeration() != null) {
            for (InterlisParser.EnumElementContext child : context.enumeration().enumElement()) {
                collectEnumValues(child, path, values);
            }
        }
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
        } else if (!restricted.structureRef().isEmpty()) {
            element = new DomainReferenceSyntax(nextId(), range(restricted),
                    nameFromContext(restricted.structureRef().getFirst()));
        } else if (restricted.type() != null) {
            element = buildType(restricted.type());
        } else if (restricted.referenceAttr() != null) {
            element = new ReferenceTypeSyntax(nextId(), range(restricted),
                    nameFromContext(restricted.referenceAttr().restrictedClassOrAssRef()),
                    restricted.referenceAttr().EXTERNAL() != null);
        } else if (restricted.enumeration() != null) {
            if (language == LanguageVersion.ILI23) {
                unsupportedFeatures.add("ENUM_COLLECTION_ILI23");
            }
            element = enumerationType(restricted.enumeration());
        } else {
            element = new UnsupportedTypeSyntax(nextId(), range(restricted),
                    "ANYSTRUCTURE", "ANYSTRUCTURE element");
            unsupportedFeatures.add("ANYSTRUCTURE");
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
            BinaryOperator operator = relationOperator(context.relation().getStart().getType());
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

    private ExpressionSyntax buildFactor(InterlisParser.FactorContext context) {
        if (context.objectOrAttributePath() != null) {
            return pathExpression(context.objectOrAttributePath());
        }
        if (context.constant() != null) {
            return constantExpression(context.constant());
        }
        if (context.Number() != null) {
            Token token = context.Number().getSymbol();
            return new LiteralExpressionSyntax(nextId(), range(context), LiteralKind.NUMBER,
                    token.getText(), decimal(token.getText()));
        }
        if (context.functionCall() != null) {
            InterlisParser.FunctionCallContext call = context.functionCall();
            List<ExpressionSyntax> arguments = new ArrayList<>();
            for (InterlisParser.ArgumentContext argument : call.argument()) {
                if (argument.expression() != null) {
                    arguments.add(buildExpression(argument.expression()));
                } else {
                    unsupportedFeatures.add("FUNCTION_CALL_ARGUMENT");
                }
            }
            String name = call.Name().isEmpty() ? "<missing>"
                    : call.Name(call.Name().size() - 1).getText();
            return new FunctionCallExpressionSyntax(nextId(), range(context), name, arguments);
        }
        if (context.ALL() != null) {
            return new GenericConstantExpressionSyntax(nextId(), range(context), "ALL");
        }
        if (context.INTERLIS() != null) {
            List<ExpressionSyntax> arguments = new ArrayList<>();
            for (InterlisParser.ArgumentContext argument : context.argument()) {
                if (argument.expression() != null) {
                    arguments.add(buildExpression(argument.expression()));
                } else {
                    unsupportedFeatures.add("BUILTIN_ARGUMENT");
                }
            }
            String name = context.Name().isEmpty()
                    ? context.URI() != null ? context.URI().getText() : "builtin"
                    : context.Name(context.Name().size() - 1).getText();
            return new BuiltinFunctionExpressionSyntax(nextId(), range(context), name, arguments);
        }
        if (context.PARAMETER() != null) {
            return new GenericConstantExpressionSyntax(nextId(), range(context), "PARAMETER");
        }
        if (context.inspection() != null) {
            unsupportedFeatures.add("INSPECTION");
            return unsupportedExpression(context, "INSPECTION", "inspection expression");
        }
        unsupportedFeatures.add("UNSUPPORTED_EXPRESSION");
        return unsupportedExpression(context, "UNSUPPORTED_EXPRESSION", "unsupported expression");
    }

    private ExpressionSyntax constantExpression(InterlisParser.ConstantContext context) {
        if (context.UNDEFINED() != null) {
            return new UndefinedExpressionSyntax(nextId(), range(context));
        }
        if (context.textConst() != null) {
            String lexeme = context.textConst().getText();
            return new LiteralExpressionSyntax(nextId(), range(context), LiteralKind.TEXT,
                    lexeme, Optional.empty());
        }
        if (context.numericConst() != null) {
            InterlisParser.NumericConstContext numeric = context.numericConst();
            String text = numeric.getText();
            if ("PI".equals(text) || "LNBASE".equals(text)) {
                return new ConstantExpressionSyntax(nextId(), range(context), text);
            }
            if (numeric.decConst() != null && numeric.decConst().getStart() != null) {
                String lexeme = numeric.decConst().getText();
                return new LiteralExpressionSyntax(nextId(), range(context), LiteralKind.NUMBER,
                        lexeme, decimal(lexeme));
            }
            unsupportedFeatures.add("NUMERIC_CONSTANT");
            return unsupportedExpression(context, "NUMERIC_CONSTANT", "numeric constant");
        }
        if (context.classConst() != null) {
            return new ClassConstantExpressionSyntax(nextId(), range(context),
                    nameFromContext(context.classConst().viewableRef()));
        }
        if (context.attributePathConst() != null) {
            return new GenericConstantExpressionSyntax(nextId(), range(context), "ATTRIBUTE");
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

    private NameSyntax pathName(InterlisParser.ObjectOrAttributePathContext context) {
        List<NameSyntax.NameSegmentSyntax> segments = new ArrayList<>();
        for (InterlisParser.PathElContext element : context.pathEl()) {
            Token token = firstMeaningfulToken(element);
            if (token != null) {
                segments.add(new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token)));
            }
        }
        return segments.isEmpty() ? syntheticName("<missing-path>", range(context))
                : new NameSyntax(nextId(), range(context), segments);
    }

    private ExpressionSyntax pathExpression(InterlisParser.ObjectOrAttributePathContext context) {
        List<NameSyntax> segments = new ArrayList<>();
        for (InterlisParser.PathElContext element : context.pathEl()) {
            Token token = firstMeaningfulToken(element);
            if (token == null) {
                continue;
            }
            if (element.LSBR() != null && element.Name() != null) {
                // Name[index] selection is not in the P1 slice.
                unsupportedFeatures.add("PATH_INDEX");
            }
            segments.add(new NameSyntax(nextId(), range(element), List.of(
                    new NameSyntax.NameSegmentSyntax(token.getText(), tokenRange(token)))));
        }
        if (segments.isEmpty()) {
            unsupportedFeatures.add("EMPTY_PATH");
            return unsupportedExpression(context, "EMPTY_PATH", "empty object path");
        }
        return new PathExpressionSyntax(nextId(), range(context), segments);
    }

    private Token firstMeaningfulToken(ParserRuleContext context) {
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
            if (type == InterlisParser.Name || type == InterlisParser.INTERLIS) {
                target.add(terminal.getSymbol());
            }
            return;
        }
        if (tree instanceof ParserRuleContext rule) {
            for (ParseTree child : rule.children) {
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
        Set<Modifier> result = directModifiers(context);
        if (result.contains(Modifier.OID)) {
        }
        return result;
    }

    private Set<Modifier> directModifiers(ParserRuleContext context) {
        Set<Modifier> result = new LinkedHashSet<>();
        for (ParseTree child : context.children) {
            if (!(child instanceof TerminalNode terminal)) {
                continue;
            }
            switch (terminal.getSymbol().getType()) {
                case InterlisParser.ABSTRACT -> result.add(Modifier.ABSTRACT);
                case InterlisParser.EXTENDED -> result.add(Modifier.EXTENDED);
                case InterlisParser.FINAL -> result.add(Modifier.FINAL);
                case InterlisParser.TRANSIENT -> result.add(Modifier.TRANSIENT);
                case InterlisParser.MANDATORY -> result.add(Modifier.MANDATORY);
                case InterlisParser.ORDERED -> result.add(Modifier.ORDERED);
                case InterlisParser.EXTERNAL -> result.add(Modifier.EXTERNAL);
                case InterlisParser.HIDING -> result.add(Modifier.HIDING);
                case InterlisParser.GENERIC -> result.add(Modifier.GENERIC);
                case InterlisParser.VIEW -> result.add(Modifier.VIEW);
                case InterlisParser.OID -> result.add(Modifier.OID);
                case InterlisParser.DERIVED -> result.add(Modifier.DERIVED);
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

    private Optional<DecimalLiteral> decimal(String lexeme) {
        try {
            return Optional.of(DecimalLiteral.parse(lexeme));
        } catch (RuntimeException e) {
            unsupportedFeatures.add("NUMERIC_LITERAL");
            return Optional.empty();
        }
    }

    private String unquote(String text) {
        if (text.length() < 2 || text.charAt(0) != '"') {
            return text;
        }
        String body = text.substring(1, text.length() - 1);
        StringBuilder result = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(++i);
                switch (next) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case 'u' -> {
                        if (i + 4 < body.length()) {
                            result.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> result.append(next);
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private Optional<SourceRange> optionalRange(Token token, ParserRuleContext context) {
        return token == null ? Optional.empty() : Optional.of(tokenRange(token));
    }

    private SyntaxNodeId nextId() {
        return new SyntaxNodeId("syntax-" + (++nodeCounter));
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
