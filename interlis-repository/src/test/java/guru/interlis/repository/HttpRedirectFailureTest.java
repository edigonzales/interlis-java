package guru.interlis.repository;

import com.sun.net.httpserver.HttpServer;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HttpRedirectFailureTest {
    @TempDir Path directory;
    HttpServer server;
    @AfterEach void close() { if (server != null) server.stop(0); }

    @Test void malformedRedirectLocationsAreTypedAndSafe() throws Exception {
        var location = new AtomicReference<>("http://[");
        String base = start(location);
        for (String value : List.of("http://[", "/bad%escape?token=marked-secret", "http://user:marked-secret@[", "/bad path")) {
            location.set(value);
            var failure = assertThrows(SourceLoadException.class, () -> load(direct(), base + "/redirect", false), value);
            assertEquals(SourceLoadException.Reason.IO_FAILURE, failure.reason());
            assertFalse(failure.getMessage().contains("marked-secret"));
            assertFalse(failure.getMessage().contains(value));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        }
    }

    @Test void nonRequestableHttpTargetsAreTyped() throws Exception {
        var location = new AtomicReference<>("http:/missing-host");
        String base = start(location);
        for (String value : List.of("http:/missing-host", "http://host:wrong", "https:/missing-host")) {
            location.set(value);
            assertEquals(SourceLoadException.Reason.IO_FAILURE,
                    assertThrows(SourceLoadException.class, () -> load(direct(), base + "/redirect", false)).reason());
        }
    }

    @Test void invalidInitialHttpAddressesUseTheSameTypedBoundary() {
        for (String value : List.of("http://[", "relative", "http:/missing-host"))
            assertEquals(SourceLoadException.Reason.IO_FAILURE,
                    assertThrows(SourceLoadException.class, () -> load(direct(), value, false)).reason());
    }

    @Test void badRedirectDoesNotPublishContentOrSuccessObservation() throws Exception {
        var location = new AtomicReference<>("http://["); String base = start(location);
        var events = new ArrayList<RepositoryCacheEvent>();
        var access = HostRepositories.create(directory, RepositoryCacheConfiguration.at(directory.resolve("cache")), events::add,
                new RepositoryProxyConfiguration.Direct());
        assertEquals(SourceLoadException.Reason.IO_FAILURE,
                assertThrows(SourceLoadException.class, () -> load(access.loader(), base + "/redirect", false)).reason());
        assertFalse(events.contains(RepositoryCacheEvent.DOWNLOAD));
        assertEquals(SourceLoadException.Reason.OFFLINE,
                assertThrows(SourceLoadException.class, () -> load(access.loader(), base + "/redirect", true)).reason());
        location.set("/ok"); assertEquals("valid", load(access.loader(), base + "/redirect", false).text());
        assertEquals("valid", load(access.loader(), base + "/redirect", true).text());
    }

    @Test void validRelativeRedirectsAndUnsupportedSchemesRetainTheirBehavior() throws Exception {
        var location = new AtomicReference<>("../ok?version=1"); String base = start(location);
        assertEquals("valid", load(direct(), base + "/folder/redirect", false).text());
        location.set("file:///not-http");
        var failure = assertThrows(SourceLoadException.class, () -> load(direct(), base + "/redirect", false));
        assertEquals(SourceLoadException.Reason.IO_FAILURE, failure.reason());
        assertEquals("redirect to unsupported scheme", failure.getMessage());
    }

    private String start(AtomicReference<String> location) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/redirect")) {
                exchange.getResponseHeaders().set("Location", location.get());
                exchange.getResponseHeaders().set("ETag", "\"redirect-body\"");
                exchange.sendResponseHeaders(302, 6); exchange.getResponseBody().write("poison".getBytes(StandardCharsets.UTF_8));
            } else {
                exchange.sendResponseHeaders(200, 5); exchange.getResponseBody().write("valid".getBytes(StandardCharsets.UTF_8));
            }
            exchange.close();
        });
        server.start(); return "http://127.0.0.1:" + server.getAddress().getPort();
    }
    private static HttpSourceLoader direct() { return new HttpSourceLoader(new RepositoryProxyConfiguration.Direct()); }
    private static LoadedSource load(SourceLoader loader, String url, boolean offline) {
        var defaults = FetchPolicy.defaults();
        return loader.load(new SourceLocator(url), new FetchPolicy(offline, 1024, defaults.connectTimeout(), defaults.readTimeout(), defaults.requestTimeout()), CancellationToken.none());
    }
}
