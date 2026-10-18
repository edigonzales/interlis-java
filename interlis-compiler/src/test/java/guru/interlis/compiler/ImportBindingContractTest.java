package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportBindingContractTest {
    private static final String BASE = """
            INTERLIS 2.4;
            MODEL Base AT "https://example.interlis.ch" VERSION "1.0" =
              DOMAIN Count = 0..999;
              TOPIC Common =
                CLASS BaseBuilding =
                  height : Count;
                END BaseBuilding;
              END Common;
            END Base.
            """;
    private static final String SURVEY = """
            INTERLIS 2.4;
            MODEL Survey AT "https://example.interlis.ch" VERSION "1.0" =
              IMPORTS Base;
              TOPIC Data =
    DEPENDS ON Base.Common;
                CLASS Building EXTENDS Base.Common.BaseBuilding =
                  height (EXTENDED) : Base.Count;
                END Building;
              END Data;
            END Survey.
            """;

    @Test
    void staleTargetRevisionIsRejected() {
        SourceText base = CompilerTestSupport.source("memory:/stale-base.ili", BASE);
        SourceText survey = CompilerTestSupport.source("memory:/stale-survey.ili", SURVEY);
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        ImportBinding correct = CompilerTestSupport.binding(surveySyntax, "Base", base, baseSyntax);
        ImportResolution.Resolved resolved = (ImportResolution.Resolved) correct.resolution();
        ImportBinding stale = new ImportBinding(correct.site(),
                new ImportResolution.Resolved(new ModelLocation(resolved.target().source(),
                        new SourceRevision(resolved.target().revision().value() + 1),
                        resolved.target().modelName(), resolved.target().modelVersion(),
                        resolved.target().declarationOffset())));
        Map<SourceId, SourceText> sources = new LinkedHashMap<>();
        sources.put(base.id(), base);
        sources.put(survey.id(), survey);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Survey", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(sources, List.of(stale), List.of(),
                        new ResolutionFingerprint("test-resolver/stale")),
                CompilerOptions.defaults());
        CompilationOutcome outcome = CompilerEngines.create().compile(request,
                CancellationToken.none());
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class, outcome);
        assertTrue(rejected.analysis().diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-RES-0004")
                        || d.code().value().equals("ILI-RES-0001")));
    }
}
