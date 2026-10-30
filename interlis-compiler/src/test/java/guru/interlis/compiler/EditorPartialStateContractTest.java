package guru.interlis.compiler;

import guru.interlis.compiler.query.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** INC-005 / SEM-015: editor recovery keeps actual navigable neighbors without granting a schema. */
class EditorPartialStateContractTest {
    @Test void missingAttributeTypeKeepsNeighborDefinitionCompletionAndExactPublicType() {
        for(String version:List.of("2.3","2.4")) {
            String text="INTERLIS "+version+";MODEL F AT \"x\" VERSION \"1\"=TOPIC T=CLASS Broken=missing: ;END Broken;CLASS Neighbor=value:TEXT*4;END Neighbor;CLASS Use EXTENDS Neighbor=END Use;END T;END F.";
            var source=CompilerTestSupport.source("memory:/editor-partial.ili",text);var request=CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
            var engine=CompilerEngines.create();var analysis=engine.analyze(request,CancellationToken.none());assertFalse(analysis.diagnostics().isEmpty());
            var neighbor=analysis.queries().findQualifiedName(QualifiedName.of("F","T","Neighbor")).getFirst();assertInstanceOf(ClassSymbol.class,analysis.queries().symbol(neighbor).known().orElseThrow());
            var value=analysis.queries().findQualifiedName(QualifiedName.of("F","T","Neighbor","value")).getFirst();
            assertEquals(4,assertInstanceOf(TextType.class,analysis.queries().declaredType(value).known().orElseThrow()).maxLength().orElseThrow());
            var queries=LanguageQueries.create();int offset=text.lastIndexOf("EXTENDS Neighbor")+8;
            var definition=queries.definition(analysis,source.range(offset,offset),CancellationToken.none()).known().orElseThrow();assertEquals(neighbor,definition.symbol());assertEquals(source.revision(),definition.nameRange().revision());
            var completed=queries.complete(analysis,new CompletionQuery(source.range(offset,offset),new NamePrefix("Nei"),20),CancellationToken.none());
            assertTrue(completed.candidates().stream().anyMatch(c->c.label().equals("Neighbor")),completed.toString());
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,engine.compile(request,CancellationToken.none()));assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
        }
    }
}
