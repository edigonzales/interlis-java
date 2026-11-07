package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.repository.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryResolutionRendererTest {
    @Test void currentProgressUsesCommonLocalizedSafeAndColoredPresentation() {
        var renderer = new RepositoryResolutionRenderer();
        for (var kind : List.of(RepositorySearchStep.Kind.LOOKUP, RepositorySearchStep.Kind.SELECTED)) {
            var observation = new RepositoryResolutionObservation.SearchStep(new RepositorySearchStep("Model\n\u001b",
                    URI.create("https://user:password@example.org/?secret=value#fragment"), kind, "1 model.ili\u202e"));
            for (var language : DiagnosticLanguage.values()) {
                String text = renderer.render(observation, language, false).orElseThrow();
                assertTrue(text.startsWith("INFO "), text);
                assertTrue(text.endsWith(" [ILI-RES-001" + (kind == RepositorySearchStep.Kind.LOOKUP ? "1" : "2") + "]"), text);
                assertTrue(text.contains("Model\\u000a\\u001b"), text);
                for (String secret : List.of("password", "user", "secret", "value", "fragment")) assertFalse(text.contains(secret), text);
                assertFalse(text.contains("\n"));
                assertTrue(renderer.render(observation, language, true).orElseThrow().startsWith("\u001b[36mINFO\u001b[0m"));
            }
        }
        var verification = new RepositoryResolutionObservation.VerificationStarted();
        assertEquals("INFO Completion check: checking resolved sources for changes. [ILI-RES-0013]",
                renderer.render(verification, DiagnosticLanguage.EN, false).orElseThrow());
        assertEquals("INFO Abschlussprüfung: verwendete Quellen auf Änderungen prüfen. [ILI-RES-0013]",
                renderer.render(verification, DiagnosticLanguage.DE, false).orElseThrow());
        for (var kind : RepositorySearchStep.Kind.values()) if (kind != RepositorySearchStep.Kind.LOOKUP && kind != RepositorySearchStep.Kind.SELECTED)
            assertTrue(renderer.render(new RepositoryResolutionObservation.SearchStep(new RepositorySearchStep("Model", URI.create("https://example.org/"), kind, "")),
                    DiagnosticLanguage.EN, false).isEmpty());
    }
}
