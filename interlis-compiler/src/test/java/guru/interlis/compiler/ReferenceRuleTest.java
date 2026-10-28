package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceRuleTest {
    private static final String BASE = """
            INTERLIS 2.4;
            MODEL Base AT "https://example.interlis.ch" VERSION "1.0" =
              DOMAIN Count = 0..999;
              TOPIC Common = OID AS INTERLIS.UUIDOID;
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
                ASSOCIATION Link =
                  local -- {1} Building;
                  target (EXTERNAL) -- {1} Base.Common.BaseBuilding;
                END Link;
              END Data;
            END Survey.
            """;

    @Test
    void referenceValidOverrideAndExternalRoleCompile() {
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile(SURVEY));
    }

    @Test
    void overrideWithoutExtendedIsRejected() {
        String text = SURVEY.replace("height (EXTENDED) :", "height :");
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class, compile(text));
        assertTrue(rejected.analysis().diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-SEM-0010")));
    }

    @Test
    void crossTopicRoleWithoutExternalIsRejected() {
        String text = SURVEY.replace("target (EXTERNAL) --", "target --");
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class, compile(text));
        assertTrue(rejected.analysis().diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-SEM-0010")));
    }

    private static CompilationOutcome compile(String surveyText) {
        SourceText base = CompilerTestSupport.source("memory:/rule-base.ili", BASE);
        SourceText survey = CompilerTestSupport.source("memory:/rule-survey.ili", surveyText);
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        CompilationRequest request = CompilerTestSupport.request(surveySyntax, baseSyntax,
                CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }
}
