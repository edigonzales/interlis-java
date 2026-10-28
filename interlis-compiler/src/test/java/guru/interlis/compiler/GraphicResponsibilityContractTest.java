package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.GraphicBindingContractTest.*;

/** G2 D: responsible signature class follows bound objects and parameter owners. */
class GraphicResponsibilityContractTest {
    private static final String TYPES="TOPIC Signs = CLASS S EXTENDS INTERLIS.SIGN = PARAMETER A: MANDATORY 0..10; END S; "
            +"CLASS Derived EXTENDS S = PARAMETER B: MANDATORY 0..10; END Derived; "
            +"CLASS Most EXTENDS Derived = PARAMETER C: MANDATORY 0..10; END Most; "
            +"CLASS Other EXTENDS INTERLIS.SIGN = END Other; CLASS Colour EXTENDS INTERLIS.SIGN = END Colour; END Signs; ";
    private static String body(String actual,String signature,String assignments) {
        return TYPES+"SIGN BASKET Library ~ Signs OBJECTS OF "+actual+": Dot; "
                +"TOPIC Data = CLASS Feature = kind:(a,b) ORDERED; visible:BOOLEAN; END Feature; "
                +"GRAPHIC G BASED ON Feature = Draw OF M.Signs."+signature+": (Sign := {Dot}; "+assignments+"); END G; END Data;";
    }
    private static CompilationOutcome.Rejected reject(String version,String body,String rule) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,compile(model(version,body)));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());
        assertTrue(r.analysis().diagnostics().toString().contains(rule),r.analysis().diagnostics()::toString);return r;
    }

    @Test void mandatorySignatureValuesCannotBeExplicitlyUndefined() {
        for(var v:List.of("2.3","2.4"))for(var value:List.of("UNDEFINED","\"\""))
            reject(v,body("S","S","A := "+value),"GRAPHIC_PARAMETER_MANDATORY");
    }

    @Test void exactOriginalsRetainDeclaredSignatureAndBoundObjectAndParameters() throws Exception {
        var pins=Map.of("2.3-S.ili","16d08a96323f91f27b7ed551f578bb3513ae4bfad27b5a4dfafc493ae6b73cbe",
                "2.3-Derived.ili","35a82b534d04edb6f479b38252315b577f70fea583b737ca4c37a6b531312bf1",
                "2.3-Other.ili","a961c22e4ed546b088d18417de2fb8b4c1e83b4d5501bb7e64b60fee6c5888a1",
                "2.4-S.ili","bc5e7e3f1d1f9962a6d3a4b0b78363d3ef31f54ec559441225f073212eacff52",
                "2.4-Derived.ili","6b258a1474625b82f64b0470331235daeb080d997a6352a80987561d8ace2db9",
                "2.4-Other.ili","77a421d920fd6ff7e62efa45755fab69ea18fb5002bb6ac848f9eae4c42de6c4");
        for(var entry:pins.entrySet()) {
            var bytes=Files.readAllBytes(Path.of("../testdata/conformance/g2-signatures",entry.getKey()));
            assertEquals(entry.getValue(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            var text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
            if(entry.getKey().contains("Other")) {
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,compile(text));
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
                assertTrue(rejected.analysis().diagnostics().toString().contains("GRAPHIC_METAOBJECT_TYPE"));continue;
            }
            var result=ok(text);var g=result.schema().semanticGraph();var draw=(DrawingRuleSymbol)named(g,"Draw");
            assertEquals(named(g,"Derived").id(),draw.signatureClass().orElseThrow());
            var assignments=draw.clauses().getFirst().assignments();assertEquals(3,assignments.size());
            var object=(MetaObjectSymbol)g.symbol(((DrawingRuleSymbol.MetaObjectValue)assignments.getFirst().value()).object().orElseThrow()).orElseThrow();
            assertEquals(named(g,entry.getKey().contains("-S.")?"S":"Derived").id(),object.objectClass().orElseThrow());
            assertEquals(named(g,"S").id(),g.symbol(assignments.get(1).parameter().orElseThrow()).orElseThrow().owner().orElseThrow());
            assertEquals(named(g,"Derived").id(),g.symbol(assignments.get(2).parameter().orElseThrow()).orElseThrow().owner().orElseThrow());
            for(var a:assignments) assertEquals(g.symbol(a.parameter().orElseThrow()).orElseThrow(),result.analysis().queries().symbol(new SymbolRef(g.snapshotId(),a.parameter().orElseThrow())).known().orElseThrow());
            var source=result.analysis().syntaxSnapshots().values().iterator().next().source();
            int at=source.text().indexOf("B := 2");
            assertEquals(assignments.get(2).parameter().orElseThrow(),guru.interlis.compiler.query.LanguageQueries.create()
                    .symbolAt(result.analysis(),source.range(at,at),CancellationToken.none()).known().orElseThrow().symbol());
        }
    }

    @Test void anUnusedDeclaredSubclassDoesNotIntroduceItsMandatoryParameters() {
        for(var v:List.of("2.3","2.4"))ok(model(v,body("S","Most","A := 1")));
    }

    @Test void usedParameterOwnersPromoteResponsibilityOnlyAsFarAsRequired() {
        for(var v:List.of("2.3","2.4")) {
            ok(model(v,body("S","Most","A := 1; B := 2")));
            ok(model(v,body("S","Most","A := 1; B := 2; C := 3")));
            reject(v,body("S","Most","A := 1; C := 3"),"GRAPHIC_PARAMETER_MISSING");
            reject(v,body("S","Derived","B := 2"),"GRAPHIC_PARAMETER_MISSING");
        }
    }

    @Test void actualSubclassParametersAreRequiredEvenIfTheOfClassIsABase() {
        for(var v:List.of("2.3","2.4")) {
            reject(v,body("Derived","S","A := 1"),"GRAPHIC_PARAMETER_MISSING");
            reject(v,body("Most","Most","A := 1; B := 2"),"GRAPHIC_PARAMETER_MISSING");
            reject(v,body("Other","Derived","A := 1; B := 2"),"GRAPHIC_METAOBJECT_TYPE");
        }
    }

    @Test void explicitMetaobjectTargetsKeepTheirOwnRestrictions() {
        for(var v:List.of("2.3","2.4")) {
            var text=body("S","Derived","A := 1; B := 2").replace("PARAMETER B:","PARAMETER Sign(EXTENDED):METAOBJECT OF Derived; B:");
            reject(v,text,"GRAPHIC_METAOBJECT_TYPE");ok(model(v,text.replace("OBJECTS OF S:","OBJECTS OF Derived:")));
            var colour=body("S","S","A := 1; Colour := {Paint}").replace("A: MANDATORY", "Colour:METAOBJECT OF M.Signs.Colour; A: MANDATORY").replace("OBJECTS OF S: Dot;","OBJECTS OF S: Dot OBJECTS OF Colour: Paint;");
            ok(model(v,colour));reject(v,colour.replace("Colour := {Paint}","Colour := {Dot}"),"GRAPHIC_METAOBJECT_TYPE");
            reject(v,colour.replace("Colour:METAOBJECT OF M.Signs.Colour", "Colour:METAOBJECT"),"GRAPHIC_METAOBJECT_TYPE");
            ok(model(v,colour.replace("Colour:METAOBJECT OF M.Signs.Colour", "Colour:METAOBJECT").replace("Colour := {Paint}","Colour := {Dot}")));
            var inherited=body("S","S","A := 1").replace("PARAMETER B:","PARAMETER Sign(EXTENDED):METAOBJECT OF Derived; B:")
                    .replace("END Data;","GRAPHIC H EXTENDS G = Draw(EXTENDED) OF M.Signs.Derived:(A := 1); END H; END Data;");
            reject(v,inherited,"GRAPHIC_METAOBJECT_TYPE");
            ok(model(v,inherited.replace("OBJECTS OF S: Dot;","OBJECTS OF S: Dot OBJECTS OF Derived: Special;")
                    .replace("Derived:(A := 1)","Derived:(Sign := {Special}; A := 1; B := 2)")));

        }
    }

    @Test void inheritedSignAndParametersRemainOrderedWhileAddedParametersPromoteTheClass() {
        for(var v:List.of("2.3","2.4")) {
            var b=body("S","S","A := 1").replace("END Data;", "GRAPHIC H EXTENDS G = Draw(EXTENDED) OF M.Signs.Most:(B := 2); END H; END Data;");
            var g=ok(model(v,b)).schema().semanticGraph();
            var h=(GraphicSymbol)named(g,"H");var local=(DrawingRuleSymbol)g.symbol(h.rules().getFirst()).orElseThrow();
            assertEquals(named(g,"Draw").id(),local.overrides().orElseThrow());assertEquals(1,local.clauses().getFirst().assignments().size());
            reject(v,b.replace("(B := 2)","(C := 3)"),"GRAPHIC_PARAMETER_MISSING");
        }
    }

    @Test void parameterOverridesRecheckInheritedValuesInTheResponsibleClass() {
        for(var v:List.of("2.3","2.4")) {
            var b=body("S","S","A := 9").replace("PARAMETER B:","PARAMETER A(EXTENDED):MANDATORY 0..5; B:")
                    .replace("END Data;", "GRAPHIC H EXTENDS G = Draw(EXTENDED) OF M.Signs.Derived:(B := 2); END H; END Data;");
            reject(v,b,"GRAPHIC_PARAMETER_TYPE");ok(model(v,b.replace("A := 9","A := 4")));
            ok(model(v,b.replace("(B := 2)","(A := 4; B := 2)")));
            ok(model(v,b.replace("(B := 2)","WHERE visible == #true (A := 4; B := 2)")));
            ok(model(v,b.replace("Draw OF M.Signs.S: (", "Draw OF M.Signs.S: WHERE visible == #true (").replace("(B := 2)","WHERE visible == #true (A := 4; B := 2)")));
        }
    }

    @Test void conditionalAndAccordingSignAlternativesRetainTheirOwnBoundObjects() {
        for(var v:List.of("2.3","2.4")) {
            var b=body("S","Derived","A := 1; B := 2").replace("OBJECTS OF S: Dot;","OBJECTS OF S: Dot OBJECTS OF Derived: Special;")
                    .replace("(Sign := {Dot}; A := 1; B := 2)","WHERE visible == #true (Sign := ACCORDING kind ({Dot} WHEN IN #a,{Special} WHEN IN #b); A := 1; B := 2)");
            var g=ok(model(v,b)).schema().semanticGraph();var draw=(DrawingRuleSymbol)named(g,"Draw");assertTrue(draw.clauses().getFirst().condition().isPresent());
            var value=(DrawingRuleSymbol.AccordingValue)draw.clauses().getFirst().assignments().getFirst().value();
            assertEquals(List.of("a","b"),value.cases().stream().map(DrawingRuleSymbol.EnumAssignment::first).toList());
            assertEquals(List.of(named(g,"Dot").id(),named(g,"Special").id()),value.cases().stream().map(e->((DrawingRuleSymbol.MetaObjectValue)e.value()).object().orElseThrow()).toList());
            reject(v,b.replace("; B := 2", ""),"GRAPHIC_PARAMETER_MISSING");
        }
    }

    @Test void importedSignatureResponsibilityDoesNotDependOnSourceOrder() {
        for(var v:List.of("2.3","2.4")) for(boolean reverse:List.of(false,true)) {
            var base=CompilerTestSupport.source(reverse?"memory:/z-base.ili":"memory:/a-base.ili",model(v,TYPES).replace("MODEL M","MODEL Base").replace("END M.","END Base."));
            var use=CompilerTestSupport.source(reverse?"memory:/a-use.ili":"memory:/z-use.ili",model(v,body("S","Derived","A := 1; B := 2").replace(TYPES,"IMPORTS Base; ").replace("~ Signs", "~ Base.Signs").replace("M.Signs.","Base.Signs.")));
            var sources=Map.of(base.id(),base,use.id(),use);var result=CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"),sources,bindings(sources),CompilerOptions.defaults()),CancellationToken.none());
            assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
        }
    }

    @Test void translationStillComparesTheBoundSignObject() {
        for(var v:List.of("2.3","2.4")) {
            var b=body("S","Derived","A := 1; B := 2").replace("OBJECTS OF S: Dot;","OBJECTS OF S: Dot,Square;");
            var original=model(v,b).replace("MODEL M", "MODEL Original").replace("END M.","END Original.").replace("M.Signs.","Original.Signs.");
            var translated=model(v,b).replace("INTERLIS "+v+"; ","").replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Original [\"1\"] =");
            ok(original+translated);var r=assertInstanceOf(CompilationOutcome.Rejected.class,compile(original+translated.replace("Sign := {Dot}","Sign := {Square}")));
            assertTrue(r.analysis().diagnostics().toString().contains("TRANSLATION_DRAWING_RULE"));
        }
    }

    @Test void editSequencesMatchFreshPublicGraphsAndRecover() {
        for(var v:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;
            for(int revision=1;revision<=3;revision++) {
                var source=new SourceText(new SourceId("memory:/graphic.ili"),new SourceRevision(revision),model(v,body("S","Derived",revision==2?"B := 2":"A := 1; B := 2")));
                var input=CompilerTestSupport.request(List.of("M"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
                var actual=engine.analyze(input,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                var fresh=CompilerEngines.create().analyze(input,CancellationToken.none());assertEquals(fresh.diagnostics(),actual.diagnostics());assertEquals(revision!=2,actual.diagnostics().isEmpty());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(actual.semanticGraph().symbols(),actual.semanticGraph()));
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().types(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(actual.semanticGraph().types(),actual.semanticGraph()));
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(actual.semanticGraph().expressions(),actual.semanticGraph()));
                previous=actual;
            }
        }
    }
}
