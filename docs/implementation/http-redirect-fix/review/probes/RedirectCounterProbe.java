import com.sun.net.httpserver.HttpServer;

import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.HttpSourceLoader;
import guru.interlis.repository.LoadedSource;
import guru.interlis.repository.RepositoryProxyConfiguration;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.repository.SourceLocator;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * New independent review counterprobe for REDIR-01/02/03.
 * Uses only the supplied distribution JARs and a local HTTP server.
 */
public final class RedirectCounterProbe {
    private static final String SECRET = "probe-secret-7f3a";
    private static final String MALFORMED = "http://[::1/%ZZ?token=" + SECRET;

    public static void main(String[] args) throws Exception {
        AtomicReference<String> location = new AtomicReference<>(MALFORMED);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/index.xml".equals(path)) {
                byte[] body = "valid-probe".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.getResponseHeaders().set("Location", location.get());
                exchange.sendResponseHeaders(302, -1);
            }
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            HttpSourceLoader loader = new HttpSourceLoader(new RepositoryProxyConfiguration.Direct());

            SourceLoadException failure = null;
            try {
                load(loader, base + "/ilimodels.xml");
            } catch (SourceLoadException expected) {
                failure = expected;
            }
            require(failure != null, "malformed Location did not throw SourceLoadException");
            require(failure.reason() == SourceLoadException.Reason.IO_FAILURE,
                    "wrong reason: " + failure.reason());
            require("invalid HTTP redirect address".equals(failure.getMessage()),
                    "unsafe/unexpected message: " + failure.getMessage());
            require(!failure.getMessage().contains(SECRET), "message leaked malformed Location secret");
            require(failure.getCause() instanceof IllegalArgumentException,
                    "missing internal IllegalArgumentException cause: " + failure.getCause());
            System.out.println("MALFORMED_REDIRECT typed IO_FAILURE safe-message cause=IllegalArgumentException");

            location.set("index.xml");
            LoadedSource loaded = load(loader, base + "/ilimodels.xml");
            require("valid-probe".equals(loaded.text()), "relative redirect did not load content");
            System.out.println("RELATIVE_REDIRECT control loaded=" + loaded.text());
        } finally {
            server.stop(0);
        }
        System.out.println("REDIRECT_COUNTERPROBE_PASS");
    }

    private static LoadedSource load(HttpSourceLoader loader, String url) {
        return loader.load(new SourceLocator(url), FetchPolicy.defaults(), CancellationToken.none());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
