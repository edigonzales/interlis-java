import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.HostRepositories;
import guru.interlis.repository.HttpSourceLoader;
import guru.interlis.repository.LoadedSource;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryClient;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositoryIndexReader;
import guru.interlis.repository.RepositoryProxyConfiguration;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.repository.SourceLoader;
import guru.interlis.repository.SourceLocator;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Reviewer-owned narrow counterprobes against the supplied distribution jars. */
public final class TimeoutProbe {
    private static final String H = "HTTP/1.1";
    private static final byte[] X = "x".getBytes(StandardCharsets.US_ASCII);

    private TimeoutProbe() { }

    public static void main(String[] args) throws Exception {
        testDefaultsAndValidation();
        testCatalogFingerprint();
        testTlsConnectTimeout();
        try (RawServer origin = new RawServer(TimeoutProbe::origin)) {
            String base = "http://127.0.0.1:" + origin.port();
            testReadTimeoutBeforeHeaders(origin, base);
            testReadTimeoutAfterPartialBody(origin, base);
            testProgressingBodyResetsReadBudget(origin, base);
            testRedirectGetsFreshTotalBudget(origin, base);
            testTotalBudgetWinsWithoutReadInactivity(base);
            testTimedOutContentIsNotCached(base);
        }
        testProxyIsolation();
        System.out.println("TIMEOUT_PROBE_PASS");
    }

    private static void origin(Socket socket, String path) throws Exception {
        switch (path) {
            case "/headers" -> {
                Thread.sleep(1500);
                send(socket, 200, "OK", Map.of(), X);
            }
            case "/body" -> {
                OutputStream out = socket.getOutputStream();
                out.write((H + " 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                out.write('x');
                out.flush();
                Thread.sleep(1500);
                out.write('y');
                out.flush();
            }
            case "/progress" -> chunked(socket, 12, 100);
            case "/one250" -> {
                Thread.sleep(250);
                send(socket, 200, "OK", Map.of(), X);
            }
            case "/redirect" -> {
                Thread.sleep(250);
                send(socket, 302, "Found", Map.of("Location", "/one250"), new byte[0]);
            }
            case "/total" -> chunked(socket, 10, 200);
            case "/cache" -> {
                OutputStream out = socket.getOutputStream();
                out.write((H + " 200 OK\r\nContent-Length: 2\r\nETag: \"strong\"\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                out.write('x');
                out.flush();
                Thread.sleep(1500);
                out.write('y');
                out.flush();
            }
            default -> send(socket, 404, "Not Found", Map.of(), new byte[0]);
        }
    }

    private static void testDefaultsAndValidation() {
        FetchPolicy defaults = FetchPolicy.defaults();
        check(!defaults.offline(), "defaults offline");
        check(defaults.maxBytes() == 16 * 1024 * 1024, "defaults maxBytes");
        assertEquals(Duration.ofSeconds(15), defaults.connectTimeout(), "defaults connect");
        assertEquals(Duration.ofSeconds(40), defaults.readTimeout(), "defaults read");
        assertEquals(Optional.empty(), defaults.requestTimeout(), "defaults request");
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(Long.MAX_VALUE))) {
            expect(IllegalArgumentException.class,
                    () -> new FetchPolicy(false, 1, invalid, defaults.readTimeout(), Optional.empty()), "invalid connect");
            expect(IllegalArgumentException.class,
                    () -> new FetchPolicy(false, 1, defaults.connectTimeout(), invalid, Optional.empty()), "invalid read");
            expect(IllegalArgumentException.class,
                    () -> new FetchPolicy(false, 1, defaults.connectTimeout(), defaults.readTimeout(),
                            Optional.of(invalid)), "invalid request");
        }
        expect(NullPointerException.class,
                () -> new FetchPolicy(false, 1, null, defaults.readTimeout(), Optional.empty()), "null connect");
        expect(NullPointerException.class,
                () -> new FetchPolicy(false, 1, defaults.connectTimeout(), null, Optional.empty()), "null read");
        expect(NullPointerException.class,
                () -> new FetchPolicy(false, 1, defaults.connectTimeout(), defaults.readTimeout(), null), "null request");
        expect(IllegalArgumentException.class,
                () -> new FetchPolicy(false, 0, defaults.connectTimeout(), defaults.readTimeout(), Optional.empty()), "zero bytes");
        for (String host : List.of("", "a b", "https://host", "user:secret@host", "host/path", "host?secret",
                "host#part", "host:80", "host\n")) {
            expect(IllegalArgumentException.class, () -> new RepositoryProxyConfiguration.HttpProxy(host, 80),
                    "invalid proxy host " + host);
        }
        for (String host : List.of("localhost", "127.0.0.1", "[::1]")) {
            check(new RepositoryProxyConfiguration.HttpProxy(host, 80) != null, "valid proxy host " + host);
        }
        expect(IllegalArgumentException.class, () -> new RepositoryProxyConfiguration.HttpProxy("localhost", 0), "port 0");
        expect(IllegalArgumentException.class, () -> new RepositoryProxyConfiguration.HttpProxy("localhost", 65536), "port 65536");
        System.out.println("PASS defaults-and-validation");
    }

    private static void testCatalogFingerprint() {
        var client = new RepositoryClient((locator, policy, token) -> {
            throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND, "no source", null);
        }, new RepositoryIndexReader());
        var defaults = FetchPolicy.defaults();
        var policies = List.of(defaults,
                new FetchPolicy(false, defaults.maxBytes(), Duration.ofSeconds(16), defaults.readTimeout(), Optional.empty()),
                new FetchPolicy(false, defaults.maxBytes(), defaults.connectTimeout(), Duration.ofSeconds(41), Optional.empty()),
                new FetchPolicy(false, defaults.maxBytes(), defaults.connectTimeout(), defaults.readTimeout(),
                        Optional.of(Duration.ofSeconds(60))));
        var revisions = new java.util.HashSet<String>();
        for (FetchPolicy policy : policies) {
            String revision = client.catalog(new RepositoryConfig(List.of(), policy), CancellationToken.none()).revision();
            check(revisions.add(revision), "catalog revision collision for a timeout component");
        }
        assertEquals(4, revisions.size(), "catalog fingerprint coverage");
        System.out.println("PASS catalog-fingerprint-all-three-timeouts");
    }

    private static void testTlsConnectTimeout() throws Exception {
        try (ServerSocket stalled = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
            Thread.startVirtualThread(() -> {
                try (Socket socket = stalled.accept()) {
                    Thread.sleep(3000);
                } catch (Exception ignored) {
                    // Test server intentionally stalls before the TLS handshake.
                }
            });
            long started = System.nanoTime();
            SourceLoadException failure = expectFailure(() -> load(
                    "https://localhost:" + stalled.getLocalPort(),
                    new FetchPolicy(false, 1024, Duration.ofMillis(200), Duration.ofSeconds(5), Optional.empty())));
            assertEquals(SourceLoadException.Reason.TIMEOUT, failure.reason(), "TLS connect timeout reason");
            check(failure.getCause() instanceof java.net.http.HttpConnectTimeoutException,
                    "TLS connect timeout cause was " + failure.getCause());
            long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
            check(elapsed < 3000, "TLS connect timeout took too long: " + elapsed);
            System.out.println("PASS tls-connect-timeout elapsedMs=" + elapsed);
        }
    }

    private static void testReadTimeoutBeforeHeaders(RawServer origin, String base) {
        long started = System.nanoTime();
        SourceLoadException failure = expectFailure(() -> load(base + "/headers", policy(250, null)));
        assertEquals(SourceLoadException.Reason.TIMEOUT, failure.reason(), "header timeout reason");
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        check(elapsed < 1500, "header timeout took too long: " + elapsed);
        System.out.println("PASS read-timeout-before-headers elapsedMs=" + elapsed + " requests=" + origin.requests.get());
    }

    private static void testReadTimeoutAfterPartialBody(RawServer origin, String base) {
        long started = System.nanoTime();
        SourceLoadException failure = expectFailure(() -> load(base + "/body", policy(250, null)));
        assertEquals(SourceLoadException.Reason.TIMEOUT, failure.reason(), "body timeout reason");
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        check(elapsed < 1500, "body timeout took too long: " + elapsed);
        System.out.println("PASS read-timeout-after-partial-body elapsedMs=" + elapsed + " requests=" + origin.requests.get());
    }

    private static void testProgressingBodyResetsReadBudget(RawServer origin, String base) {
        long started = System.nanoTime();
        LoadedSource loaded = load(base + "/progress", policy(300, null));
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertEquals("x".repeat(12), loaded.text(), "progress body");
        check(elapsed >= 1000, "progressing body did not span multiple inactivity budgets: " + elapsed);
        System.out.println("PASS progressing-body-resets-read-budget elapsedMs=" + elapsed
                + " requests=" + origin.requests.get());
    }

    private static void testRedirectGetsFreshTotalBudget(RawServer origin, String base) {
        int before = origin.requests.get();
        long started = System.nanoTime();
        LoadedSource loaded = load(base + "/redirect", policy(1000, 350L));
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertEquals("x", loaded.text(), "redirect body");
        check(origin.requests.get() - before >= 2, "redirect did not issue a second request");
        check(elapsed >= 400, "combined redirect exchanges unexpectedly below one shared budget: " + elapsed);
        System.out.println("PASS redirect-fresh-total-budget elapsedMs=" + elapsed
                + " requests=" + (origin.requests.get() - before));
    }

    private static void testTotalBudgetWinsWithoutReadInactivity(String base) {
        long started = System.nanoTime();
        SourceLoadException failure = expectFailure(() -> load(base + "/total", policy(500, 450L)));
        assertEquals(SourceLoadException.Reason.TIMEOUT, failure.reason(), "total timeout reason");
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        check(elapsed >= 350 && elapsed < 1500, "total timeout window: " + elapsed);
        System.out.println("PASS total-budget-without-read-inactivity elapsedMs=" + elapsed);
    }

    private static void testTimedOutContentIsNotCached(String base) throws Exception {
        Path root = Files.createTempDirectory(Path.of("build/reviewer/probes"), "cache-");
        Path cache = root.resolve("cache");
        var access = HostRepositories.create(root, RepositoryCacheConfiguration.at(cache), event -> { },
                new RepositoryProxyConfiguration.Direct());
        SourceLoadException failure = expectFailure(() -> access.loader().load(
                new SourceLocator(base + "/cache"), policy(200, null), CancellationToken.none()));
        assertEquals(SourceLoadException.Reason.TIMEOUT, failure.reason(), "cache timeout reason");
        long files = 0;
        if (Files.exists(cache)) {
            try (var walk = Files.walk(cache)) {
                files = walk.filter(Files::isRegularFile).count();
            }
        }
        assertEquals(0L, files, "timed out content published cache files");
        FetchPolicy offline = new FetchPolicy(true, 1024, Duration.ofSeconds(2), Duration.ofSeconds(2), Optional.empty());
        SourceLoadException miss = expectFailure(() -> access.loader().load(
                new SourceLocator(base + "/cache"), offline, CancellationToken.none()));
        assertEquals(SourceLoadException.Reason.OFFLINE, miss.reason(), "offline after timeout");
        System.out.println("PASS timed-out-content-not-cached files=" + files);
    }

    private static void testProxyIsolation() throws Exception {
        AtomicInteger proxyRequests = new AtomicInteger();
        try (RawServer origin = new RawServer((socket, path) ->
                send(socket, 200, "OK", Map.of(), "origin".getBytes(StandardCharsets.US_ASCII)));
             RawServer proxy = new RawServer((socket, path) -> {
                 proxyRequests.incrementAndGet();
                 send(socket, 200, "OK", Map.of(), "proxy".getBytes(StandardCharsets.US_ASCII));
             })) {
            ProxySelector previous = ProxySelector.getDefault();
            Properties before = new Properties();
            before.putAll(System.getProperties());
            try {
                ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress("127.0.0.1", proxy.port())));
                HttpSourceLoader system = new HttpSourceLoader();
                ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress("127.0.0.1", 1)));
                HttpSourceLoader direct = new HttpSourceLoader(new RepositoryProxyConfiguration.Direct());
                HttpSourceLoader explicit = new HttpSourceLoader(
                        new RepositoryProxyConfiguration.HttpProxy("127.0.0.1", proxy.port()));

                CompletableFuture<String> viaDirect = CompletableFuture.supplyAsync(() ->
                        load(direct, "http://127.0.0.1:" + origin.port() + "/direct", policy(2000, null)).text());
                CompletableFuture<String> viaExplicit = CompletableFuture.supplyAsync(() ->
                        load(explicit, "http://unreachable.invalid/model", policy(2000, null)).text());
                String viaSystem = load(system, "http://unreachable.invalid/model", policy(2000, null)).text();

                assertEquals("origin", viaDirect.get(5, TimeUnit.SECONDS), "direct isolation");
                assertEquals("proxy", viaExplicit.get(5, TimeUnit.SECONDS), "explicit isolation");
                assertEquals("proxy", viaSystem, "captured system selector");
                assertEquals(2, proxyRequests.get(), "proxy request count");
                assertEquals(1, origin.requests.get(), "origin request count");
                assertEquals(before, System.getProperties(), "system properties mutated");
                var selected = ProxySelector.getDefault().select(URI.create("http://unreachable.invalid/")).getFirst();
                assertEquals(1, ((InetSocketAddress) selected.address()).getPort(), "default selector was mutated");
                System.out.println("PASS proxy-isolation proxyRequests=" + proxyRequests.get()
                        + " originRequests=" + origin.requests.get());
            } finally {
                ProxySelector.setDefault(previous);
            }
        }
    }

    private static FetchPolicy policy(long readMillis, Long totalMillis) {
        return new FetchPolicy(false, 1024, Duration.ofSeconds(2), Duration.ofMillis(readMillis),
                totalMillis == null ? Optional.empty() : Optional.of(Duration.ofMillis(totalMillis)));
    }

    private static LoadedSource load(SourceLoader loader, String location, FetchPolicy policy) {
        return loader.load(new SourceLocator(location), policy, CancellationToken.none());
    }

    private static LoadedSource load(String location, FetchPolicy policy) {
        return new HttpSourceLoader(new RepositoryProxyConfiguration.Direct())
                .load(new SourceLocator(location), policy, CancellationToken.none());
    }

    private static SourceLoadException expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected SourceLoadException");
        } catch (SourceLoadException failure) {
            return failure;
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action, String name) {
        try {
            action.run();
            throw new AssertionError(name + ": expected " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError(name + ": expected " + type.getSimpleName() + " but got " + failure, failure);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void chunked(Socket socket, int pieces, long delayMillis) throws Exception {
        OutputStream out = socket.getOutputStream();
        out.write((H + " 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        for (int i = 0; i < pieces; i++) {
            Thread.sleep(delayMillis);
            out.write("1\r\nx\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }
        out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static void send(Socket socket, int status, String reason, Map<String, String> headers,
                             byte[] body) throws IOException {
        OutputStream out = socket.getOutputStream();
        StringBuilder head = new StringBuilder(H).append(' ').append(status).append(' ').append(reason).append("\r\n");
        headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("Content-Length: ").append(body.length).append("\r\nConnection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String readHeaders(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) >= 0) {
            bytes.write(next);
            if (bytes.size() > 16_384) throw new IOException("headers too large");
            if (bytes.size() >= 4) {
                byte[] data = bytes.toByteArray();
                int at = data.length;
                if (data[at - 4] == '\r' && data[at - 3] == '\n' && data[at - 2] == '\r' && data[at - 1] == '\n') {
                    return bytes.toString(StandardCharsets.ISO_8859_1);
                }
            }
        }
        throw new EOFException();
    }

    private static String requestPath(String headers) {
        String first = headers.lines().findFirst().orElseThrow();
        String[] parts = first.split(" ");
        if (parts.length < 2) return "/";
        String target = parts[1];
        try {
            String path = URI.create(target).getPath();
            return path == null || path.isEmpty() ? "/" : path;
        } catch (IllegalArgumentException invalid) {
            return "/";
        }
    }

    private interface Handler {
        void handle(Socket socket, String path) throws Exception;
    }

    private static final class RawServer implements AutoCloseable {
        private final ServerSocket server;
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicReference<String> lastRequest = new AtomicReference<>();

        RawServer(Handler handler) throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread.startVirtualThread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        sockets.add(socket);
                        Thread.startVirtualThread(() -> {
                            try (socket) {
                                String headers = readHeaders(socket.getInputStream());
                                lastRequest.set(headers);
                                requests.incrementAndGet();
                                handler.handle(socket, requestPath(headers));
                            } catch (Exception ignored) {
                                // Test server intentionally tolerates client cancellation.
                            }
                        });
                    } catch (IOException closed) {
                        break;
                    }
                }
            });
        }

        int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            server.close();
            for (Socket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Best effort test cleanup.
                }
            }
        }
    }
}
