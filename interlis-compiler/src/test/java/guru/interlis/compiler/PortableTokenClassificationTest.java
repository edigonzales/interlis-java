package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortableTokenClassificationTest {
    @Test void longPunctuationAndUnicodeTriviaRetainTheirPublicTokenKindsAndOffsets() {
        for(String version:List.of("2.3","2.4")) {
            String punctuation="!!"+"-".repeat(100000),mixed="!! mixed A",unicode="!! 🧭";
            var source=new SourceText(new SourceId("memory:/token-classification"),new SourceRevision(1),"INTERLIS "+version+";\n"+punctuation+"\n"+mixed+"\n"+unicode+"\nMODEL F AT \"x\" VERSION \"1\"=TOPIC T=CLASS C=value:TEXT;END C;END T;END F.");
            var result=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(new CompilationRequest(List.of(new ModelSelector.ByName("F",Optional.empty(),Optional.empty())),SourceSet.of(Map.of(source.id(),source)),CompilerOptions.defaults()),CancellationToken.none()));
            var syntax=result.analysis().syntaxSnapshot(source.id()).orElseThrow();
            for(var comment:List.of(punctuation,mixed,unicode)) {
                var token=syntax.tokens().stream().filter(t->t.text().equals(comment)).findFirst().orElseThrow();
                assertEquals(comment.equals(punctuation)?TokenKind.PUNCTUATION:TokenKind.OTHER,token.kind());
                assertEquals(source.text().indexOf(comment),token.range().startOffset());assertEquals(comment.length(),token.range().length());
                assertTrue(syntax.trivia().stream().anyMatch(t->t.range().equals(token.range())));
            }
            var c=result.analysis().semanticGraph().symbols().stream().filter(s->s.name().value().equals("C")).findFirst().orElseThrow();
            assertEquals(source.text().lastIndexOf("CLASS C")+6,c.nameRange().orElseThrow().startOffset());
        }
    }
}
