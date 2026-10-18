package guru.interlis.compiler.internal.antlr;

import guru.interlis.compiler.internal.DiagnosticCollector;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.source.SourceRange;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.Objects;
import java.util.stream.Collectors;

/** Collects parser diagnostics with original ranges; no stderr listener remains. */
public final class CollectingParserErrorListener extends BaseErrorListener {
    private final AntlrOffsetMap offsets;
    private final DiagnosticCollector collector;

    public CollectingParserErrorListener(AntlrOffsetMap offsets, DiagnosticCollector collector) {
        this.offsets = Objects.requireNonNull(offsets, "offsets");
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {
        Token token = offendingSymbol instanceof Token t ? t : null;
        SourceRange range = token == null
                ? offsets.source().range(
                        Math.min(offsets.source().length(), offsets.offsetAtLineColumn(line, charPositionInLine)),
                        Math.min(offsets.source().length(), offsets.offsetAtLineColumn(line, charPositionInLine)))
                : offsets.tokenRange(token);
        boolean eof = token != null && token.getType() == Token.EOF;
        DiagnosticCode code = new DiagnosticCode(eof ? "ILI-SYN-0003" : "ILI-SYN-0002");
        Phase phase = Phase.PARSING;
        DiagnosticDraft draft = DiagnosticDraft.of(code, Severity.ERROR, phase,
                        eof ? "diagnostic.syntax.missing" : "diagnostic.syntax.unexpected")
                .at(range);
        if (eof) {
            draft.argument(new DiagnosticArgument.IdentifierListArgument("expected",
                    expectedTokens(recognizer, e)));
        } else {
            draft.argument(new DiagnosticArgument.TokenArgument("actual",
                    token == null ? "<unknown>" : safeTokenText(token)));
            draft.argument(new DiagnosticArgument.IdentifierListArgument("expected",
                    expectedTokens(recognizer, e)));
        }
        collector.emit(draft);
    }

    private static String safeTokenText(Token token) {
        String text = token.getText();
        if (text == null) {
            return "<eof>";
        }
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    private static java.util.List<String> expectedTokens(Recognizer<?, ?> recognizer,
                                                         RecognitionException e) {
        IntervalSet expected = e != null ? e.getExpectedTokens() : IntervalSet.EMPTY_SET;
        if (expected == null || expected.isNil()) {
            return java.util.List.of("<unknown>");
        }
        return expected.toList().stream()
                .map(recognizer.getVocabulary()::getDisplayName)
                .sorted()
                .collect(Collectors.toList());
    }
}
