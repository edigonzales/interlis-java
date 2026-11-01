package guru.interlis.cli;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class P3OutcomePriorityContractTest {
    @Test void everyNonemptyCombinationKeepsTheSpecifiedHostPriorityAndAllReasons() {
        var source = new SourceText(new SourceId("memory:/priority.ili"), new SourceRevision(1),
                "INTERLIS 2.4; MODEL F AT \"https://example.org\" VERSION \"1\" = END F.");
        var root = new ModelSelector.BySource(source.id());
        var analysis = CompilerEngines.create().analyze(new CompilationRequest(List.of(root),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults()),
                AnalysisReuse.none(), CancellationToken.none());
        var possible = RejectionReason.values();
        assertEquals(6, possible.length);
        for (int mask = 1; mask < (1 << possible.length); mask++) {
            var reasons = EnumSet.noneOf(RejectionReason.class);
            for (int index = 0; index < possible.length; index++)
                if ((mask & (1 << index)) != 0) reasons.add(possible[index]);
            var summary = new RejectionSummary(reasons, List.of(), List.of(root), List.of());
            var rejected = new CompilationOutcome.Rejected(analysis, summary);
            var expected = reasons.contains(RejectionReason.ENVIRONMENT_FAILURE) ? CliTermination.OPERATIONAL_FAILURE
                    : reasons.contains(RejectionReason.INCOMPLETE_SUPPORT) ? CliTermination.INCOMPLETE_ANALYSIS
                    : reasons.contains(RejectionReason.MISSING_INPUT) || reasons.contains(RejectionReason.AMBIGUOUS_INPUT)
                    ? CliTermination.INPUT_UNRESOLVED
                    : reasons.contains(RejectionReason.SOURCE_ERRORS) ? CliTermination.SOURCE_REJECTED
                    : CliTermination.WARNINGS_REJECTED;
            assertEquals(expected, new CliOutcomeMapper().classify(rejected), reasons.toString());
            assertEquals(reasons, rejected.rejection().reasons());
        }
    }
}
