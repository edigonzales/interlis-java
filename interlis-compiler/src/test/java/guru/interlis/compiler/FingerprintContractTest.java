package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FingerprintContractTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Fingerprint AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Item =
                  value : 0..9;
                END Item;
              END Data;
            END Fingerprint.
            """;

    @Test
    void sameSemanticInputHasSameSnapshotIdAndChangedContentChangesIt() {
        SourceText first = source(MODEL);
        CompilationOutcome.Succeeded a = compile(first);
        CompilationOutcome.Succeeded b = compile(first);
        assertEquals(a.schema().snapshotId(), b.schema().snapshotId());
        SourceText changed = source(MODEL.replace("value : 0..9;", "value : 0..99;"));
        CompilationOutcome.Succeeded c = compile(changed);
        assertNotEquals(a.schema().snapshotId(), c.schema().snapshotId());
    }

    private static SourceText source(String text) {
        return new SourceText(new SourceId("memory:/fingerprint.ili"),
                new SourceRevision(1), text);
    }

    private static CompilationOutcome.Succeeded compile(SourceText source) {
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Fingerprint", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
