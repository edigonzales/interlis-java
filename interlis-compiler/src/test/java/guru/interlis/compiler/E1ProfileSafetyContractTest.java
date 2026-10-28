package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class E1ProfileSafetyContractTest {
    @Test void completeDocumentsConsumeAllTokensAfterTheirModels() {
        for(var version:List.of("2.3","2.4")) {
            String valid=text(version,"TOPIC T=CLASS C=END C; END T;");
            for(String trailing:List.of("TRAILING","INTERLIS "+version+"; MODEL Ignored AT \"https://example.org\" VERSION \"1\"=END Ignored.",";")) {
                var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(valid+trailing,CompilerOptions.defaults()),TOKEN));
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());
                assertTrue(r.analysis().diagnostics().stream().anyMatch(d->d.code().value().startsWith("ILI-SYN-")));
            }
            assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(input(valid+"\n!! trailing comment\n /* block */",CompilerOptions.defaults()),TOKEN));
        }
    }
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static String text(String version,String body) {return "INTERLIS "+version+"; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = "+body+" END F.";}
    private static CompilationRequest input(String text,CompilerOptions options) {var s=source("memory:/safety.ili",text);return request(List.of("F"),Map.of(s.id(),s),List.of(),options);}
    private static CompilationOutcome.Succeeded ok(String version,String body) {
        var result=CompilerEngines.create().compile(input(text(version,body),CompilerOptions.defaults()),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
    }
    @Test void temporalKeywordsAre24SyntaxAndBothVersionsKeepTheirFormatDomainReferences() {
        for(var version:List.of("2.3","2.4")) for(var keyword:List.of("DATE","TIMEOFDAY","DATETIME")) {
            var body="TOPIC T=CLASS C=value:"+keyword+"; END C; END T;";
            for(var mode:ParseMode.values()) {
                var parsed=SyntaxServices.create().parse(source("memory:/date.ili",text(version,body)),ParseOptions.defaults(mode),TOKEN);
                assertEquals(version.equals("2.3") && keyword.equals("DATE"),parsed.diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SYN-0006")));
            }
            if(version.equals("2.4"))ok(version,body);
            else assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(text(version,body),CompilerOptions.defaults()),TOKEN));
            ok(version,body.replace(keyword,"INTERLIS."+switch(keyword){case "DATE"->"XMLDate";case "DATETIME"->"XMLDateTime";default->"XMLTime";}));
        }
    }
    @Test void repeatedPropertiesAreVisibleWarningsRatherThanHiddenSetDeduplication() {
        var bodies=List.of("TOPIC T(ABSTRACT,ABSTRACT)=END T;","TOPIC T=CLASS C(FINAL,FINAL)=END C; END T;",
                "STRUCTURE S(FINAL,FINAL)=END S;","DOMAIN D(FINAL,FINAL)=0..10;",
                "TOPIC T=CLASS C=value(FINAL,FINAL):TEXT; END C; END T;",
                "TOPIC T=OID AS INTERLIS.STANDARDOID; CLASS A=END A; CLASS B=END B; ASSOCIATION R=a(ORDERED,ORDERED)--{*}A; b--B; END R; END T;");
        for(var version:List.of("2.3","2.4")) for(var body:bodies) {
            var result=ok(version,body);var diagnostics=result.analysis().diagnostics();
            assertTrue(diagnostics.stream().anyMatch(d->d.code().value().equals("ILI-SYN-0007")&&d.severity()==guru.interlis.diagnostics.Severity.WARNING),diagnostics::toString);
            assertTrue(diagnostics.stream().noneMatch(d->d.severity()==guru.interlis.diagnostics.Severity.ERROR));
            var defaults=CompilerOptions.defaults();var strict=new CompilerOptions(defaults.semanticProfile(),defaults.allowedLanguages(),WarningPolicy.REJECT_WARNINGS,defaults.budget(),defaults.overlays());
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(text(version,body),strict),TOKEN));
            assertEquals(Set.of(RejectionReason.WARNINGS_REJECTED),rejected.rejection().reasons());
        }
    }
    @Test void explicitAndImplicitClassOrStructureExtensionCannotBeCombined() {
        for(var version:List.of("2.3","2.4"))for(var kind:List.of("CLASS","STRUCTURE")) {
            var body="TOPIC Base="+kind+" C=END C; END Base; TOPIC T EXTENDS Base="+kind+" C(EXTENDED) EXTENDS Base.C=END C; END T;";
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(text(version,body),CompilerOptions.defaults()),TOKEN));
            assertTrue(result.analysis().diagnostics().toString().contains("EXTENDED_EXPLICIT_BASE"));
            ok(version,body.replace(" EXTENDS Base.C",""));
        }
    }
    @Test void syntaxRecoveryDoesNotHideInvalidFunctionArguments() {
        for(var version:List.of("2.3","2.4")) {
            var body="TOPIC T=CLASS C=value:; END C; CLASS D=MANDATORY CONSTRAINT INTERLIS.convertUnit(#true)>0; END D; END T;";
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(text(version,body),CompilerOptions.defaults()),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
            assertTrue(result.analysis().diagnostics().stream().filter(d->d.code().value().equals("ILI-IMPL-0001")).allMatch(d->!d.arguments().toString().contains("MISSING_ATTRIBUTE_TYPE")));
            var diagnostics=result.analysis().diagnostics().stream().map(guru.interlis.diagnostics.Diagnostic::id).collect(java.util.stream.Collectors.toSet());
            for(var type:result.analysis().semanticGraph().types())if(type instanceof ErrorType error)assertTrue(diagnostics.containsAll(error.diagnosticIds()));
        }
    }
}
