package guru.interlis.gui;

import guru.interlis.execution.RunContextFactory;
import guru.interlis.execution.RunEvent;
import guru.interlis.gui.application.CompileRequest;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.application.SessionInputs;
import guru.interlis.gui.application.SessionOptions;
import guru.interlis.gui.presentation.DiagnosticPresenter;
import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeTextRenderingTest {
    @TempDir
    Path directory;

    @Test
    void sourceDisplayAndRunLogRemoveCredentialsQueriesAndFragments() {
        String source = "https://user:password@example.test/model.ili?token=secret#private";
        assertEquals("https://example.test/model.ili", DiagnosticPresenter.displaySource(new SourceId(source)));
        var context = RunContextFactory.defaults().createRoot();
        var request = new CompileRequest(context, 1,
                new SessionInputs(List.of(directory.resolve("model.ili")), List.of()),
                SessionOptions.defaults(), directory);
        var header = new RunEvent.RunEventHeader(context.runId(), Optional.empty(), 1, 1, 0, 0);
        var resolved = new RunEvent.SourceResolved(header, source, "repository", false, 0);
        var longValue = new RunEvent.SourceResolved(header, "https://example.test/" + "x".repeat(500),
                "repository", false, 0);
        var result = new CompileResult(request, Instant.EPOCH, Duration.ZERO, new CompileResult.Failed(),
                Map.of(), Optional.empty(), Optional.empty(), List.of(), List.of(resolved, longValue));
        String log = RunLog.text(result);
        assertTrue(log.contains("https://example.test/model.ili\n"));
        assertFalse(log.contains("password") || log.contains("secret") || log.contains("private"));
        assertTrue(log.lines().reduce((first, second) -> second).orElseThrow().endsWith("…"));
    }
}
