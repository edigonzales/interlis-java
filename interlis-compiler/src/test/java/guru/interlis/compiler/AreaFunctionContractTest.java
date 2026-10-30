package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class AreaFunctionContractTest {
    private static final String GEOMETRY="DOMAIN Point=COORD 0.000..100.000,0.000..100.000; ";
    private static final String SURFACE="SURFACE WITH(STRAIGHTS) VERTEX Point WITHOUT OVERLAPS >0.010";
    private static final String STRUCTURES="STRUCTURE S=surface:"+SURFACE+"; text:TEXT; END S; STRUCTURE Holder=surfaces:BAG {0..*} OF S; END Holder; ";
    private static CompilationOutcome compile(String v,String declarations) {
        var s=source("memory:/areas.ili","INTERLIS "+v+"; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = "+GEOMETRY+declarations+" END F.");
        var input=request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
        var engine=CompilerEngines.create();var result=engine.compile(input,CancellationToken.none());
        if(result instanceof CompilationOutcome.Failed)engine.analyze(input,CancellationToken.none());
        return result;
    }
    private static SemanticGraph ok(String v,String body) {
        var r=compile(v,body);return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString()).schema().semanticGraph();
    }
    private static void bad(String v,String body,String rule) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,compile(v,body));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());assertTrue(r.analysis().diagnostics().toString().contains(rule),r.analysis().diagnostics().toString());
    }
    private static String direct(String function,String args) {return "TOPIC T=CLASS C=surface:"+SURFACE+";SET CONSTRAINT INTERLIS."+function+"("+args+");END C;END T;";}
    private static FunctionCallExpression call(SemanticGraph g,String name) {return g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).filter(x->x.functionName().endsWith("."+name)).findFirst().orElseThrow();}

    @Test void attributeFunctionsBindDirectAndNestedSurfaceOwnersAndPublicSignatures() {
        for(String v:List.of("2.3","2.4"))for(String name:v.equals("2.3")?List.of("areAreas"):List.of("areAreas","areAreasWithoutGaps")) {
            var g=ok(v,direct(name,"ALL,UNDEFINED,>>surface"));
            var call=call(g,name);assertTrue(call.argumentPaths().isEmpty());
            var function=assertInstanceOf(FunctionSymbol.class,g.symbol(call.targetFunction().orElseThrow()).orElseThrow());
            for(int i=1;i<3;i++) {
                var formal=assertInstanceOf(AttributePathType.class,g.type(function.parameters().get(i).typeId()).orElseThrow());
                assertEquals(new AttributePathType.Argument(function.id(),i-1),formal.owner().orElseThrow());assertEquals(1,formal.restrictions().size());
            }
            ok(v,"TOPIC T="+STRUCTURES+"CLASS C=bag:BAG {0..*} OF S; SET CONSTRAINT INTERLIS."+name+"(ALL,>>bag,>>S->surface);END C;END T;");
        }
    }
    @Test void emptyTextBagUsesTheObjectRootForAttributeAndStringVariants() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,direct("areAreas","ALL,\"\",>>surface"));
            bad(v,"TOPIC T="+STRUCTURES+"CLASS C=surface:"+SURFACE+";SET CONSTRAINT INTERLIS.areAreas(ALL,\"\",>>S->surface);END C;END T;","FUNCTION_ATTRIBUTE");
        }
        var g=ok("2.4",direct("areAreas2","ALL,\"\",\"surface\""));
        assertEquals(0,call(g,"areAreas2").argumentPaths().get(2).rootArgument());
        assertTrue(call(g,"areAreas2").argumentPaths().get(2).binding().isPresent());
    }
    @Test void attributeFunctionsRejectWrongOwnersWrongValuesAndArity() {
        for(String v:List.of("2.3","2.4")) {
            bad(v,"TOPIC T="+STRUCTURES+"CLASS C=bag:BAG OF S; SET CONSTRAINT INTERLIS.areAreas(ALL,>>bag,>>S->text);END C;END T;","FUNCTION_ATTRIBUTE");
            bad(v,"TOPIC T="+STRUCTURES+"CLASS C=bag:BAG OF S;surface:"+SURFACE+";SET CONSTRAINT INTERLIS.areAreas(ALL,>>bag,>>C->surface);END C;END T;","FUNCTION_ATTRIBUTE");
            bad(v,direct("areAreas","ALL,UNDEFINED,123"),"FUNCTION_ATTRIBUTE");
            bad(v,direct("areAreas","ALL,UNDEFINED"),"ILI-SEM-0005");
        }
    }
    @Test void userFunctionAttributeArgumentsUseActualPriorArguments() {
        for(String v:List.of("2.3","2.4")) {
            String function="FUNCTION check(objects:OBJECTS OF ANYCLASS; attr:ATTRIBUTE OF @objects RESTRICTION(SURFACE)):BOOLEAN; ";
            var g=ok(v,function+"TOPIC T=CLASS C=surface:"+SURFACE+";SET CONSTRAINT F.check(ALL,>>surface);END C;END T;");
            assertEquals(1,g.expressions().stream().filter(FunctionCallExpression.class::isInstance).count());
            bad(v,function+"TOPIC T=CLASS Other=surface:"+SURFACE+";END Other;CLASS C=surface:"+SURFACE+";SET CONSTRAINT F.check(ALL,>>Other->surface);END C;END T;","FUNCTION_ATTRIBUTE");
        }
    }
    @Test void textFunctionsBindPathsThroughCollectionsAndKeepRawEscapeRanges() {
        for(String name:List.of("areAreas2","areAreas3","areAreasWithoutGaps2")) {
            var g=ok("2.4","TOPIC T="+STRUCTURES+"CLASS C=geometry:BAG OF Holder;SET CONSTRAINT INTERLIS."+name+"(ALL,\"geom\\u0065try->surfaces\",\"surface\");END C;END T;");
            var call=call(g,name);assertEquals(Set.of(1,2),call.argumentPaths().keySet());
            assertEquals(0,call.argumentPaths().get(1).rootArgument());assertEquals(1,call.argumentPaths().get(2).rootArgument());
            assertEquals(List.of("geometry","surfaces"),call.argumentPaths().get(1).steps().stream().map(FunctionCallExpression.ArgumentPathStep::name).toList());
            assertEquals(13,call.argumentPaths().get(1).steps().getFirst().range().length());
            for(var p:call.argumentPaths().values())assertInstanceOf(PathExpression.class,g.expression(p.binding().orElseThrow()).orElseThrow());
            assertTrue(g.references().stream().anyMatch(r->r.range().equals(call.argumentPaths().get(1).steps().getFirst().range())));
            ok("2.4",direct(name,"ALL,UNDEFINED,\"surface\""));
        }
    }
    @Test void textPathsRejectMalformedNonconstantWrongValueAndUnavailableFunctions() {
        for(String argument:List.of("\"missing\"","\"surface->\"","\"sur\\u0024face\"","\"\"","name")) {
            String body="TOPIC T=CLASS C=surface:"+SURFACE+";name:TEXT;SET CONSTRAINT INTERLIS.areAreas2(ALL,UNDEFINED,"+argument+");END C;END T;";
            var outcome=compile("2.4",body);var r=assertInstanceOf(CompilationOutcome.Rejected.class,outcome,()->argument+": "+outcome);assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());
        }
        bad("2.4",direct("areAreas2","ALL,\"surface\",\"surface\""),"FUNCTION_PATH_TYPE");
        bad("2.3",direct("areAreasWithoutGaps","ALL,UNDEFINED,>>surface"),"BUILTIN_NAME");
        bad("2.3",direct("areAreas2","ALL,UNDEFINED,\"surface\""),"BUILTIN_NAME");
    }
    @Test void valueDependentObjectRootsRetainParsedPathsWithoutInventedMemberIdentities() {
        var g=ok("2.4","FUNCTION objects(flag:BOOLEAN):OBJECTS OF ANYCLASS;TOPIC T=CLASS C=SET CONSTRAINT INTERLIS.areAreas2(F.objects(#true),UNDEFINED,\"surface\");END C;END T;");
        var path=call(g,"areAreas2").argumentPaths().get(2);assertEquals(0,path.rootArgument());assertTrue(path.binding().isEmpty());assertEquals("surface",path.steps().getFirst().name());assertInstanceOf(GeometryType.class,g.type(path.expectedType()).orElseThrow());
    }
}
