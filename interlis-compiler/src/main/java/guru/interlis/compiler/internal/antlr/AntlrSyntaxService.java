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

        if (effectiveDetection.version().orElse(null) == LanguageVersion.ILI23) {
            for (Token token : tokens.getTokens()) {
                if (token.getType() == InterlisLexer.MULTICOORD) {
                    collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0006"),
                                    Severity.ERROR, Phase.PARSING,
                                    "diagnostic.syntax.versionConstruct")
                            .at(offsets.tokenRange(token))
                            .argument(new DiagnosticArgument.TextArgument("construct", "MULTICOORD"))
                            .argument(new DiagnosticArgument.TextArgument("language", "2.3")));
                }
            }
        }

        InterlisParser parser = new InterlisParser(tokens);
        parser.removeErrorListeners();
        var parserListener = new CollectingParserErrorListener(offsets, collector);
        parser.addErrorListener(parserListener);

        InterlisParser.Interlis2defContext tree = null;
        String failure = null;
        try {
            tree = parser.interlis2def();
        } catch (RuntimeException e) {
            failure = e.getClass().getSimpleName() + ": " + safeMessage(e);
        }
        cancellation.throwIfCancellationRequested();

        boolean consumed = tokenAt(tokens) == Token.EOF;
        if (consumed && !collector.budgetExceeded()) {
            // The grammar's start rule does not require EOF; enforce it here.
            Token next = tokens.LT(1);
            if (next != null && next.getType() != Token.EOF) {
                collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0004"),
                                Severity.ERROR, Phase.PARSING, "diagnostic.syntax.trailing")
                        .at(offsets.tokenRange(next))
                        .argument(new DiagnosticArgument.TokenArgument("actual",
                                safeTokenText(next))));
            }
        }
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
        if (failure != null) {
            collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0002"),
                            Severity.ERROR, Phase.PARSING, "diagnostic.syntax.unexpected")
                    .at(offsets.eofRange())
                    .argument(new DiagnosticArgument.TokenArgument("actual", "<parser-error>"))
                    .argument(new DiagnosticArgument.IdentifierListArgument("expected",
                            List.of("<recoverable-parse>"))));
        }

        LanguageVersion language = effectiveDetection.version().orElse(LanguageVersion.ILI23);
        SyntaxTreeBuilder builder = new SyntaxTreeBuilder(source, offsets, tokens, language);
        DocumentSyntax document;
        try {
            document = builder.build(tree);
        } catch (AnalysisBudgetExceededException budgetError) {
            document = new DocumentSyntax(new SyntaxNodeId("syntax-0"),
                    source.range(source.length(), source.length()), List.of());
            return snapshotWithBudget(source, options, effectiveDetection, tokens, offsets,
                    collector, document, builder, budgetError);
        }
        List<Diagnostic> diagnostics = collector.freeze();
        SyntaxCoverage coverage = new SyntaxCoverage(consumed,
                diagnostics.stream().noneMatch(d -> d.code().value().equals("ILI-SYN-0001")),
                tree != null && diagnostics.stream().noneMatch(d -> d.code().value().startsWith("ILI-SYN-0002")
                        || d.code().value().startsWith("ILI-SYN-0003")
                        || d.code().value().startsWith("ILI-SYN-0004")),
                builder.recoveredRanges(), builder.unsupportedFeatures(), false, false);
        return new SyntaxSnapshot(source, options.mode(), effectiveDetection, document,
                buildTokens(source, offsets, tokens), buildTrivia(source, offsets, tokens),
                diagnostics, coverage, fingerprint(source, options, effectiveDetection));
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
        return new SyntaxSnapshot(source, options.mode(), detection, document,
                buildTokens(source, offsets, tokens), buildTrivia(source, offsets, tokens),
                List.of(diagnostic), coverage, fingerprint(source, options, detection));
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
        if (text != null && text.matches("[A-Z][A-Z0-9_]*")) {
            return TokenKind.KEYWORD;
        }
        if (text != null && text.matches("[!-/:-@\\[-`{-~]+")) {
            return TokenKind.PUNCTUATION;
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
        String fragmentText = source.slice(fragment);
        DiagnosticCollector collector = new DiagnosticCollector(CATALOG,
                AnalysisBudget.defaults().maxDiagnostics());
        AntlrOffsetMap offsets = new AntlrOffsetMap(source);
        var charStream = CharStreams.fromString(fragmentText);
        InterlisLexer lexer = new InterlisLexer(charStream);
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        List<Token> shifted = new ArrayList<>();
        for (Token token : tokens.getTokens()) {
            CommonToken copy = new CommonToken(token);
            copy.setStartIndex(token.getStartIndex() + fragment.startOffset());
            int stop = token.getStopIndex() < 0 ? -1 : token.getStopIndex() + fragment.startOffset();
            copy.setStopIndex(stop);
            shifted.add(copy);
        }
        CommonTokenStream fragmentTokens = new CommonTokenStream(
                new org.antlr.v4.runtime.ListTokenSource(shifted));
        fragmentTokens.fill();
        InterlisParser parser = new InterlisParser(fragmentTokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new CollectingParserErrorListener(offsets, collector));
        org.antlr.v4.runtime.ParserRuleContext context;
        try {
            context = switch (kind) {
                case EXPRESSION -> parser.expression();
                case OBJECT_PATH -> parser.objectOrAttributePath();
                case ATTRIBUTE_DECLARATION -> parser.attributeDef();
            };
        } catch (RuntimeException error) {
            context = null;
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
                languageVersion);
        guru.interlis.syntax.SyntaxNode root;
        if (context == null) {
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
        } else {
            root = switch (kind) {
                case EXPRESSION -> builder.buildExpressionFragment(
                        (InterlisParser.ExpressionContext) context);
                case OBJECT_PATH -> builder.buildPathFragment(
                        (InterlisParser.ObjectOrAttributePathContext) context);
                case ATTRIBUTE_DECLARATION -> builder.buildAttributeFragment(
                        (InterlisParser.AttributeDefContext) context);
            };
        }
        return new FragmentSnapshot(source, kind, fragment, root, consumed,
                collector.freeze(), languageVersion);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return "";
        }
        return message.length() > 200 ? message.substring(0, 197) + "..." : message;
    }

    private static String safeTokenText(Token token) {
        String text = token.getText();
        if (text == null) {
            return "<eof>";
        }
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }
}
