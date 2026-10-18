package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ConformanceRulesContractTest {
    @Test
    void duplicateTopicNamesAreRejected() {
        assertRejected("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T = END T;
                  TOPIC T = END T;
                END M.
                """);
    }

    @Test
    void modelEndNameMustMatch() {
        assertRejected("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                END Other.
                """);
    }

    @Test
    void extendedTextMayNotWidenMaximumLength() {
        assertRejected("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  STRUCTURE Base =
                    value : MANDATORY TEXT*20;
                  END Base;
                  STRUCTURE Derived EXTENDS Base =
                    value (EXTENDED) : MANDATORY TEXT;
                  END Derived;
                END M.
                """);
    }

    private static void assertRejected(String text) {
        SourceText source = new SourceText(new SourceId("memory:/rules.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("M",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
