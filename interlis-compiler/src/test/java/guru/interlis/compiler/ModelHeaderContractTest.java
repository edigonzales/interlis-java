package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class ModelHeaderContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version, String name, String header, String options, String body) {
        return "INTERLIS " + version + "; MODEL " + name + " " + header + " AT \"https://example.org\" VERSION \"1\" = " + options + body + " END " + name + ".";
    }
    private static CompilationRequest input(String text,int revision) {
        var s=new SourceText(new SourceId("memory:/header.ili"),new SourceRevision(revision),text);
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String text) {
        var r=CompilerEngines.create().compile(input(text,1),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString());
    }
    private static CompilationOutcome.Rejected bad(String text,String reason) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(text,1),TOKEN));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());
        assertTrue(r.analysis().diagnostics().toString().contains(reason),r.analysis().diagnostics()::toString);return r;
    }
    private static ModelSymbol root(CompilationOutcome.Succeeded result) {
        return (ModelSymbol)result.schema().semanticGraph().symbol(result.schema().semanticGraph().rootModels().getFirst()).orElseThrow();
    }

    @Test void explicitOptionsRetainDecodedValuesOriginalRangesAndPublicOrigins() {
        String text=model("2.4","F","NOINCREMENTALTRANSFER","CHARSET \"UTF-8\"; XMLNS \"urn:example:model\"; ","TOPIC T=CLASS C=END C; END T;");
        var result=ok(text);var m=root(result);var options=m.options();
        assertFalse(options.incrementalTransferAllowed());assertFalse(options.usesDefaultCharacterRepertoire());
        assertEquals("UTF-8",options.characterSet().orElseThrow().value());assertEquals("urn:example:model",m.transferNamespace());
        assertEquals(options,result.analysis().syntaxSnapshots().values().iterator().next().root().models().getFirst().options());
        for(var entry:Map.of("NOINCREMENTALTRANSFER",options.noIncrementalTransfer().orElseThrow(),"CHARSET \"UTF-8\"",options.characterSet().orElseThrow().origin(),"XMLNS \"urn:example:model\"",options.xmlNamespace().orElseThrow().origin()).entrySet()) {
            var range=((Origin.DeclaredOrigin)entry.getValue()).range();assertEquals(text.indexOf(entry.getKey()),range.startOffset());assertEquals(entry.getKey().length(),range.length());
        }
        assertEquals(m,result.analysis().queries().symbol(new SymbolRef(result.analysis().snapshotId(),m.id())).known().orElseThrow());
    }

    @Test void profileRejectsOnly24OptionsIn23AndRetainsEditorDeclarations() {
        for(var version:List.of("2.3","2.4")) for(var option:List.of("NOINCREMENTALTRANSFER","CHARSET","XMLNS")) {
            var text=model(version,"F",option.equals("NOINCREMENTALTRANSFER")?option:"",option.equals("CHARSET")?"CHARSET \"UTF-8\"; ":option.equals("XMLNS")?"XMLNS \"urn:test\"; ":"","DOMAIN D=0..10;");
            for(var mode:ParseMode.values()) {
                var parsed=SyntaxServices.create().parse(source("memory:/profile.ili",text),ParseOptions.defaults(mode),TOKEN);
                assertEquals(1,parsed.root().models().getFirst().members().size());
                assertEquals(version.equals("2.3"),parsed.diagnostics().stream().anyMatch(d->d.severity()==guru.interlis.diagnostics.Severity.ERROR));
            }
            if(version.equals("2.3"))bad(text,"ILI-SYN-");else ok(text);
        }
    }

    @Test void defaultsAreNormativeRepertoiresAndVersionedNamespaces() {
        for(var v:List.of("2.3","2.4")) {
            var m=root(ok(model(v,"F","","","DOMAIN D=0..10;")));
            assertEquals(ModelOptions.defaults(),m.options());assertTrue(m.options().usesDefaultCharacterRepertoire());assertTrue(m.options().incrementalTransferAllowed());
            assertEquals(v.equals("2.3")?"http://www.interlis.ch/INTERLIS2.3":"http://www.interlis.ch/xtf/2.4/F",m.transferNamespace());
        }
    }

    @Test void registeredCharsetAliasesWorkWithoutDependingOnJvmCodecs() {
        for(var charset:List.of("UTF-8","utf-8","latin1","ISO_8859-1:1987","csISO2022JP","UTF-7","TIS-620"))
            assertEquals(charset,root(ok(model("2.4","F","","CHARSET \""+charset+"\"; ","DOMAIN D=0..10;"))).options().characterSet().orElseThrow().value());
        for(var charset:List.of("","unknown-charset","utf8","this is not a charset"))bad(model("2.4","F","","CHARSET \""+charset+"\"; ","DOMAIN D=0..10;"),"MODEL_CHARSET");
    }

    @Test void namespacesUseUriReferencesAndPreserveTheirExactIdentity() {
        for(var ns:List.of("urn:test:namespace","https://example.org/model","relative/model","#local","https://example.org/%7EModel"))
            assertEquals(ns,root(ok(model("2.4","F","","XMLNS \""+ns+"\"; ","DOMAIN D=0..10;"))).transferNamespace());
        for(var ns:List.of("","not a uri","%wrong","http://www.w3.org/2000/xmlns/","http://www.w3.org/XML/1998/namespace"))
            bad(model("2.4","F","","XMLNS \""+ns+"\"; ","DOMAIN D=0..10;"),"MODEL_XMLNS");
    }

    private static String translated(String name,String original,String header,String options) {
        return model("2.4",name,header,options,"DOMAIN D=0..10;").replace("INTERLIS 2.4; ","").replace("VERSION \"1\" =","VERSION \"1\" TRANSLATION OF "+original+" [\"1\"] =");
    }
    @Test void translationsUseCanonicalTransferNamespaceAndSemanticallyEqualOptions() {
        String original=model("2.4","Original","NOINCREMENTALTRANSFER","CHARSET \"latin1\"; XMLNS \"urn:test:original\"; ","DOMAIN D=0..10;");
        String text=original+translated("F","Original","NOINCREMENTALTRANSFER","CHARSET \"ISO-8859-1\"; ");
        assertEquals("urn:test:original",root(ok(text)).transferNamespace());
        ok(text.replace("CHARSET \"ISO-8859-1\"; ","CHARSET \"ISO-8859-1\"; XMLNS \"urn:test:original\"; "));
        bad(text.replace("CHARSET \"ISO-8859-1\"; ","CHARSET \"ISO-8859-1\"; XMLNS \"urn:test:other\"; "),"TRANSLATION_XMLNS");
        bad(text.replace("CHARSET \"ISO-8859-1\"","CHARSET \"UTF-8\""),"TRANSLATION_STRUCTURE");
        bad(original+translated("F","Original","","CHARSET \"ISO-8859-1\"; "),"TRANSLATION_STRUCTURE");
        var transitive=model("2.4","Original","","","DOMAIN D=0..10;")+translated("Middle","Original","","")+translated("F","Middle","","");
        assertEquals("http://www.interlis.ch/xtf/2.4/Original",root(ok(transitive)).transferNamespace());
    }

    @Test void contractedIsStructurallyMeaningfulOnlyIn23() {
        for(var version:List.of("2.3","2.4")) {
            var original=model(version,"Original","","","DOMAIN D=0..10;");
            var translated=model(version,"F","","","DOMAIN D=0..10;").replace("INTERLIS "+version+"; ","").replace("MODEL F","CONTRACTED MODEL F").replace("VERSION \"1\" =","VERSION \"1\" TRANSLATION OF Original [\"1\"] =");
            if(version.equals("2.3"))bad(original+translated,"TRANSLATION_STRUCTURE");else ok(original+translated);
        }
    }

    @Test void importsAndModelOrderRetainBoundNamespacesAndOptionOrigins() {
        for(boolean reverse:List.of(false,true)) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili",model("2.4","Base","NOINCREMENTALTRANSFER","CHARSET \"UTF-8\"; XMLNS \"urn:base\"; ","DOMAIN D=0..10;"));
            var use=source(reverse?"memory:/a-use.ili":"memory:/z-use.ili",model("2.4","F","","XMLNS \"urn:use\"; ","IMPORTS Base; TOPIC T=CLASS C=value:Base.D; END C; END T;"));
            var request=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
            var result=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request,TOKEN));
            for(var ref:result.analysis().queries().findQualifiedName(new QualifiedName(List.of("Base")))) {
                var bound=(ModelSymbol)result.analysis().queries().symbol(ref).known().orElseThrow();assertEquals("urn:base",bound.transferNamespace());
                assertEquals(base.id(),((Origin.DeclaredOrigin)bound.options().characterSet().orElseThrow().origin()).range().source());
            }
            assertEquals("urn:use",root(result).transferNamespace());
            var original=model("2.4","Original","","XMLNS \"urn:original\"; ","DOMAIN D=0..10;");
            var middle="INTERLIS 2.4; "+translated("Middle","Original","","");
            var translated="INTERLIS 2.4; "+translated("F","Middle","","");
            var a=source(reverse?"memory:/z.ili":"memory:/a.ili",original);
            var b=source("memory:/m.ili",middle);var c=source(reverse?"memory:/a.ili":"memory:/z.ili",translated);
            var r=CompilerEngines.create().compile(request(List.of("F"),Map.of(a.id(),a,b.id(),b,c.id(),c),List.of(),CompilerOptions.defaults()),TOKEN);
            assertEquals("urn:original",root(assertInstanceOf(CompilationOutcome.Succeeded.class,r)).transferNamespace());
        }
    }

    @Test void editsOfOnlyOptionsAgreeFreshAndReusedAndRecover() {
        var engine=CompilerEngines.create();AnalysisSnapshot previous=null;
        for(int step=1;step<=3;step++) {
            var request=input(model("2.4","F","",step==2?"CHARSET \"missing-charset\"; ":"XMLNS \"urn:test:"+step+"\"; ","DOMAIN D=0..10;"),step);
            var reused=engine.analyze(request,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
            var fresh=CompilerEngines.create().analyze(request,TOKEN);assertEquals(fresh.diagnostics(),reused.diagnostics());
            assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(),reused.semanticGraph()));previous=reused;
        }
    }
}
