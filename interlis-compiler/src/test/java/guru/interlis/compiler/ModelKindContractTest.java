package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelKindContractTest {
    @Test
    void typeModelKindIsPreserved() {
        SourceText source = new SourceText(new SourceId("memory:/type-model.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                TYPE MODEL T AT "https://example.interlis.ch" VERSION "1.0" =
                END T.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("T",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        ModelSymbol model = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast)
                .findFirst().orElseThrow();
        assertEquals(ModelKind.TYPE, model.modelKind());
    }

    private static CompilationOutcome compileProfile(String version, String prefix, String body) {
        var source = CompilerTestSupport.source("memory:/model-profile.ili", "INTERLIS " + version + "; " + prefix
                + " MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.");
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),CancellationToken.none());
    }

    @Test void typeModelsRejectObjectAndRuntimeDeclarationsButKeepTypeFamilies() {
        for(var version:List.of("2.3","2.4")) {
            org.junit.jupiter.api.Assertions.assertInstanceOf(CompilationOutcome.Succeeded.class,compileProfile(version,"CONTRACTED TYPE",
                    "UNIT Distance; DOMAIN D=0..10; FUNCTION f(v:BOOLEAN):BOOLEAN;"));
            org.junit.jupiter.api.Assertions.assertInstanceOf(CompilationOutcome.Succeeded.class,compileProfile(version,"TYPE","STRUCTURE S=value:TEXT; END S;"));
            org.junit.jupiter.api.Assertions.assertInstanceOf(CompilationOutcome.Succeeded.class,compileProfile(version,"TYPE","CLASS C (ABSTRACT)=END C;"));
            for(var invalid:List.of("CLASS C=END C;","TOPIC T=END T;","PARAMETER p:BOOLEAN;")) {
                var rejected=org.junit.jupiter.api.Assertions.assertInstanceOf(CompilationOutcome.Rejected.class,compileProfile(version,"CONTRACTED TYPE",invalid));
                assertTrue(rejected.analysis().diagnostics().toString().contains("TYPE_MODEL_CONTENT"));
                org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
            }
        }
    }

    @Test void typeModelsAllowReferenceSystemBasketsButKeepSignBasketsRejected() {
        for(String version:List.of("2.3","2.4"))for(boolean referenceSystem:List.of(true,false)) {
            String kind=referenceSystem?"REFSYSTEM":"SIGN",modelKind=referenceSystem?"REFSYSTEM":"SYMBOLOGY";
            var target=CompilerTestSupport.source("memory:/type-model-basket-target.ili","INTERLIS "+version+"; CONTRACTED "+modelKind+" MODEL R AT \"https://example.org\" VERSION \"1\"=TOPIC T=CLASS S EXTENDS INTERLIS."+kind+"=END S;END T;END R.");
            var source=CompilerTestSupport.source("memory:/type-model-baskets.ili","INTERLIS "+version+";CONTRACTED TYPE MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS R;"+kind+" BASKET B~R.T OBJECTS OF S:obj;END F.");
            var binding=CompilerTestSupport.binding(CompilerTestSupport.parse(source),"R",target,CompilerTestSupport.parse(target));
            var outcome=CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source,target.id(),target),List.of(binding),CompilerOptions.defaults()),CancellationToken.none());
            if(referenceSystem) {
                var result=assertInstanceOf(CompilationOutcome.Succeeded.class,outcome,()->outcome instanceof CompilationOutcome.Rejected rejected?rejected.analysis().diagnostics().toString():outcome.toString());
                var basket=result.schema().semanticGraph().symbols().stream().filter(MetadataBasketSymbol.class::isInstance).map(MetadataBasketSymbol.class::cast).filter(candidate->candidate.name().value().equals("B")).findFirst().orElseThrow();
                assertEquals(MetadataBasketSymbol.Kind.REFSYSTEM,basket.basketKind());assertEquals(1,basket.objects().size());assertTrue(basket.topic().isPresent());
                int offset=source.text().indexOf("BASKET B")+7;
                assertEquals(basket.id(),LanguageQueries.create().symbolAt(result.analysis(),source.range(offset,offset),CancellationToken.none()).known().orElseThrow().symbol());
            } else {
                var result=assertInstanceOf(CompilationOutcome.Rejected.class,outcome);
                assertTrue(result.analysis().diagnostics().toString().contains("TYPE_MODEL_CONTENT"));
            }
        }
    }

    @Test void typeModelPermittedFamiliesExposePublicSymbolsAndQueries() {
        for(var version:List.of("2.3","2.4")) {
            var source=CompilerTestSupport.source("memory:/type-model-families.ili",
                    "INTERLIS "+version+"; CONTRACTED TYPE MODEL F AT \"https://example.org\" VERSION \"1\" = "
                            +"UNIT Distance; DOMAIN D=0..10; FUNCTION f(v:BOOLEAN):BOOLEAN; END F.");
            var request=CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
            var result=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request,CancellationToken.none()));
            var graph=result.schema().semanticGraph();
            var model=(ModelSymbol)graph.symbols().stream().filter(ModelSymbol.class::isInstance).findFirst().orElseThrow();
            assertEquals(ModelKind.TYPE,model.modelKind());
            for(var expected:List.of("Distance","D","f")) {
                var symbol=graph.symbols().stream().filter(s->s.name().value().equals(expected)).findFirst().orElseThrow();
                String needle=expected.equals("Distance")?"UNIT Distance":expected.equals("D")?"DOMAIN D":"FUNCTION f";
                var offset=source.text().indexOf(needle)+needle.length()-expected.length();
                var handle=LanguageQueries.create().symbolAt(result.analysis(),source.range(offset,offset),CancellationToken.none()).known().orElseThrow();
                assertEquals(symbol.id(),handle.symbol(),version+":"+expected);
            }
            var distance=graph.symbols().stream().filter(UnitSymbol.class::isInstance).findFirst().orElseThrow();
            assertTrue(graph.symbol(distance.id()).isPresent());
            var domain=(DomainSymbol)graph.symbols().stream().filter(DomainSymbol.class::isInstance).findFirst().orElseThrow();
            assertTrue(graph.type(domain.typeId()).isPresent());
            var function=(FunctionSymbol)graph.symbols().stream().filter(FunctionSymbol.class::isInstance).findFirst().orElseThrow();
            assertTrue(graph.type(function.returnType()).isPresent());
        }
    }
    @Test void symbologyContractedRequirementIsVersionedWhileAdvisoryFamiliesStayAdvisory() {
        for(var version:List.of("2.3","2.4")) {
            for(var prefix:List.of("CONTRACTED SYMBOLOGY","REFSYSTEM",""))
                org.junit.jupiter.api.Assertions.assertInstanceOf(CompilationOutcome.Succeeded.class,compileProfile(version,prefix,"TOPIC T=CLASS C=END C; END T;"));
            var result=compileProfile(version,"SYMBOLOGY","");
            if(version.equals("2.3")) {
                var rejected=org.junit.jupiter.api.Assertions.assertInstanceOf(CompilationOutcome.Rejected.class,result);
                assertTrue(rejected.analysis().diagnostics().toString().contains("SYMBOLOGY_CONTRACTED"));
            } else org.junit.jupiter.api.Assertions.assertInstanceOf(CompilationOutcome.Succeeded.class,result);
        }
    }

    @Test void documentaryLanguageRemainsPublicAndKeepsItsSourceOrigin() {
        for(var language:List.of("de_CH","fr","LocalVocabulary")) {
            var text="INTERLIS 2.4; MODEL F ("+language+") AT \"https://example.org\" VERSION \"1\" = END F.";
            var source=CompilerTestSupport.source("memory:/language.ili",text);
            var result=org.junit.jupiter.api.Assertions.assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(
                    CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),CancellationToken.none()));
            var model=(ModelSymbol)result.schema().semanticGraph().symbol(result.schema().semanticGraph().rootModels().getFirst()).orElseThrow();
            assertEquals(language,model.options().language().orElseThrow().value());
            assertEquals(java.util.Optional.of(language),result.analysis().syntaxSnapshots().get(source.id()).root().models().getFirst().language());
            var range=((guru.interlis.model.Origin.DeclaredOrigin)model.options().language().orElseThrow().origin()).range();
            assertEquals("("+language+")",source.slice(range));
        }
    }
}
