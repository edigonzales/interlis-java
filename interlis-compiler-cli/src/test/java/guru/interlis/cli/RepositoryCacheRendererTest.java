package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryCacheRendererTest {
    final RepositoryCacheRenderer renderer=new RepositoryCacheRenderer();
    @Test void messagesUseTheCommonLocalizedInfoHeaderColorAndNeutralMissWording() {
        for(var event:RepositoryCacheEvent.values()) {
            var o=new RepositoryCacheObservation(event,Optional.of("https://example.org/model.ili"));
            if(event==RepositoryCacheEvent.WRITE_UNAVAILABLE) {assertTrue(renderer.render(o,DiagnosticLanguage.EN,false).isEmpty());continue;}
            String en=renderer.render(o,DiagnosticLanguage.EN,false).orElseThrow(),de=renderer.render(o,DiagnosticLanguage.DE,false).orElseThrow();
            assertTrue(en.startsWith("INFO [ILI-RES-0010]"),en);assertTrue(de.startsWith("Information [ILI-RES-0010]"),de);
            assertTrue(en.contains("https://example.org/model.ili"));assertFalse(en.contains(event.name()));assertFalse(en.contains("\u001b"));
            assertTrue(renderer.render(o,DiagnosticLanguage.DE,true).orElseThrow().contains("\u001b[36mInformation\u001b[0m"));
            if(event==RepositoryCacheEvent.CACHE_MISS) {assertFalse(de.contains("fehlt"));assertFalse(de.contains("erneut"));}
            if(event==RepositoryCacheEvent.DOWNLOAD)assertFalse(de.contains("gespeichert"));
        }
    }
    @Test void addressCredentialsQueriesFragmentsAndTerminalControlsAreNeverRendered() {
        var o=new RepositoryCacheObservation(RepositoryCacheEvent.OFFLINE_HIT,Optional.of("https://user:password@example.org/😀\u202e.ili?token=secret#fragment"));
        String text=renderer.render(o,DiagnosticLanguage.DE,false).orElseThrow();
        for(String secret:List.of("user","password","token","secret","fragment","\u202e"))assertFalse(text.contains(secret),text);
        assertTrue(text.contains("\\u202e"));assertTrue(text.contains("😀"));
    }
}
