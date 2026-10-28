package guru.interlis.compiler.internal.antlr;

import guru.interlis.compiler.internal.DiagnosticCollector;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.Objects;

/** Collects lexer diagnostics in the canonical model and never writes to stderr. */
public final class CollectingLexerErrorListener extends BaseErrorListener {
    private final SourceText source;
    private final AntlrOffsetMap offsets;
    private final DiagnosticCollector collector;

    public CollectingLexerErrorListener(SourceText source, AntlrOffsetMap offsets,
                                        DiagnosticCollector collector) {
        this.source = Objects.requireNonNull(source, "source");
        this.offsets = Objects.requireNonNull(offsets, "offsets");
        this.collector = Objects.requireNonNull(collector, "collector");
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {
        SourceRange range;
        if(e instanceof org.antlr.v4.runtime.LexerNoViableAltException lexical
                && lexical.getStartIndex()>=0 && lexical.getStartIndex()<offsets.codePointCount())
            range=offsets.range(lexical.getStartIndex(),lexical.getStartIndex());
        else {
            int start=offsets.offsetAtLineColumn(line,charPositionInLine);
            range=source.range(start,Math.min(source.length(),start+1));
        }
        String tokenText = offendingSymbol instanceof org.antlr.v4.runtime.Token token
                ? token.getText() : "<lexer>";
        collector.emit(DiagnosticDraft.of(
                        new DiagnosticCode("ILI-SYN-0001"), Severity.ERROR, Phase.LEXING,
                        "diagnostic.syntax.lexical")
                .at(range)
                .argument(new DiagnosticArgument.TokenArgument("token",
                        tokenText == null ? "<eof>" : tokenText)));
    }
}
