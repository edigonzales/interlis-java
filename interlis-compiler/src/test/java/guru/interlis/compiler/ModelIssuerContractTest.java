package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.QualifiedName;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The approved URI-reference profile is syntactic and requires no host access. */
class ModelIssuerContractTest {
    private static CompilationOutcome compile(String version,String issuer) {
        var source=CompilerTestSupport.source("memory:/issuer.ili","INTERLIS "+version+";MODEL F AT \""+issuer+"\" VERSION \"1\"=END F.");
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),CancellationToken.none());
    }
    @Test void absoluteAndRelativeReferencesPreserveTheirExactPublicIssuer() {
        for(String version:List.of("2.3","2.4"))for(String issuer:List.of("x","../models?x=1#v","https://example.invalid/a%20b","urn:example:model","mailto:test@example.invalid","","a".repeat(1023))) {
            var out=assertInstanceOf(CompilationOutcome.Succeeded.class,compile(version,issuer),version+issuer);
            var ref=out.schema().queries().findQualifiedName(QualifiedName.of("F")).getFirst();
            assertEquals(issuer,((ModelSymbol)out.schema().queries().symbol(ref).known().orElseThrow()).issuer());
        }
    }
    @Test void invalidSyntaxNonAsciiAndOverlengthAreSourceRejections() {
        for(String version:List.of("2.3","2.4"))for(String issuer:List.of("a b","%xy","https://[broken","https://example.invalid/ä","a".repeat(1024))) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,compile(version,issuer),version+issuer);
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            var diagnostic=out.analysis().diagnostics().stream().filter(d->d.code().value().equals("ILI-SEM-0010")&&d.arguments().toString().contains("MODEL_ISSUER")).findFirst().orElseThrow();
            assertEquals("memory:/issuer.ili",diagnostic.primaryRange().orElseThrow().source().value());
        }
    }
}
