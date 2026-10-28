package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class TextLiteralContractTest {
    private static CompilationRequest input(String version,String literal,int length,long revision) {
        var source=new SourceText(new SourceId("memory:/text.ili"),new SourceRevision(revision),"INTERLIS "+version+"; MODEL F AT \"https://example.org\" VERSION \"1\" = TOPIC T=CLASS C=a:TEXT*"+length+":=\""+literal+"\"; END C; END T; END F.");
        return request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    private static CompilationRequest modelRequest(String version,String body) {
        var source=new SourceText(new SourceId("memory:/string-contract.ili"),new SourceRevision(1),
                "INTERLIS "+version+"; MODEL F AT \"https://example.org\" VERSION \"1\" = "+body+" END F.");
        return request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    private static SyntaxSnapshot parseText(String version,String body,ParseMode mode) {
        var source=new SourceText(new SourceId("memory:/string-contract.ili"),new SourceRevision(1),
                "INTERLIS "+version+"; MODEL F AT \"https://example.org\" VERSION \"1\" = "+body+" END F.");
        return SyntaxServices.create().parse(source,ParseOptions.defaults(mode),CancellationToken.none());
    }
    @Test void stringEscapesAndLineBoundariesAreLexicalContracts() {
        String literal="a\\\"b\\\\c"+"\\u0061";
        String decoded="a\"b\\ca";
        for(var version:List.of("2.3","2.4")) {
            var result=CompilerEngines.create().compile(modelRequest(version,
                    "TOPIC T=CLASS C=a:TEXT*6:=\""+literal+"\"; END C; CLASS D=END D; END T;"),CancellationToken.none());
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result,
                    ()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString()).schema().semanticGraph();
            var expression=graph.expressions().stream().filter(LiteralExpression.class::isInstance).map(LiteralExpression.class::cast)
                    .filter(e->e.textValue().equals(Optional.of(decoded))).findFirst().orElseThrow();
            var source=modelRequest(version,"TOPIC T=CLASS C=a:TEXT*6:=\""+literal+"\"; END C; CLASS D=END D; END T;").sources().sources().values().iterator().next();
            assertEquals("\""+literal+"\"",source.slice(expression.range()));
            assertEquals(6,assertInstanceOf(TextType.class,graph.type(expression.typeId()).orElseThrow()).maxLength().orElseThrow());

            for(var body:List.of(
                    "TOPIC T=CLASS C=a:TEXT*10:=\"a\nb\"; END C; CLASS D=END D; END T;",
                    "TOPIC T=CLASS C=a:TEXT*10:=\"abc; END C; CLASS D=END D; END T;")) {
                for(var mode:ParseMode.values()) {
                    var snapshot=parseText(version,body,mode);
                    assertFalse(snapshot.diagnostics().isEmpty(),()->version+":"+mode+":"+body);
                    if(mode==ParseMode.EDITOR) assertFalse(snapshot.coverage().parseComplete(),()->version+":"+body);
                }
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,
                        CompilerEngines.create().compile(modelRequest(version,body),CancellationToken.none()));
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons(),version+":"+body);
            }
        }
        for(var version:List.of("2.3","2.4")) {
            var surrogate="\\uD83E\\uDD73";
            var result=CompilerEngines.create().compile(modelRequest(version,
                    "TOPIC T=CLASS C=a:TEXT:=\""+surrogate+"\"; END C; END T;"),CancellationToken.none());
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result,
                    ()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString()).schema().semanticGraph();
            var expression=graph.expressions().stream().filter(LiteralExpression.class::isInstance).map(LiteralExpression.class::cast)
                    .filter(e->e.textValue().equals(Optional.of("🥳"))).findFirst().orElseThrow();
            var source=modelRequest(version,"TOPIC T=CLASS C=a:TEXT:=\""+surrogate+"\"; END C; END T;").sources().sources().values().iterator().next();
            assertEquals("\""+surrogate+"\"",source.slice(expression.range()));
            for(var invalid:List.of("\\q","\\u12")) {
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,
                        CompilerEngines.create().compile(modelRequest(version,
                                "TOPIC T=CLASS C=a:TEXT:=\""+invalid+"\"; END C; END T;"),CancellationToken.none()));
                assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS),version+":"+invalid+":"+rejected.rejection().reasons());
            }
        }
    }
    @Test void typedLiteralsUseNormativeLengthWithoutChangingDecodedTextOrSourceRanges() {
        for(var version:List.of("2.3","2.4")) for(var literal:List.of("é","e\u0301","C\u0302\u0327","\uFB03","🥳","\u034F")) {
            var request=input(version,literal,1,1);var result=CompilerEngines.create().compile(request,CancellationToken.none());
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString()).schema().semanticGraph();
            var expression=graph.expressions().stream().filter(LiteralExpression.class::isInstance).map(LiteralExpression.class::cast).filter(e->e.textValue().equals(Optional.of(literal))).findFirst().orElseThrow();
            assertEquals(1,assertInstanceOf(TextType.class,graph.type(expression.typeId()).orElseThrow()).maxLength().orElseThrow());
            var attribute=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).findFirst().orElseThrow();
            assertEquals(RelationDecision.ALLOWED,new TypeRelations(graph).canAssign(new TypeRef(graph.snapshotId(),expression.typeId()),
                    new TypeRef(graph.snapshotId(),attribute.declaredType()),AssignmentContext.NORMAL).known().orElseThrow().decision());
            var source=request.sources().sources().values().iterator().next();assertEquals("\""+literal+"\"",source.slice(expression.range()));
        }
        assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input("2.4","ab",1,1),CancellationToken.none()));
        assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input("2.4","각",2,1),CancellationToken.none()));
        assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(input("2.4","각",3,1),CancellationToken.none()));
    }
    @Test void zeroDeclarationWidthsAndTextControlCharactersAreSourceErrors() {
        for(var version:List.of("2.3","2.4"))for(var value:List.of("x","")) {
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,value,0,1),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
            assertTrue(result.analysis().diagnostics().toString().contains("TEXT_LENGTH"));
        }
        for(var version:List.of("2.3","2.4")) {
            var text=input(version,"a\\u0009b",10,1);
            assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(text,CancellationToken.none()));
            var original=text.sources().sources().values().iterator().next();
            var modified=new SourceText(original.id(),original.revision(),original.text().replace(":TEXT*",":MTEXT*"));
            var multiline=request(List.of("F"),Map.of(modified.id(),modified),List.of(),CompilerOptions.defaults());
            var result=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(multiline,CancellationToken.none()));
            var expression=result.schema().semanticGraph().expressions().stream().filter(LiteralExpression.class::isInstance).map(LiteralExpression.class::cast).findFirst().orElseThrow();
            assertEquals(Optional.of("a\tb"),expression.textValue());
            assertEquals(TextKind.MTEXT,assertInstanceOf(TextType.class,result.schema().semanticGraph().type(expression.typeId()).orElseThrow()).textKind());
        }
    }
    @Test void importedFunctionParametersUseTheSameLiteralTypesInEitherSourceOrder() {
        for(var version:List.of("2.3","2.4"))for(var order:List.of(false,true))for(var literal:List.of("é","e\u0301")) {
            var base=source(order?"memory:/a-base.ili":"memory:/z-base.ili","INTERLIS "+version+"; CONTRACTED MODEL Base AT \"https://example.org\" VERSION \"1\" = FUNCTION f(v:TEXT*1):BOOLEAN; END Base.");
            var use=source(order?"memory:/z-use.ili":"memory:/a-use.ili","INTERLIS "+version+"; MODEL F AT \"https://example.org\" VERSION \"1\" = IMPORTS Base; TOPIC T=CLASS C=MANDATORY CONSTRAINT Base.f(\""+literal+"\"); END C; END T; END F.");
            var request=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
            var result=CompilerEngines.create().compile(request,CancellationToken.none());
            {
                var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result).schema().semanticGraph();
                var call=graph.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
                assertTrue(call.targetFunction().isPresent());
            }
        }
    }
    @Test void versionedLiteralsAndEditResultsAgreeWithFreshAnalysis() {
        assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(input("2.3","e\u0301",1,1),CancellationToken.none()));
        assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(input("2.3","e\u0301",2,1),CancellationToken.none()));
        var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
        for(var literal:List.of("e\u0301","ab","é")) {
            var request=input("2.4",literal,1,++revision);var fresh=engine.analyze(request,CancellationToken.none());
            var reused=previous==null?fresh:engine.analyze(request,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
            assertEquals(fresh.diagnostics(),reused.diagnostics());
            assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().expressions(),reused.semanticGraph()));previous=reused;
        }
    }
}
