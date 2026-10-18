package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** External list/map mutation must not change a request or analysis snapshot. */
class SnapshotOwnershipTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Owned AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Item =
                  value : 0..9;
                END Item;
              END Data;
            END Owned.
            """;

    @Test
    void mutatingCallerCollectionsDoesNotChangeCompilation() {
        SourceText source = new SourceText(new SourceId("memory:/owned.ili"),
                new SourceRevision(1), MODEL);
        Map<SourceId, SourceText> sources = new LinkedHashMap<>();
        sources.put(source.id(), source);
        List<ModelSelector> roots = new ArrayList<>();
        roots.add(new ModelSelector.ByName("Owned", Optional.empty(), Optional.empty()));
        CompilationRequest request = new CompilationRequest(roots,
                SourceSet.of(sources), CompilerOptions.defaults());
        sources.clear();
        roots.clear();
        CompilationOutcome outcome = CompilerEngines.create().compile(request,
                CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Succeeded.class, outcome);
        CompilationOutcome.Succeeded succeeded = (CompilationOutcome.Succeeded) outcome;
        assertTrue(succeeded.schema().models().size() == 1);
        assertTrue(succeeded.analysis().sourceRevisions().containsKey(source.id()));
    }
}
