package guru.interlis.cli.verification;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import guru.interlis.model.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacySourceMetadataTest {
    static SourceText source(String version,String newline,long revision) {
        String text="INTERLIS "+version+";\nCONTRACTED MODEL M AT \"x\" VERSION \"1\" =\n"
                +"!!@ fn.description=\"🦊 resource\";\n!!@ fn.param=\"url address\";\n!!@ fn.param=\"prefix namespace\";\n"
                +"!!@ fn.return=\"First line\n!!@ second line\";\nFUNCTION f(url:TEXT;prefix:TEXT):BOOLEAN;\nEND M.\n";
        return new SourceText(new SourceId("memory:/legacy-metadata.ili"),new SourceRevision(revision),text.replace("\n",newline));
    }
    static CompilationRequest request(SourceText source) {
        return new CompilationRequest(List.of(new ModelSelector.ByName("M",Optional.empty(),Optional.empty())),SourceSet.of(Map.of(source.id(),source)),CompilerOptions.defaults());
    }
    @Test void repeatedFunctionParametersAndMultilineCommentsCompileInBothProfiles() {
        for(String version:List.of("2.3","2.4"))for(String newline:List.of("\n","\r\n")) {
            var outcome=CompilerEngines.create().compile(request(source(version,newline,1)),CancellationToken.none());
            assertInstanceOf(CompilationOutcome.Succeeded.class,outcome,()->outcome instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():outcome.toString());
        }
    }

    static MetaAttributeSet metadata(CompilationOutcome.Succeeded result) {
        return result.schema().semanticGraph().symbols().stream().filter(s->s.name().value().equals("f")).findFirst().orElseThrow().header().metaAttributes();
    }
    @Test void everyOccurrenceAndItsOriginalUtf16RangeIsPublicInStrictAndEditorModes() {
        for(String version:List.of("2.3","2.4"))for(String newline:List.of("\n","\r\n")) {
            var source=source(version,newline,7);
            for(ParseMode mode:ParseMode.values()) {
                var syntax=SyntaxServices.create().parse(source,ParseOptions.defaults(mode),CancellationToken.none());
                assertTrue(syntax.diagnostics().isEmpty(),syntax.diagnostics().toString());assertEquals(source.text(),syntax.source().text());
                assertEquals(4,syntax.metadata().values().stream().mapToInt(m->m.attributes().size()).sum());
            }
            var result=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request(source),CancellationToken.none()));
            var metadata=metadata(result);assertEquals(4,metadata.entries().size());assertEquals(3,metadata.values().size());
            assertEquals(List.of("url address","prefix namespace"),metadata.values("fn.param").stream().map(MetaAttributeSet.Value::text).toList());
            assertEquals("url address",metadata.values().get("fn.param").text());assertTrue(metadata.values("missing").isEmpty());
            assertEquals("First line"+newline+" second line",metadata.values().get("fn.return").text());
            for(var entry:metadata.entries()) {
                var origin=assertInstanceOf(Origin.DeclaredOrigin.class,entry.value().origin());assertEquals(source.id(),origin.range().source());assertEquals(source.revision(),origin.range().revision());
                String raw=source.slice(origin.range());assertTrue(raw.startsWith(entry.key()+"="));
                if(entry.key().equals("fn.return"))assertEquals("fn.return=\"First line"+newline+"!!@ second line\"",raw);
            }
        }
    }
    @Test void reusedEditsRetainBothValuesAndDoNotMutatePublishedMetadata() {
        for(String version:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();var old=source(version,"\n",1);
            var original=assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(request(old),CancellationToken.none()));
            var changed=new SourceText(old.id(),new SourceRevision(2),old.text().replace("prefix namespace","new namespace"));
            var reused=assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(request(changed),new AnalysisReuse(Optional.of(original.analysis()),original.analysis().syntaxSnapshots()),CancellationToken.none()));
            var fresh=assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(request(changed),CancellationToken.none()));
            assertEquals(metadata(fresh),metadata(reused));assertEquals(List.of("url address","new namespace"),metadata(reused).values("fn.param").stream().map(MetaAttributeSet.Value::text).toList());
            assertEquals("prefix namespace",metadata(original).values("fn.param").getLast().text());
            var entries=new ArrayList<>(metadata(fresh).entries());var index=new LinkedHashMap<>(metadata(fresh).values());var copy=new MetaAttributeSet(index,entries);entries.clear();index.clear();assertEquals(4,copy.entries().size());
            assertThrows(UnsupportedOperationException.class,()->copy.entries().clear());assertThrows(UnsupportedOperationException.class,()->copy.values("fn.param").clear());
            assertThrows(IllegalArgumentException.class,()->new MetaAttributeSet(Map.of(),copy.entries()));
        }
    }
    @Test void scalarConflictsAndUnterminatedOrMalformedContinuationsRemainSourceErrors() {
        for(String version:List.of("2.3","2.4"))for(String annotation:List.of("label=one;label=two","fn.param=one;fn.param=two","fn.return=\"open","fn.return=\"open\n!!@ broken\\q\";")) {
            var source=new SourceText(new SourceId("memory:/bad-metadata.ili"),new SourceRevision(3),"INTERLIS "+version+";MODEL M AT \"x\" VERSION \"1\"=\n!!@ "+annotation+"\nDOMAIN D=TEXT;END M.");
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(source),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
            assertTrue(rejected.analysis().diagnostics().stream().allMatch(d->!d.code().value().equals("ILI-INTERNAL-0001")));
        }
    }
}
