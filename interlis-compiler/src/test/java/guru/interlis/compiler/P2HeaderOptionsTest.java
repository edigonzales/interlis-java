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

class P2HeaderOptionsTest {
    private static CompilationOutcome compile(String text, String model) {
        SourceText source = new SourceText(new SourceId("memory:/header-options.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(
                new ModelSelector.ByName(model, Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }

    @Test
    void modelHeaderOptionsDoNotBlockCompile() {
        String text = """
                INTERLIS 2.4;
                MODEL M NOINCREMENTALTRANSFER AT "x" VERSION "1" =
                  CHARSET "UTF-8";
                  XMLNS "https://example.interlis.ch";
                  TOPIC T =
                    CLASS C =
                      value : TEXT*10;
                    END C;
                  END T;
                END M.
                """;
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile(text, "M"));
    }
}
