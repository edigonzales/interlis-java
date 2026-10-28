package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.CancellationReason;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import guru.interlis.model.LanguageVersion;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** Exact numeric resource limits are cancellation, not an unsupported-language result. */
class NumericLiteralBudgetContractTest {
    @Test void oversizedThresholdsRangesAndConstantsUseTheExistingBudgetOutcome() {
        for(String v:List.of("2.3","2.4"))for(String number:List.of("1e100001","1e-100001","1e999999999999999999999","1".repeat(4097)))
            for(String body:List.of("DOMAIN D=0.."+number+";","TOPIC T=CLASS C=CONSTRAINT >= "+number+"% #true;END C;END T;","TOPIC T=CLASS C=MANDATORY CONSTRAINT "+number+">0;END C;END T;")) {
                var s=source("memory:/numeric-budget.ili","INTERLIS "+v+";MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
                var r=assertInstanceOf(CompilationOutcome.Cancelled.class,CompilerEngines.create().compile(request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults()),CancellationToken.none()));
                assertEquals(CancellationReason.RESOURCE_LIMIT,r.reason());
            }
    }
    @Test void fragmentLimitsAreReportedWithoutAnEscapingException() {
        for(String v:List.of("2.3","2.4")) {
            var s=source("memory:/numeric-fragment.ili","1e99999999999999999999");
            var r=SyntaxServices.create().parseFragment(s,s.range(0,s.length()),FragmentKind.EXPRESSION,v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,CancellationToken.none());
            assertFalse(r.consumedEntireFragment());assertEquals(1,r.diagnostics().size());assertEquals("ILI-INF-0002",r.diagnostics().getFirst().code().value());
        }
    }
}
