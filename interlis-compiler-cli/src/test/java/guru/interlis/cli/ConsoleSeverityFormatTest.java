package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleSeverityFormatTest {
    @Test void everySeverityPrecedesCodeAndOptionalRevisionFaithfulLocationInBothLanguages() {
        var id = new SourceId("memory:/😀.ili");
        var source = new SourceText(id, new SourceRevision(7), "😀\r\n  Missing");
        var range = new SourceRange(id, source.revision(), 6, 13);
        var renderer = new ConsoleDiagnosticRenderer();
        for (var severity : Severity.values()) for (var language : DiagnosticLanguage.values()) {
            for (var position : List.of(Optional.<SourceRange>empty(), Optional.of(range))) {
                var diagnostic = new Diagnostic(new DiagnosticId("format"), new DiagnosticCode("ILI-SEM-0001"),
                        severity, Phase.BINDING, "diagnostic.semantics.unresolved",
                        List.of(new DiagnosticArgument.IdentifierArgument("name", "Missing\n\u001b")), position,
                        List.of(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(), List.of());
                for (boolean color : List.of(false, true)) {
                    String rendered = renderer.render(diagnostic, Map.of(id, source), language,
                            new RenderOptions(color, 0, 0, 160, false));
                    String plain = rendered.replaceAll("\u001b\\[[0-9;]*m", "");
                    assertTrue(plain.startsWith(severity.name() + " [ILI-SEM-0001] "), plain);
                    if (position.isPresent()) assertTrue(plain.startsWith(severity.name() + " [ILI-SEM-0001] memory:/😀.ili:2:3: "), plain);
                    assertTrue(plain.contains("Missing\\u000a\\u001b"), plain);
                    assertEquals(color, rendered.contains("\u001b["));
                }
                if (position.isPresent()) {
                    String stale = renderer.render(diagnostic, Map.of(id, new SourceText(id, new SourceRevision(8), source.text())), language,
                            new RenderOptions(false, 0, 0, 160, false));
                    assertTrue(stale.startsWith(severity.name() + " [ILI-SEM-0001] memory:/😀.ili@7 (UTF-16 6..13): "), stale);
                }
            }
        }
    }
}
