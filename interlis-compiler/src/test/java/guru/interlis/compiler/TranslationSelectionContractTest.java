package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslationSelectionContractTest {
    static final CancellationToken TOKEN=CancellationToken.none();
    static String model(String version,String name,String translation,String body) {
        return "INTERLIS "+version+";MODEL "+name+" AT \"https://example.org\" VERSION \"1\" "+translation+"="+body+"END "+name+".";
    }
    static SourceText source(String id,String text) { return new SourceText(new SourceId(id),new SourceRevision(1),text); }
    static ModelLocation location(SourceText source) {
        var syntax=SyntaxServices.create().parse(source,ParseOptions.defaults(ParseMode.EDITOR),TOKEN).root().models().getFirst();
        return new ModelLocation(source.id(),source.revision(),syntax.name(),Optional.of(syntax.modelVersion()),syntax.range().startOffset());
    }
    static CompilationRequest request(List<SourceText> sources,TranslationBinding... bindings) {
        var data=new LinkedHashMap<SourceId,SourceText>();sources.forEach(s->data.put(s.id(),s));
        return new CompilationRequest(List.of(new ModelSelector.BySource(sources.getFirst().id())),
                SourceSet.of(data,List.of(),List.of(),ResolutionFingerprint.none(),List.of(bindings)),CompilerOptions.defaults());
    }
    @Test void actualOriginalIdentitySurvivesTwoSameNamedVersionsAndAffectsTheInputFingerprint() {
        for(String version:List.of("2.3","2.4")) {
            var root=source("memory:/f",model(version,"F","TRANSLATION OF Original[\"1\"]","DOMAIN D=TEXT;"));
            var original=source("memory:/original",model(version,"Original","","DOMAIN D=TEXT;"));
            var other=source("memory:/other",model(version,"Original","","DOMAIN D=TEXT*7;"));
            var binding=new TranslationBinding(location(root),location(original));
            var input=request(List.of(root,original,other),binding);
            var out=CompilerEngines.create().compile(input,TOKEN);
            var ok=assertInstanceOf(CompilationOutcome.Succeeded.class,out);
            assertEquals(input.fingerprint(),ok.analysis().fingerprint());
            assertNotEquals(input.fingerprint(),request(List.of(root,original,other),new TranslationBinding(location(root),location(other))).fingerprint());
            assertThrows(UnsupportedOperationException.class,()->input.sources().translations().clear());
        }
    }
    @Test void fingerprintSeparatesNestedFieldsInsteadOfRecordTextAndIncludesResolutionDiagnostics() {
        String separator="], revision=SourceRevision[value=1], modelName=Original, modelVersion=Optional[";
        var a=new ModelLocation(new SourceId("memory:/a"+separator+"x"),new SourceRevision(1),"Original",Optional.of("v"),14);
        var b=new ModelLocation(new SourceId("memory:/a"),new SourceRevision(1),"Original",Optional.of("x"+separator+"v"),14);
        assertNotEquals(a,b);assertEquals(a.toString(),b.toString());
        var root=source("memory:/f",model("2.4","F","",""));
        assertNotEquals(request(List.of(root),new TranslationBinding(location(root),a)).fingerprint(),
                request(List.of(root),new TranslationBinding(location(root),b)).fingerprint());
        String hint="], sourceHint=Optional[SourceId[value=";
        var first=new ModelSelector.ByName("F",Optional.of("x"+hint+"y"),Optional.of(new SourceId("z")));
        var second=new ModelSelector.ByName("F",Optional.of("x"),Optional.of(new SourceId("y"+hint+"z")));
        assertNotEquals(first,second);assertEquals(first.toString(),second.toString());
        var input=request(List.of(root));
        assertNotEquals(new CompilationRequest(List.of(first),input.sources(),input.options()).fingerprint(),
                new CompilationRequest(List.of(second),input.sources(),input.options()).fingerprint());
        var range=new SourceRange(root.id(),root.revision(),0,1);var site=new ImportSite(range,"F","Original");
        var sources=Map.of(root.id(),root);
        var left=SourceSet.of(sources,List.of(new ImportBinding(site,new ImportResolution.Resolved(a))),List.of(),ResolutionFingerprint.none());
        var right=SourceSet.of(sources,List.of(new ImportBinding(site,new ImportResolution.Resolved(b))),List.of(),ResolutionFingerprint.none());
        assertNotEquals(new CompilationRequest(input.roots(),left,input.options()).fingerprint(),new CompilationRequest(input.roots(),right,input.options()).fingerprint());
        var d1=diagnostic(List.of("a, b"));var d2=diagnostic(List.of("a","b"));assertNotEquals(d1,d2);assertEquals(d1.toString(),d2.toString());
        left=SourceSet.of(sources,List.of(),List.of(d1),ResolutionFingerprint.none());right=SourceSet.of(sources,List.of(),List.of(d2),ResolutionFingerprint.none());
        assertNotEquals(new CompilationRequest(input.roots(),left,input.options()).fingerprint(),new CompilationRequest(input.roots(),right,input.options()).fingerprint());
    }
    private static guru.interlis.diagnostics.Diagnostic diagnostic(List<String> candidates) {
        return new guru.interlis.diagnostics.Diagnostic(new guru.interlis.diagnostics.DiagnosticId("diag-probe"),
                new guru.interlis.diagnostics.DiagnosticCode("ILI-RES-0006"),guru.interlis.diagnostics.Severity.ERROR,
                guru.interlis.diagnostics.Phase.SOURCE_RESOLUTION,"diagnostic.resolution.overlay.ambiguous",
                List.of(new guru.interlis.diagnostics.DiagnosticArgument.TextArgument("overlay","project"),
                        new guru.interlis.diagnostics.DiagnosticArgument.IdentifierArgument("model","Original"),
                        new guru.interlis.diagnostics.DiagnosticArgument.IdentifierListArgument("candidates",candidates)),
                Optional.empty(),List.of(),Optional.empty(),List.of(),Optional.empty(),Optional.empty(),List.of());
    }
    @Test void staleOrInventedOriginalBindingsCannotFallBackToAnOtherwiseValidOriginal() {
        var root=source("memory:/f",model("2.4","F","TRANSLATION OF Original[\"1\"]","DOMAIN D=TEXT;"));
        var original=source("memory:/original",model("2.4","Original","","DOMAIN D=TEXT;"));
        var correct=location(original);
        var wrongRevision=new ModelLocation(correct.source(),new SourceRevision(99),correct.modelName(),correct.modelVersion(),correct.declarationOffset());
        var wrongOffset=new ModelLocation(correct.source(),correct.revision(),correct.modelName(),correct.modelVersion(),correct.declarationOffset()+1);
        var wrongDependent=new ModelLocation(root.id(),new SourceRevision(99),"F",Optional.of("1"),location(root).declarationOffset());
        for(var binding:List.of(new TranslationBinding(location(root),wrongRevision),new TranslationBinding(location(root),wrongOffset),new TranslationBinding(wrongDependent,correct))) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(List.of(root,original),binding),TOKEN));
            assertEquals(Set.of(RejectionReason.MISSING_INPUT),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-RES-0007")&&d.primaryRange().isEmpty()));
        }
    }
    @Test void contradictoryAndConflictingSelectionsRemainInvalidResolvedInput() {
        var root=source("memory:/f",model("2.4","F","TRANSLATION OF Original[\"1\"]","DOMAIN D=TEXT;"));
        var original=source("memory:/original",model("2.4","Original","","DOMAIN D=TEXT;"));
        var other=source("memory:/other",model("2.4","Other","","DOMAIN D=TEXT;"));
        var correct=new TranslationBinding(location(root),location(original));var wrong=new TranslationBinding(location(root),location(other));
        for(var input:List.of(request(List.of(root,original,other),wrong),request(List.of(root,original,other),correct,wrong))) {
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input,TOKEN));
            assertTrue(rejected.rejection().reasons().contains(RejectionReason.MISSING_INPUT));
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-RES-0007")));
        }
    }
}
