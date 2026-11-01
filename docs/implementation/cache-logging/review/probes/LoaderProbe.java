import com.sun.net.httpserver.HttpServer;
import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.HostRepositories;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryCacheEvent;
import guru.interlis.repository.RepositoryCacheObservation;
import guru.interlis.repository.SourceLocator;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Reviewer-owned probe of actual HTTP/offline observations through the public host API. */
public final class LoaderProbe {
    private static int checks;
    private static final AtomicInteger requests = new AtomicInteger();

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static List<RepositoryCacheEvent> kinds(List<RepositoryCacheObservation> observations) {
        return observations.stream().map(RepositoryCacheObservation::event).toList();
    }

    public static void main(String[] args) throws Exception {
        String body = "INTERLIS 2.4; MODEL Basis AT \"x\" VERSION \"1\" = END Basis.";
        String tag = "\"probe-strong-etag\"";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try (exchange) {
                if (!exchange.getRequestURI().getPath().equals("/model.ili")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                String conditional = exchange.getRequestHeaders().getFirst("If-None-Match");
                exchange.getResponseHeaders().add("ETag", tag);
                if (tag.equals(conditional)) {
                    exchange.sendResponseHeaders(304, -1);
                } else {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }
        });
        server.start();
        try {
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            String url = origin + "/model.ili?token=secret#fragment";
            Path root = Files.createTempDirectory("reviewer-loader-");
            List<RepositoryCacheObservation> observations = new ArrayList<>();
            var access = HostRepositories.createObserved(root,
                    RepositoryCacheConfiguration.at(root.resolve("cache")), observations::add);

            var first = access.loader().load(new SourceLocator(url), FetchPolicy.defaults(), CancellationToken.none());
            check(first.text().equals(body), "first load text");
            check(kinds(observations).equals(List.of(RepositoryCacheEvent.DOWNLOAD)), "first observation " + kinds(observations));
            check(observations.getFirst().safeSource().equals(java.util.Optional.of(origin + "/model.ili")),
                    "actual source not sanitized: " + observations.getFirst().safeSource());
            check(!observations.getFirst().safeSource().orElseThrow().contains("token"), "query leaked");

            var second = access.loader().load(new SourceLocator(url), FetchPolicy.defaults(), CancellationToken.none());
            check(second.text().equals(body), "second load text");
            check(kinds(observations).equals(List.of(RepositoryCacheEvent.DOWNLOAD, RepositoryCacheEvent.NOT_MODIFIED)),
                    "second observation " + kinds(observations));
            check(observations.get(1).safeSource().equals(java.util.Optional.of(origin + "/model.ili")),
                    "304 source not sanitized: " + observations.get(1).safeSource());

            int beforeOffline = requests.get();
            var offlinePolicy = new FetchPolicy(true, 16 * 1024 * 1024, Duration.ofSeconds(10));
            var offline = access.loader().load(new SourceLocator(url), offlinePolicy, CancellationToken.none());
            check(offline.text().equals(body), "offline load text");
            check(requests.get() == beforeOffline, "offline path performed HTTP");
            check(kinds(observations).equals(List.of(RepositoryCacheEvent.DOWNLOAD, RepositoryCacheEvent.NOT_MODIFIED,
                    RepositoryCacheEvent.OFFLINE_HIT)), "offline observation " + kinds(observations));

            // Legacy three-argument entry remains operational and delivers exactly one callback per access.
            Path legacyRoot = Files.createTempDirectory("reviewer-legacy-");
            List<RepositoryCacheEvent> legacyEvents = new ArrayList<>();
            var legacy = HostRepositories.create(legacyRoot,
                    RepositoryCacheConfiguration.at(legacyRoot.resolve("cache")), legacyEvents::add);
            legacy.loader().load(new SourceLocator(origin + "/model.ili"), FetchPolicy.defaults(), CancellationToken.none());
            legacy.loader().load(new SourceLocator(origin + "/model.ili"), FetchPolicy.defaults(), CancellationToken.none());
            check(legacyEvents.equals(List.of(RepositoryCacheEvent.DOWNLOAD, RepositoryCacheEvent.NOT_MODIFIED)),
                    "legacy callbacks: " + legacyEvents);

            // Observer failures never alter a load.
            Path throwingRoot = Files.createTempDirectory("reviewer-throwing-");
            var throwing = HostRepositories.createObserved(throwingRoot,
                    RepositoryCacheConfiguration.at(throwingRoot.resolve("cache")), observation -> {
                        throw new IllegalStateException("probe observer failure");
                    });
            check(throwing.loader().load(new SourceLocator(origin + "/model.ili?x=1"), FetchPolicy.defaults(),
                    CancellationToken.none()).text().equals(body), "throwing detailed observer changed load");
            check(throwing.loader().load(new SourceLocator(origin + "/model.ili?x=1"), FetchPolicy.defaults(),
                    CancellationToken.none()).text().equals(body), "throwing observer changed repeated load");
            var throwingLegacy = HostRepositories.create(throwingRoot.resolve("legacy"),
                    RepositoryCacheConfiguration.at(throwingRoot.resolve("legacy-cache")), event -> {
                        throw new IllegalStateException("probe legacy observer failure");
                    });
            check(throwingLegacy.loader().load(new SourceLocator(origin + "/model.ili?x=2"), FetchPolicy.defaults(),
                    CancellationToken.none()).text().equals(body), "throwing legacy observer changed load");

            System.out.println("LOADER_PROBE_PASS checks=" + checks + " requests=" + requests.get());
        } finally {
            server.stop(0);
        }
    }
}
