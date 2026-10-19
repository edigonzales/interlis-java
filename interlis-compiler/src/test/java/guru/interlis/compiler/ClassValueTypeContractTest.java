package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassValueType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClassValueTypeContractTest {
    @Test
    void classConstantIsComparableWithClassValueType() {
        SourceText source = new SourceText(new SourceId("memory:/class-constant.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL C AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = END A;
                    CLASS B =
                      value : CLASS;
                      MANDATORY CONSTRAINT value == >A;
                    END B;
                  END T;
                END C.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("C",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }

    @Test
    void classValueTypeIsModeled() {
        SourceText source = new SourceText(new SourceId("memory:/class-value.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL C AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = value : CLASS; END A;
                  END T;
                END C.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("C",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        assertInstanceOf(ClassValueType.class, succeeded.analysis().semanticGraph().types().stream()
                .filter(ClassValueType.class::isInstance).findFirst().orElseThrow());
    }
}
