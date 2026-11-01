package guru.interlis.repository;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

class HttpNetworkPolicyTest {
    @TempDir Path directory;
    private final List<HttpServer> servers = new ArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    @AfterEach void close() { servers.forEach(s -> s.stop(0)); executor.shutdownNow(); }

    @Test void defaultsAndValidation() {
        var defaults = FetchPolicy.defaults();
        assertEquals(Duration.ofSeconds(15), defaults.connectTimeout());
        assertEquals(Duration.ofSeconds(40), defaults.readTimeout());
        assertEquals(Optional.empty(), defaults.requestTimeout());
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(Long.MAX_VALUE))) {
            assertThrows(IllegalArgumentException.class, () -> new FetchPolicy(false, 1, invalid, defaults.readTimeout(), Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> new FetchPolicy(false, 1, defaults.connectTimeout(), invalid, Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> new FetchPolicy(false, 1, defaults.connectTimeout(), defaults.readTimeout(), Optional.of(invalid)));
        }
        assertThrows(NullPointerException.class, () -> new FetchPolicy(false, 1, null, defaults.readTimeout(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new FetchPolicy(false, 1, defaults.connectTimeout(), defaults.readTimeout(), null));
        assertThrows(IllegalArgumentException.class, () -> new FetchPolicy(false, 0, defaults.connectTimeout(), defaults.readTimeout(), Optional.empty()));
        for (String host : List.of("", "a b", "https://host", "user:secret@host", "host/path", "host?secret", "host#part", "host:80", "host\n"))
            assertThrows(IllegalArgumentException.class, () -> new RepositoryProxyConfiguration.HttpProxy(host, 80), host);
        for (String host : List.of("localhost", "127.0.0.1", "[::1]")) assertNotNull(new RepositoryProxyConfiguration.HttpProxy(host, 80));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryProxyConfiguration.HttpProxy("localhost", 0));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryProxyConfiguration.HttpProxy("localhost", 65536));
    }

    @Test void readTimeoutBeforeHeadersAndAfterPartialBody() throws Exception {
        String base = server(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/body")) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('x'); exchange.getResponseBody().flush();
            }
            pause(2000); exchange.close();
        });
        var loader = direct();
        for (String path : List.of("/headers", "/body")) {
            var failure = assertThrows(SourceLoadException.class, () -> load(loader, base + path, policy(250, null)));
            assertEquals(SourceLoadException.Reason.TIMEOUT, failure.reason());
        }
    }

    @Test void progressingBodyExceedsInactivityBudgetButTotalLimitStillWins() throws Exception {
        String base = server(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                for (int i = 0; i < 12; i++) {
                    exchange.getResponseBody().write('x'); exchange.getResponseBody().flush(); pause(60);
                }
            } finally { exchange.close(); }
        });
        assertEquals("xxxxxxxxxxxx", load(direct(), base, policy(350, null)).text());
        var failed = assertThrows(SourceLoadException.class, () -> load(direct(), base, policy(350, 450L)));
        assertEquals(SourceLoadException.Reason.TIMEOUT, failed.reason());
    }

    @Test void responseHeadersResetInactivityTimer() throws Exception {
        String base = server(exchange -> {
            pause(300); exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().flush();
            // No body data: the header reception alone must reset the timer.
            pause(300); exchange.getResponseBody().write('x'); exchange.close();
        });
        assertEquals("x", load(direct(), base, policy(500, null)).text());
    }

    @Test void redirectsReceiveNewTotalBudgets() throws Exception {
        var requests = new AtomicInteger();
        String base = server(exchange -> {
            pause(180);
            int number = requests.incrementAndGet();
            if (number == 1) {
                exchange.getResponseHeaders().set("Location", "/next"); exchange.sendResponseHeaders(302, -1);
            } else if (number == 2) { exchange.getResponseHeaders().set("Location", "/final"); exchange.sendResponseHeaders(302, -1); }
            else { exchange.sendResponseHeaders(200, 1); exchange.getResponseBody().write('x'); }
            exchange.close();
        });
        assertEquals("x", load(direct(), base, policy(1000, 350L)).text());
        assertEquals(3, requests.get());
    }

    @Test void timedOutContentIsNeverPublishedToCache() throws Exception {
        String base = server(exchange -> {
            exchange.getResponseHeaders().set("ETag", "\"strong\""); exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('x'); exchange.getResponseBody().flush(); pause(1500); exchange.close();
        });
        var access = HostRepositories.create(directory, RepositoryCacheConfiguration.at(directory.resolve("cache")), e -> {}, new RepositoryProxyConfiguration.Direct());
        assertEquals(SourceLoadException.Reason.TIMEOUT, assertThrows(SourceLoadException.class,
                () -> load(access.loader(), base, policy(200, null))).reason());
        var offline = new FetchPolicy(true, 1024, Duration.ofSeconds(2), Duration.ofSeconds(2), Optional.empty());
        assertEquals(SourceLoadException.Reason.OFFLINE, assertThrows(SourceLoadException.class,
                () -> load(access.loader(), base, offline)).reason());
    }

    @Test void newTlsConnectionUsesConnectTimeout() throws Exception {
        try (var socket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
            var accepted = new CountDownLatch(1);
            executor.submit(() -> { try (var connection = socket.accept()) { connection.setSoTimeout(3000); accepted.countDown(); pause(3000); } catch (IOException ignored) {} catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } });
            var policy = new FetchPolicy(false, 1024, Duration.ofMillis(200), Duration.ofSeconds(5), Optional.empty());
            long started = System.nanoTime();
            var failure = assertThrows(SourceLoadException.class, () -> load(direct(), "https://localhost:" + socket.getLocalPort(), policy));
            assertEquals(SourceLoadException.Reason.TIMEOUT, failure.reason());
            assertInstanceOf(java.net.http.HttpConnectTimeoutException.class, failure.getCause());
            assertTrue(accepted.await(1, TimeUnit.SECONDS));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(3)) < 0);
        }
    }

    @Test void explicitProxyAndDirectInstancesAreConcurrentAndSystemSelectorIsCaptured() throws Exception {
        String base = server(exchange -> { exchange.sendResponseHeaders(200, 6); exchange.getResponseBody().write("direct".getBytes(StandardCharsets.UTF_8)); exchange.close(); });
        try (var proxy = new ProxyServer(null)) {
            ProxySelector previous = ProxySelector.getDefault();
            var properties = new Properties(); properties.putAll(System.getProperties());
            try {
                ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress("127.0.0.1", proxy.port())));
                var system = new HttpSourceLoader();
                ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress("127.0.0.1", 1)));
                var direct = direct();
                var explicit = new HttpSourceLoader(new RepositoryProxyConfiguration.HttpProxy("127.0.0.1", proxy.port()));
                var a = executor.submit(() -> load(direct, base, policy(2000, null)).text());
                var b = executor.submit(() -> load(explicit, "http://unreachable.invalid/model", policy(2000, null)).text());
                assertEquals("direct", a.get(3, TimeUnit.SECONDS));
                assertEquals("proxy", b.get(3, TimeUnit.SECONDS));
                assertEquals("proxy", load(system, "http://unreachable.invalid/model", policy(2000, null)).text());
                assertEquals(2, proxy.requests.get());
                assertEquals(properties, System.getProperties());
                assertEquals(1, ProxySelector.getDefault().select(URI.create(base)).getFirst().address() instanceof InetSocketAddress addr ? addr.getPort() : -1);
                var offline = new FetchPolicy(true, 1024, Duration.ofSeconds(2), Duration.ofSeconds(2), Optional.empty());
                assertThrows(SourceLoadException.class, () -> load(explicit, base, offline));
                assertEquals(2, proxy.requests.get());
            } finally { ProxySelector.setDefault(previous); }
        }
    }

    @Test void httpsConnectSucceedsThroughExplicitProxy() throws Exception {
        SSLContext context = tlsContext();
        SSLContext previous = SSLContext.getDefault();
        try (var proxy = new ProxyServer(context)) {
            try {
                SSLContext.setDefault(context); // Test-only local CA; production never mutates globals.
                var loader = new HttpSourceLoader(new RepositoryProxyConfiguration.HttpProxy("127.0.0.1", proxy.port()));
                assertEquals("proxy", load(loader, "https://localhost:443/model", policy(3000, null)).text());
                assertEquals(1, proxy.connects.get());
                assertEquals("CONNECT localhost:443 HTTP/1.1", proxy.lastRequest.get());
            } finally { SSLContext.setDefault(previous); }
        }
    }

    private SSLContext tlsContext() throws Exception {
        Path keys = directory.resolve("tls.p12");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
                "-keystore", keys.toString(), "-storepass", "changeit", "-keypass", "changeit", "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keys)) { store.load(input, "changeit".toCharArray()); }
        var keysManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); keysManager.init(store, "changeit".toCharArray());
        var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        var context = SSLContext.getInstance("TLS"); context.init(keysManager.getKeyManagers(), trust.getTrustManagers(), null); return context;
    }

    private final class ProxyServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        final List<Socket> connections = new CopyOnWriteArrayList<>();
        final AtomicInteger requests = new AtomicInteger(), connects = new AtomicInteger();
        final AtomicReference<String> lastRequest = new AtomicReference<>();
        ProxyServer(SSLContext tls) throws IOException {
            executor.submit(() -> {
                while (!socket.isClosed()) {
                    try { var connection = socket.accept(); connections.add(connection); executor.submit(() -> handle(connection, tls)); }
                    catch (IOException ignored) { break; }
                }
            });
        }
        int port() { return socket.getLocalPort(); }
        void handle(Socket connection, SSLContext tls) {
            try (connection) {
                String headers = headers(connection.getInputStream()); lastRequest.set(headers.lines().findFirst().orElseThrow()); requests.incrementAndGet();
                if (headers.startsWith("CONNECT ")) {
                    connects.incrementAndGet();
                    connection.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); connection.getOutputStream().flush();
                    try (var secure = (SSLSocket) tls.getSocketFactory().createSocket(connection, "localhost", connection.getPort(), false)) {
                        secure.setUseClientMode(false); secure.startHandshake(); headers(secure.getInputStream()); respond(secure);
                    }
                } else respond(connection);
            } catch (IOException ignored) { }
        }
        void respond(Socket connection) throws IOException {
            connection.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nproxy".getBytes(StandardCharsets.US_ASCII)); connection.getOutputStream().flush();
        }
        @Override public void close() throws IOException { socket.close(); for (var connection : connections) connection.close(); }
    }
    private static String headers(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream(); int next;
        while ((next = input.read()) >= 0) {
            bytes.write(next); if (bytes.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) return bytes.toString(StandardCharsets.US_ASCII);
            if (bytes.size() > 8192) throw new IOException("headers too large");
        }
        throw new EOFException();
    }
    private static FetchPolicy policy(long idleMillis, Long totalMillis) {
        return new FetchPolicy(false, 1024, Duration.ofSeconds(2), Duration.ofMillis(idleMillis),
                totalMillis == null ? Optional.empty() : Optional.of(Duration.ofMillis(totalMillis)));
    }
    private static HttpSourceLoader direct() { return new HttpSourceLoader(new RepositoryProxyConfiguration.Direct()); }
    private static LoadedSource load(SourceLoader loader, String url, FetchPolicy policy) { return loader.load(new SourceLocator(url), policy, CancellationToken.none()); }
    private String server(Handler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(executor);
        server.createContext("/", exchange -> { try { handler.handle(exchange); } catch (InterruptedException e) { Thread.currentThread().interrupt(); exchange.close(); } });
        servers.add(server); server.start(); return "http://127.0.0.1:" + server.getAddress().getPort();
    }
    private static void pause(long millis) throws InterruptedException { Thread.sleep(millis); }
    private interface Handler { void handle(HttpExchange exchange) throws IOException, InterruptedException; }
}
