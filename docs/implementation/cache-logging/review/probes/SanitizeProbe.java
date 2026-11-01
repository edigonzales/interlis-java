import guru.interlis.cli.RepositoryCacheRenderer;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.repository.RepositoryCacheEvent;
import guru.interlis.repository.RepositoryCacheObservation;

import java.util.List;
import java.util.Optional;

/** Reviewer-owned probe of the public observation/rendering contract from the built JARs. */
public final class SanitizeProbe {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static String render(RepositoryCacheObservation o, DiagnosticLanguage language, boolean ansi) {
        return new RepositoryCacheRenderer().render(o, language, ansi).orElseThrow();
    }

    public static void main(String[] args) {
        var renderer = new RepositoryCacheRenderer();
        var safe = new RepositoryCacheObservation(RepositoryCacheEvent.NOT_MODIFIED,
                Optional.of("https://user:password@example.org/model.ili?token=secret#fragment"));
        check(safe.safeSource().equals(Optional.of("https://example.org/model.ili")),
                "userinfo/query/fragment not stripped: " + safe.safeSource());
        check(RepositoryCacheObservation.global(RepositoryCacheEvent.WRITE_UNAVAILABLE).safeSource().isEmpty(),
                "global observation must omit source");
        check(new RepositoryCacheObservation(RepositoryCacheEvent.OFFLINE_HIT, Optional.of("not a uri")).safeSource()
                        .equals(Optional.of("<source>")),
                "invalid source is not neutralized");

        String en = render(safe, DiagnosticLanguage.EN, false);
        check(en.equals("INFO [ILI-RES-0010] Cache access for source 'https://example.org/model.ili': content used from cache (HTTP 304)."),
                "unexpected EN text: " + en);
        String de = render(safe, DiagnosticLanguage.DE, false);
        check(de.equals("Information [ILI-RES-0010] Cachezugriff für Quelle 'https://example.org/model.ili': Inhalt aus Cache verwendet (HTTP 304)."),
                "unexpected DE text: " + de);
        check(!en.contains("NOT_MODIFIED") && !de.contains("NOT_MODIFIED"), "enum leaked into display");

        String colored = render(safe, DiagnosticLanguage.EN, true);
        check(colored.contains("\u001b[36mINFO\u001b[0m"), "INFO color missing: " + colored);
        check(colored.contains("\u001b[90mILI-RES-0010\u001b[0m"), "code color missing: " + colored);
        check(colored.indexOf("\u001b[0m") > colored.indexOf("\u001b[36mINFO"), "INFO color span not closed: " + colored);
        check(colored.indexOf("\u001b[0m", colored.indexOf("\u001b[90m")) > colored.indexOf("\u001b[90m"), "code color span not closed: " + colored);
        check(!render(safe, DiagnosticLanguage.EN, false).contains("\u001b"), "plain render contains ANSI");

        String expectEn = "INFO [ILI-RES-0010] Cache access for source 'https://example.org/model.ili': ";
        check(render(new RepositoryCacheObservation(RepositoryCacheEvent.DOWNLOAD, Optional.of("https://example.org/model.ili")), DiagnosticLanguage.EN, false)
                .equals(expectEn + "source downloaded."), "DOWNLOAD wording");
        check(render(new RepositoryCacheObservation(RepositoryCacheEvent.OFFLINE_HIT, Optional.of("https://example.org/model.ili")), DiagnosticLanguage.EN, false)
                .equals(expectEn + "content used from cache offline."), "OFFLINE_HIT wording");
        check(render(new RepositoryCacheObservation(RepositoryCacheEvent.OFFLINE_MISS, Optional.of("https://example.org/model.ili")), DiagnosticLanguage.EN, false)
                .equals(expectEn + "source unavailable in offline cache."), "OFFLINE_MISS wording");
        String miss = render(new RepositoryCacheObservation(RepositoryCacheEvent.CACHE_MISS, Optional.of("https://example.org/model.ili")), DiagnosticLanguage.EN, false);
        check(miss.equals(expectEn + "cache could not be used."), "CACHE_MISS wording");
        check(!miss.toLowerCase().contains("retry") && !miss.contains("missing") && !miss.contains("stored"),
                "CACHE_MISS overclaims: " + miss);
        String missDe = render(new RepositoryCacheObservation(RepositoryCacheEvent.CACHE_MISS, Optional.of("https://example.org/model.ili")), DiagnosticLanguage.DE, false);
        check(!missDe.contains("erneut") && !missDe.contains("fehlt") && !missDe.contains("gespeichert"),
                "CACHE_MISS DE overclaims: " + missDe);
        String downloadDe = render(new RepositoryCacheObservation(RepositoryCacheEvent.DOWNLOAD, Optional.of("https://example.org/model.ili")), DiagnosticLanguage.DE, false);
        check(!downloadDe.contains("gespeichert"), "DOWNLOAD claims persistence: " + downloadDe);
        check(renderer.render(new RepositoryCacheObservation(RepositoryCacheEvent.WRITE_UNAVAILABLE, Optional.empty()),
                DiagnosticLanguage.DE, false).isEmpty(), "WRITE_UNAVAILABLE must not be rendered as INFO");

        // Terminal/bidi controls and printable Unicode: escaped, never emitted raw.
        String hostileSource = "https://example.org/😀\u202e.ili?token=secret#fragment";
        String hostile = render(new RepositoryCacheObservation(RepositoryCacheEvent.OFFLINE_HIT, Optional.of(hostileSource)),
                DiagnosticLanguage.DE, false);
        check(!hostile.contains("\u202e"), "raw bidi control emitted: " + hostile);
        check(hostile.contains("\\u202e"), "bidi control not escaped in expected form: " + hostile);
        check(hostile.contains("😀"), "printable Unicode was lost: " + hostile);
        check(!hostile.contains("token") && !hostile.contains("secret") && !hostile.contains("fragment"),
                "query/fragment leaked: " + hostile);
        String escSource = "https://example.org/\u001b[31m.ili";
        String esc = new RepositoryCacheObservation(RepositoryCacheEvent.DOWNLOAD, Optional.of(escSource)).safeSource().orElseThrow();
        check(!esc.contains("\u001b"), "ESC survived safe source: " + esc);
        check(!render(new RepositoryCacheObservation(RepositoryCacheEvent.DOWNLOAD, Optional.of(escSource)),
                DiagnosticLanguage.EN, false).contains("\u001b"), "ESC reached renderer output");

        // Every event renders independently; no collapsing of repeated identical observations.
        List<RepositoryCacheObservation> repeated = List.of(safe, safe);
        check(repeated.stream().map(o -> render(o, DiagnosticLanguage.EN, false)).count() == 2,
                "repeated actual observations were collapsed");
        System.out.println("SANITIZE_PROBE_PASS checks=" + checks);
    }
}
