import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal public-SDK example; compiled and run against the delivered jars. */
public final class SdkExample {
    private SdkExample() {
    }

    public static void main(String[] args) {
        SourceText source = new SourceText(new SourceId("memory:/sdk-example.ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL SdkExample AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
                    CLASS Item =
                      label : TEXT*40;
                    END Item;
                  END Data;
                END SdkExample.
                """);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("SdkExample", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        CompilationOutcome outcome = CompilerEngines.create()
                .compile(request, CancellationToken.none());
        if (!(outcome instanceof CompilationOutcome.Succeeded succeeded)) {
            throw new IllegalStateException("SDK example compile failed: " + outcome);
        }
        System.out.println("SDK PASS " + succeeded.schema().snapshotId().value()
                + " models=" + succeeded.schema().models().size());
    }
}
