package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCatalog;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosticOrderTest {
    @Test
    void canonicalOrderIsIndependentOfEmissionOrder() {
        SourceText source = new SourceText(new SourceId("memory:/order.ili"),
                new SourceRevision(1), "0123456789abcdef");
        List<Diagnostic> first = collect(source, false);
        List<Diagnostic> second = collect(source, true);
        assertEquals(first.stream().map(Diagnostic::id).toList(),
                second.stream().map(Diagnostic::id).toList());
        assertEquals(first.stream().map(Diagnostic::code).toList(),
                second.stream().map(Diagnostic::code).toList());
        assertEquals(0, first.getFirst().primaryRange().orElseThrow().startOffset());
        assertEquals(10, first.get(1).primaryRange().orElseThrow().startOffset());
    }

    private static List<Diagnostic> collect(SourceText source, boolean reverse) {
        DiagnosticCollector collector = new DiagnosticCollector(DiagnosticCatalog.initial(), 100);
        DiagnosticDraft late = DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0001"),
                        Severity.ERROR, Phase.LEXING, "diagnostic.syntax.lexical")
                .at(source.range(10, 11))
                .argument(new DiagnosticArgument.TokenArgument("token", "b"));
        DiagnosticDraft early = DiagnosticDraft.of(new DiagnosticCode("ILI-SYN-0001"),
                        Severity.ERROR, Phase.LEXING, "diagnostic.syntax.lexical")
                .at(source.range(0, 1))
                .argument(new DiagnosticArgument.TokenArgument("token", "a"));
        if (reverse) {
            collector.emit(late);
            collector.emit(early);
        } else {
            collector.emit(early);
            collector.emit(late);
        }
        return collector.freeze();
    }
}
