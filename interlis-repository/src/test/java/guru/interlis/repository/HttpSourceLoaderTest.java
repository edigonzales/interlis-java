package guru.interlis.repository;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSourceLoaderTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void loadsUtf8AndFollowsRedirect() {
        String base = start(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/redirect")) {
                exchange.getResponseHeaders().add("Location", "/ok");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }
            send(exchange, 200, "INTERLIS 2.4;".getBytes(StandardCharsets.UTF_8));
        });
        HttpSourceLoader loader = new HttpSourceLoader();
        FetchPolicy policy = new FetchPolicy(false, 1024, Duration.ofSeconds(2));
        LoadedSource loaded = loader.load(new SourceLocator(base + "/ok"), policy,
                CancellationToken.none());
        assertEquals("INTERLIS 2.4;", loaded.text());
        assertEquals("http", loaded.origin());
        assertEquals(64, loaded.byteSha256().length());
        LoadedSource redirected = loader.load(new SourceLocator(base + "/redirect"), policy,
                CancellationToken.none());
        assertEquals("INTERLIS 2.4;", redirected.text());
    }

    @Test
    void mapsAccessEncodingSizeAndOffline() {
        String base = start(exchange -> {
            switch (exchange.getRequestURI().getPath()) {
                case "/forbidden" -> send(exchange, 403, new byte[0]);
                case "/invalid" -> send(exchange, 200, new byte[]{(byte) 0xc3, 0x28});
                case "/large" -> send(exchange, 200, new byte[2048]);
                case "/loop" -> {
                    exchange.getResponseHeaders().add("Location", "/loop");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                }
                default -> send(exchange, 404, new byte[0]);
            }
        });
        HttpSourceLoader loader = new HttpSourceLoader();
        FetchPolicy small = new FetchPolicy(false, 64, Duration.ofSeconds(2));
        assertEquals(SourceLoadException.Reason.ACCESS_DENIED, reason(loader, base + "/forbidden", small));
        assertEquals(SourceLoadException.Reason.INVALID_ENCODING, reason(loader, base + "/invalid", small));
        assertEquals(SourceLoadException.Reason.TOO_LARGE, reason(loader, base + "/large", small));
        assertEquals(SourceLoadException.Reason.NOT_FOUND, reason(loader, base + "/missing", small));
        assertEquals(SourceLoadException.Reason.REDIRECT_LIMIT, reason(loader, base + "/loop", small));
        assertEquals(SourceLoadException.Reason.OFFLINE, reason(loader, base + "/ok",
                new FetchPolicy(true, 64, Duration.ofSeconds(2))));
    }

    @Test
    void mapsTimeout() {
        String base = start(exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            send(exchange, 200, new byte[0]);
        });
        HttpSourceLoader loader = new HttpSourceLoader();
        FetchPolicy policy = new FetchPolicy(false, 1024, Duration.ofMillis(50));
        assertEquals(SourceLoadException.Reason.TIMEOUT, reason(loader, base + "/slow", policy));
    }

    private static SourceLoadException.Reason reason(HttpSourceLoader loader, String location,
                                                      FetchPolicy policy) {
        SourceLoadException failure = assertThrows(SourceLoadException.class,
                () -> loader.load(new SourceLocator(location), policy, CancellationToken.none()));
        return failure.reason();
    }

    private String start(Handler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    handler.handle(exchange);
                } catch (RuntimeException failure) {
                    exchange.close();
                }
            });
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "http-test");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return "http://127.0.0.1:" + server.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
