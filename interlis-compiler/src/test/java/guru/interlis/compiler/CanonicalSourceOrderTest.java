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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** DIAG-008/TEST: source-map insertion order must not change the canonical result. */
class CanonicalSourceOrderTest {
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
    void sourceMapOrderDoesNotChangeSnapshotOrDiagnostics() {
        SourceText base = CompilerTestSupport.source("memory:/order-base.ili", BASE);
        SourceText survey = CompilerTestSupport.source("memory:/order-survey.ili", SURVEY);
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        ImportBinding binding = CompilerTestSupport.binding(surveySyntax, "Base", base, baseSyntax);

        Map<SourceId, SourceText> first = new LinkedHashMap<>();
        first.put(base.id(), base);
        first.put(survey.id(), survey);
        Map<SourceId, SourceText> second = new LinkedHashMap<>();
        second.put(survey.id(), survey);
        second.put(base.id(), base);

        CompilationOutcome firstOutcome = compile(first, binding);
        CompilationOutcome secondOutcome = compile(second, binding);
        CompilationOutcome.Succeeded firstSuccess = assertInstanceOf(
                CompilationOutcome.Succeeded.class, firstOutcome);
        CompilationOutcome.Succeeded secondSuccess = assertInstanceOf(
                CompilationOutcome.Succeeded.class, secondOutcome);
        assertEquals(firstSuccess.schema().snapshotId(), secondSuccess.schema().snapshotId());
        assertEquals(firstSuccess.schema().models(), secondSuccess.schema().models());
    }

    private static CompilationOutcome compile(Map<SourceId, SourceText> sources,
                                               ImportBinding binding) {
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Survey", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(sources, List.of(binding), List.of(),
                        new ResolutionFingerprint("test-resolver/canonical")),
                CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }
}
