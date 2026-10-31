package guru.interlis.compiler.internal.antlr;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.compiler.internal.AnalysisBudgetExceededException;
import guru.interlis.compiler.internal.DiagnosticCollector;
import guru.interlis.compiler.internal.Fingerprints;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCatalog;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.DiagnosticIds;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.ContentFingerprint;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.DetectionState;
import guru.interlis.syntax.DocumentSyntax;
import guru.interlis.syntax.FragmentKind;
import guru.interlis.syntax.FragmentSnapshot;
import guru.interlis.syntax.LanguageDetection;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxCoverage;
import guru.interlis.syntax.SyntaxFingerprint;
import guru.interlis.syntax.SyntaxNodeId;
import guru.interlis.syntax.SyntaxService;
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.syntax.SyntaxToken;
import guru.interlis.syntax.TokenKind;
import guru.interlis.syntax.TokenOrigin;
import guru.interlis.syntax.TriviaKind;
import guru.interlis.syntax.TriviaSpan;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** ANTLR-backed syntax service; all ANTLR objects stay internal. */
public final class AntlrSyntaxService implements SyntaxService {
    private static final DiagnosticCatalog CATALOG = DiagnosticCatalog.initial();

    @Override
    public SyntaxSnapshot parse(SourceText source, ParseOptions options,
                                CancellationToken cancellation) {
        try { return parseDocument(source,options,cancellation); }
        catch(AnalysisBudgetExceededException limit) {
            throw new guru.interlis.execution.OperationCancelledException(guru.interlis.execution.CancellationReason.RESOURCE_LIMIT);
        }
    }

    private SyntaxSnapshot parseDocument(SourceText source,ParseOptions options,CancellationToken cancellation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        cancellation.throwIfCancellationRequested();
        AnalysisBudget budget = options.budget();
        if (source.length() > budget.maxSourceUtf16Length()) {
            throw new AnalysisBudgetExceededException("maxSourceUtf16Length",
                    budget.maxSourceUtf16Length());
        }
        DiagnosticCollector collector = new DiagnosticCollector(CATALOG, budget.maxDiagnostics());
        AntlrOffsetMap offsets = new AntlrOffsetMap(source);
        LanguageDetection detection = new VersionHeaderScanner(source).detect();
        LanguageDetection effectiveDetection = detection;
        if (detection.state() == DetectionState.ABSENT && options.expectedVersion().isPresent()) {
            effectiveDetection = new LanguageDetection(options.expectedVersion(),
                    java.util.Optional.empty(), DetectionState.ASSUMED_FOR_RECOVERY,
                    java.util.Optional.empty());
        }
        if (detection.state() == DetectionState.UNSUPPORTED) {
            collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0005"),
                            Severity.ERROR, Phase.PARSING, "diagnostic.syntax.version")
                    .at(detection.headerRange().orElseGet(() -> source.range(0, 0)))
                    .argument(new DiagnosticArgument.TextArgument("actual",
                            detection.rawVersion().orElse("<unknown>")))
                    .argument(new DiagnosticArgument.IdentifierListArgument("supported",
                            List.of("2.3", "2.4"))));
        }
        if (options.expectedVersion().isPresent() && detection.version().isPresent()
                && !options.expectedVersion().get().equals(detection.version().get())) {
            collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0006"),
                            Severity.ERROR, Phase.PARSING, "diagnostic.syntax.versionConstruct")
                    .at(detection.headerRange().orElseGet(() -> source.range(0, 0)))
                    .argument(new DiagnosticArgument.TextArgument("construct", "language version"))
                    .argument(new DiagnosticArgument.TextArgument("language",
                            detection.rawVersion().orElse("<unknown>"))));
        }

        var charStream = CharStreams.fromString(source.text());
        InterlisLexer lexer = new InterlisLexer(charStream);
        lexer.removeErrorListeners();
        var lexerListener = new CollectingLexerErrorListener(source, offsets, collector);
        lexer.addErrorListener(lexerListener);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        if(tokens.size()>budget.maxTokens())throw new AnalysisBudgetExceededException("maxTokens",budget.maxTokens());

        LanguageVersion profileVersion = effectiveDetection.version().orElse(LanguageVersion.ILI23);
        checkTokenProfile(tokens,profileVersion,offsets,collector);

        InterlisParser parser = new InterlisParser(tokens);
        parser.removeErrorListeners();
        var parserListener = new CollectingParserErrorListener(offsets, collector);
        parser.addErrorListener(parserListener);
        var parseBudget=new ParseBudget(budget,cancellation);
        parser.addParseListener(parseBudget);parser.addErrorListener(parseBudget);

        InterlisParser.Interlis2defContext tree = null;
        AnalysisBudgetExceededException parseLimit = null;
        try {
            tree = parser.interlis2def();
        } catch (AnalysisBudgetExceededException limit) {
            parseLimit = limit;
        }
        cancellation.throwIfCancellationRequested();

        boolean consumed = tokenAt(tokens) == Token.EOF;
        // The start rule is also used without EOF; the syntax service owns trailing-input diagnostics.
        if (!consumed && tree != null) {
            Token next = tokens.LT(1);
            if (next != null && next.getType() != Token.EOF) {
                collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0004"),
                                Severity.ERROR, Phase.PARSING, "diagnostic.syntax.trailing")
                        .at(offsets.tokenRange(next))
                        .argument(new DiagnosticArgument.TokenArgument("actual",
                                safeTokenText(next))));
            }
        }
        LanguageVersion language = effectiveDetection.version().orElse(LanguageVersion.ILI23);
        SyntaxTreeBuilder builder = new SyntaxTreeBuilder(source, offsets, tokens, language, options.budget());
        DocumentSyntax document;
        if(parseLimit!=null) {
            document=new DocumentSyntax(new SyntaxNodeId("syntax-0"),source.range(source.length(),source.length()),List.of());
            return snapshotWithBudget(source,options,effectiveDetection,tokens,offsets,collector,document,builder,parseLimit);
        }
        try {
            document = builder.build(tree);
        } catch (AnalysisBudgetExceededException budgetError) {
            document = new DocumentSyntax(new SyntaxNodeId("syntax-0"),
                    source.range(source.length(), source.length()), List.of());
            return snapshotWithBudget(source, options, effectiveDetection, tokens, offsets,
                    collector, document, builder, budgetError);
        }
        collectVersionViolations(builder, language, collector);
        var metadata=SourceAnnotations.read(source,tokens,offsets,collector,cancellation);
        List<Diagnostic> diagnostics = collector.freeze();
        SyntaxCoverage coverage = new SyntaxCoverage(consumed,
                diagnostics.stream().noneMatch(d -> d.code().value().equals("ILI-SYN-0001")),
                tree != null && diagnostics.stream().noneMatch(d -> d.code().value().startsWith("ILI-SYN-0002")
                        || d.code().value().startsWith("ILI-SYN-0003")
                        || d.code().value().startsWith("ILI-SYN-0004")),
                builder.recoveredRanges(), builder.unsupportedFeatures(), false, false);
        return new SyntaxSnapshot(source, options, effectiveDetection, document,
                buildTokens(source, offsets, tokens), buildTrivia(source, offsets, tokens),
                diagnostics, coverage, fingerprint(source, options, effectiveDetection), metadata);
    }

    private static void collectVersionViolations(SyntaxTreeBuilder builder, LanguageVersion language,
                                                 DiagnosticCollector collector) {
        builder.propertyDiagnostics().forEach(collector::emit);
        for (int i = 0; i < builder.versionViolations().size(); i++) {
            LanguageProfile.Feature feature = builder.versionViolations().get(i);
            collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0006"),
                            Severity.ERROR, Phase.PARSING, "diagnostic.syntax.versionConstruct")
                    .at(builder.versionViolationRanges().get(i))
                    .argument(new DiagnosticArgument.TextArgument("construct", feature.label()))
                    .argument(new DiagnosticArgument.TextArgument("language", language.literal())));
        }
    }

    private static int tokenAt(CommonTokenStream tokens) {
        Token token = tokens.LT(1);
        return token == null ? Token.EOF : token.getType();
    }

    private SyntaxSnapshot snapshotWithBudget(SourceText source, ParseOptions options,
                                              LanguageDetection detection,
                                              CommonTokenStream tokens, AntlrOffsetMap offsets,
                                              DiagnosticCollector collector,
                                              DocumentSyntax document,
                                              SyntaxTreeBuilder builder,
                                              AnalysisBudgetExceededException budgetError) {
        DiagnosticId id = DiagnosticIds.create(new DiagnosticCode("ILI-INF-0002"),
                Phase.INFRASTRUCTURE, java.util.Optional.empty(), "diagnostic.limit",
                List.of(new DiagnosticArgument.TextArgument("resource", budgetError.resource()),
                        new DiagnosticArgument.IntegerArgument("limit",
                                java.math.BigInteger.valueOf(budgetError.limit()))));
        Diagnostic diagnostic = new Diagnostic(id, new DiagnosticCode("ILI-INF-0002"),
                Severity.ERROR, Phase.INFRASTRUCTURE, "diagnostic.limit",
                List.of(new DiagnosticArgument.TextArgument("resource", budgetError.resource()),
                        new DiagnosticArgument.IntegerArgument("limit",
                                java.math.BigInteger.valueOf(budgetError.limit()))),
                java.util.Optional.empty(), List.of(), java.util.Optional.empty(), List.of(),
                java.util.Optional.empty(), java.util.Optional.empty(), List.of());
        SyntaxCoverage coverage = new SyntaxCoverage(false, false, false,
                builder.recoveredRanges(), builder.unsupportedFeatures(), true, false);
        return new SyntaxSnapshot(source, options, detection, document,
                buildTokens(source, offsets, tokens), buildTrivia(source, offsets, tokens),
                List.of(diagnostic), coverage, fingerprint(source, options, detection),java.util.Map.of());
    }

    private static List<SyntaxToken> buildTokens(SourceText source, AntlrOffsetMap offsets,
                                                 CommonTokenStream tokens) {
        List<SyntaxToken> result = new ArrayList<>();
        long counter = 0;
        for (Token token : tokens.getTokens()) {
            SourceRange range = offsets.tokenRange(token);
            int channel = token.getChannel();
            TokenKind kind = tokenKind(token);
            result.add(new SyntaxToken(new SyntaxNodeId("token-" + (++counter)), kind, range,
                    channel, TokenOrigin.SOURCE, token.getText() == null ? "" : token.getText()));
        }
        return result;
    }

    private static TokenKind tokenKind(Token token) {
        if (token.getType() == Token.EOF) {
            return TokenKind.EOF;
        }
        if (token.getType() == InterlisLexer.Name) {
            return TokenKind.IDENTIFIER;
        }
        if (token.getType() == InterlisLexer.STRING) {
            return TokenKind.STRING;
        }
        if (token.getType() == InterlisLexer.Number || token.getType() == InterlisLexer.PosNumber
                || token.getType() == InterlisLexer.Dec || token.getType() == InterlisLexer.Float) {
            return TokenKind.NUMBER;
        }
        String text = token.getText();
        if (text != null && !text.isEmpty()) {
            // The same ASCII categories as before, with constant stack usage.
            // Hidden-channel separator comments can contain thousands of glyphs.
            boolean keyword = text.charAt(0) >= 'A' && text.charAt(0) <= 'Z';
            boolean punctuation = true;
            for (int index = 0; index < text.length() && (keyword || punctuation); index++) {
                char value = text.charAt(index);
                keyword &= value >= 'A' && value <= 'Z' || index > 0
                        && (value >= '0' && value <= '9' || value == '_');
                punctuation &= value >= '!' && value <= '/' || value >= ':' && value <= '@'
                        || value >= '[' && value <= '`' || value >= '{' && value <= '~';
            }
            if (keyword) return TokenKind.KEYWORD;
            if (punctuation) return TokenKind.PUNCTUATION;
        }
        return TokenKind.OTHER;
    }

    private static List<TriviaSpan> buildTrivia(SourceText source, AntlrOffsetMap offsets,
                                                CommonTokenStream tokens) {
        List<TriviaSpan> result = new ArrayList<>();
        int cursor = 0;
        for (Token token : tokens.getTokens()) {
            SourceRange range = offsets.tokenRange(token);
            if (range.startOffset() > cursor) {
                result.add(new TriviaSpan(TriviaKind.WHITESPACE_GAP,
                        source.range(cursor, range.startOffset()),
                        source.text().substring(cursor, range.startOffset())));
            }
            String text = token.getText();
            if (text != null && token.getChannel() != Token.DEFAULT_CHANNEL
                    && (text.startsWith("!!") || text.startsWith("/*"))) {
                TriviaKind kind = text.startsWith("!!") ? TriviaKind.DOCUMENTATION_COMMENT
                        : TriviaKind.COMMENT;
                result.add(new TriviaSpan(kind, range, text));
            }
            cursor = Math.max(cursor, range.endOffset());
        }
        if (cursor < source.length()) {
            result.add(new TriviaSpan(TriviaKind.WHITESPACE_GAP,
                    source.range(cursor, source.length()),
                    source.text().substring(cursor)));
        }
        return result;
    }

    private static SyntaxFingerprint fingerprint(SourceText source, ParseOptions options,
                                                 LanguageDetection detection) {
        ContentFingerprint content = source.contentFingerprint();
        String budget = options.budget().toString();
        return new SyntaxFingerprint("SHA-256/syntax-v1",
                Fingerprints.syntax(content.value(), options.mode().name(),
                        detection.version().map(LanguageVersion::literal).orElse("unknown"), budget));
    }

    @Override
    public FragmentSnapshot parseFragment(SourceText source, SourceRange fragment,
                                          FragmentKind kind, LanguageVersion languageVersion,
                                          CancellationToken cancellation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fragment, "fragment");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(languageVersion, "languageVersion");
        Objects.requireNonNull(cancellation, "cancellation");
        cancellation.throwIfCancellationRequested();
        if (!source.id().equals(fragment.source())
                || !source.revision().equals(fragment.revision())) {
            throw new IllegalArgumentException("fragment belongs to another source revision");
        }
        return parseMappedFragment(source,fragment,kind,languageVersion,cancellation,source.slice(fragment),n->fragment.startOffset()+n);
    }

    /** Internal typed-path consumer; source IDs/revisions and escape boundaries
     * refer to the original string token, not to a fabricated source document. */
    public FragmentSnapshot parsePathLiteral(SourceText source,SourceRange literal,LanguageVersion language,CancellationToken cancellation) {
        var decoded=guru.interlis.compiler.internal.InterlisStrings.decode(source.slice(literal));
        SourceRange content=source.range(literal.startOffset()+1,literal.endOffset()-1);
        return parseMappedFragment(source,content,FragmentKind.OBJECT_PATH,language,cancellation,decoded.text(),n->literal.startOffset()+decoded.rawOffset(n));
    }

    private FragmentSnapshot parseMappedFragment(SourceText source,SourceRange fragment,FragmentKind kind,
            LanguageVersion languageVersion,CancellationToken cancellation,String fragmentText,java.util.function.IntUnaryOperator originalOffset) {
        cancellation.throwIfCancellationRequested();
        DiagnosticCollector collector = new DiagnosticCollector(CATALOG,
                AnalysisBudget.defaults().maxDiagnostics());
        AntlrOffsetMap offsets = new AntlrOffsetMap(source,fragmentText,originalOffset);
        var charStream = CharStreams.fromString(fragmentText);
        InterlisLexer lexer = new InterlisLexer(charStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new CollectingLexerErrorListener(source,offsets,collector));
        CommonTokenStream fragmentTokens=new CommonTokenStream(lexer);
        fragmentTokens.fill();
        checkTokenProfile(fragmentTokens,languageVersion,offsets,collector);
        InterlisParser parser = new InterlisParser(fragmentTokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new CollectingParserErrorListener(offsets, collector));
        var fragmentBudget=new ParseBudget(AnalysisBudget.defaults(),cancellation);
        parser.addParseListener(fragmentBudget);parser.addErrorListener(fragmentBudget);
        org.antlr.v4.runtime.ParserRuleContext context;
        AnalysisBudgetExceededException fragmentLimit=null;
        try {
            context = switch (kind) {
                case EXPRESSION -> parser.expression();
                case OBJECT_PATH -> parser.objectOrAttributePath();
                case ATTRIBUTE_DECLARATION -> parser.attributeDef();
            };
        } catch (AnalysisBudgetExceededException limit) {
            context=null;fragmentLimit=limit;
        }
        cancellation.throwIfCancellationRequested();
        boolean consumed = fragmentTokens.LA(1) == Token.EOF;
        if (!consumed) {
            Token next = fragmentTokens.LT(1);
            if (next != null && next.getType() != Token.EOF) {
                collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0004"),
                                Severity.ERROR, Phase.PARSING, "diagnostic.syntax.trailing")
                        .at(offsets.tokenRange(next))
                        .argument(new DiagnosticArgument.TokenArgument("actual",
                                safeTokenText(next))));
            }
        }
        SyntaxTreeBuilder builder = new SyntaxTreeBuilder(source, offsets, fragmentTokens,
                languageVersion, AnalysisBudget.defaults());
        guru.interlis.syntax.SyntaxNode root;
        if(fragmentLimit!=null) {
            collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-INF-0002"),Severity.ERROR,Phase.INFRASTRUCTURE,"diagnostic.limit")
                    .argument(new DiagnosticArgument.TextArgument("resource",fragmentLimit.resource()))
                    .argument(new DiagnosticArgument.IntegerArgument("limit",java.math.BigInteger.valueOf(fragmentLimit.limit()))));
            root=kind==FragmentKind.ATTRIBUTE_DECLARATION
                    ? new guru.interlis.syntax.UnsupportedDeclarationSyntax(new SyntaxNodeId("fragment-root"),fragment,"RESOURCE_LIMIT",fragmentLimit.resource())
                    : new guru.interlis.syntax.UnsupportedExpressionSyntax(new SyntaxNodeId("fragment-root"),fragment,"RESOURCE_LIMIT",fragmentLimit.resource());
            consumed=false;
        } else if (context == null) {
            SourceRange range = fragment;
            collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0002"),
                            Severity.ERROR, Phase.PARSING, "diagnostic.syntax.unexpected")
                    .at(range)
                    .argument(new DiagnosticArgument.TokenArgument("actual", "<invalid-fragment>"))
                    .argument(new DiagnosticArgument.IdentifierListArgument("expected",
                            List.of(kind.name()))));
            root = switch (kind) {
                case EXPRESSION, OBJECT_PATH -> new guru.interlis.syntax.UnsupportedExpressionSyntax(
                        new SyntaxNodeId("fragment-root"), range, "FRAGMENT",
                        "fragment could not be parsed");
                case ATTRIBUTE_DECLARATION -> new guru.interlis.syntax.UnsupportedDeclarationSyntax(
                        new SyntaxNodeId("fragment-root"), range, "FRAGMENT",
                        "fragment could not be parsed");
            };
        } else try {
            root = switch (kind) {
                case EXPRESSION -> builder.buildExpressionFragment(
                        (InterlisParser.ExpressionContext) context);
                case OBJECT_PATH -> builder.buildPathFragment(
                        (InterlisParser.ObjectOrAttributePathContext) context);
                case ATTRIBUTE_DECLARATION -> builder.buildAttributeFragment(
                        (InterlisParser.AttributeDefContext) context);
            };
        }
        catch(AnalysisBudgetExceededException limit) {
            collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-INF-0002"),Severity.ERROR,Phase.INFRASTRUCTURE,"diagnostic.limit")
                    .argument(new DiagnosticArgument.TextArgument("resource",limit.resource()))
                    .argument(new DiagnosticArgument.IntegerArgument("limit",java.math.BigInteger.valueOf(limit.limit()))));
            root=kind==FragmentKind.ATTRIBUTE_DECLARATION
                    ? new guru.interlis.syntax.UnsupportedDeclarationSyntax(new SyntaxNodeId("fragment-root"),fragment,"RESOURCE_LIMIT",limit.resource())
                    : new guru.interlis.syntax.UnsupportedExpressionSyntax(new SyntaxNodeId("fragment-root"),fragment,"RESOURCE_LIMIT",limit.resource());
            consumed=false;
        }
        collectVersionViolations(builder, languageVersion, collector);
        return new FragmentSnapshot(source, kind, fragment, root, consumed,
                collector.freeze(), languageVersion);
    }

    private static void checkTokenProfile(CommonTokenStream tokens,LanguageVersion language,AntlrOffsetMap offsets,DiagnosticCollector collector) {
        LanguageProfile.classify(tokens,language);
        for (Token token : tokens.getTokens()) {
            LanguageProfile.featureForToken(token.getType()).ifPresent(feature -> {
                if (!LanguageProfile.supports(language, feature)) {
                    collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0006"),
                                    Severity.ERROR, Phase.PARSING,
                                    "diagnostic.syntax.versionConstruct")
                            .at(offsets.tokenRange(token))
                            .argument(new DiagnosticArgument.TextArgument("construct",
                                    feature.label()))
                            .argument(new DiagnosticArgument.TextArgument("language",
                                    language.literal())));
                }
            });
        }

    }



    private static String safeTokenText(Token token) {
        String text = token.getText();
        if (text == null) {
            return "<eof>";
        }
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }
}
