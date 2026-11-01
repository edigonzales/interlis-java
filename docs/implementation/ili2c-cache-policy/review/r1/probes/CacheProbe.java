package probe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.*;
import guru.interlis.source.SourceId;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Independent focused counterprobes for the MD5/TTL cache migration.
 * Uses only the supplied runtime distribution jars and public host APIs.
 */
public final class CacheProbe {
    static final CancellationToken TOKEN = CancellationToken.none();
    static final FetchPolicy ONLINE = new FetchPolicy(false, 1 << 20, Duration.ofSeconds(2), Duration.ofSeconds(2), Optional.of(Duration.ofSeconds(2)));
    static final FetchPolicy OFFLINE = new FetchPolicy(true, 1 << 20, Duration.ofSeconds(2), Duration.ofSeconds(2), Optional.of(Duration.ofSeconds(2)));
    static final Instant T0 = Instant.parse("2026-06-15T00:00:00Z");

    record Resp(int status, String body, Map<String, String> headers) { }
    record Req(String path, String ifNoneMatch, String ifModifiedSince, String acceptEncoding) { }

    static final Map<String, Resp> responses = new ConcurrentHashMap<>();
    static final List<Req> requests = Collections.synchronizedList(new ArrayList<>());
    static HttpServer server;
    static String base;
    static Path dir;
    static int passed;

    static final class TestClock extends Clock {
        volatile Instant now;
        TestClock(Instant now) { this.now = now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { Objects.requireNonNull(zone); return this; }
        public Instant instant() { return now; }
    }

    static HostRepositories.Access host(Clock clock, List<RepositoryCacheObservation> events) {
        return HostRepositories.createObserved(dir, RepositoryCacheConfiguration.at(dir.resolve("cache")),
                events::add, new RepositoryProxyConfiguration.Direct(), clock);
    }

    static String url(String path) { return base + path.substring(1); }
    static Path metadata(Path cache, String url) {
        return cache.resolve("metadata-v2").resolve(ContentDigest.sha256(url.getBytes(StandardCharsets.UTF_8)).value() + ".xml");
    }
    static Path cache() { return dir.resolve("cache"); }
    static String md5(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    static LoadedSource load(HostRepositories.Access h, String url, RepositoryCacheRequest request) {
        return h.loader().load(new SourceLocator(url), ONLINE, request, TOKEN);
    }
    static LoadedSource loadOffline(HostRepositories.Access h, String url) {
        return h.loader().loadCached(new SourceLocator(url), OFFLINE, TOKEN);
    }
    static SourceLoadException fails(Runnable action) {
        try { action.run(); }
        catch (SourceLoadException expected) { return expected; }
        throw new AssertionError("expected SourceLoadException");
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void eq(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
    static int mark() { return requests.size(); }
    static List<Req> since(int mark) {
        synchronized (requests) { return List.copyOf(requests.subList(mark, requests.size())); }
    }
    static boolean hasEvent(List<RepositoryCacheObservation> events, RepositoryCacheEvent event) {
        return events.stream().anyMatch(e -> e.event() == event);
    }
    static int indexOfEvent(List<RepositoryCacheObservation> events, RepositoryCacheEvent event) {
        for (int i = 0; i < events.size(); i++) if (events.get(i).event() == event) return i;
        return -1;
    }
    static void writeV1(Path cache, String url, String digest) throws IOException {
        Path legacy = cache.resolve("metadata-v1");
        Files.createDirectories(legacy);
        String xml = "<interlis-cache version=\"1\" source=\"" + url + "\" response-source=\"" + url
                + "\" sha256=\"" + digest + "\" etag=\"&quot;old&quot;\"/>";
        Files.writeString(legacy.resolve(ContentDigest.sha256(url.getBytes(StandardCharsets.UTF_8)).value() + ".xml"), xml);
    }

    static void t01BoundaryNoRenewalFutureAndEpoch() throws Exception {
        responses.put("/alpha", new Resp(200, "alpha", Map.of()));
        String u = url("/alpha");
        TestClock clock = new TestClock(T0);
        List<RepositoryCacheObservation> events = new CopyOnWriteArrayList<>();
        HostRepositories.Access h = host(clock, events);
        RepositoryCacheRequest metadata = RepositoryCacheRequest.metadata();
        eq("alpha", load(h, u, metadata).text(), "initial load");
        eq(1, requests.size(), "one initial request");
        byte[] before = Files.readAllBytes(metadata(cache(), u));

        clock.now = T0.plus(Duration.ofHours(24)).minusNanos(1);
        eq("alpha", load(h, u, metadata).text(), "inside boundary");
        eq(1, requests.size(), "inside boundary is a fresh hit");
        check(hasEvent(events, RepositoryCacheEvent.FRESH_HIT), "FRESH_HIT observed");

        clock.now = T0.plus(Duration.ofHours(24));
        eq("alpha", load(h, u, metadata).text(), "exact boundary");
        eq(1, requests.size(), "exact boundary is inclusive and still fresh");
        check(Arrays.equals(before, Files.readAllBytes(metadata(cache(), u))), "hit must not renew downloaded-at");

        clock.now = T0.plus(Duration.ofHours(24)).plusNanos(1);
        eq("alpha", load(h, u, metadata).text(), "just past boundary");
        eq(2, requests.size(), "just past boundary downloads");
        String xml = Files.readString(metadata(cache(), u));
        check(xml.contains("downloaded-at=\"" + clock.now + "\""), "download records the injected clock: " + xml);

        Instant t2 = clock.now;
        Files.writeString(metadata(cache(), u), xml.replaceAll("downloaded-at=\"[^\"]+\"",
                "downloaded-at=\"" + t2.plus(Duration.ofHours(1)) + "\""));
        load(h, u, metadata);
        eq(3, requests.size(), "future timestamp is never fresh online");

        Files.writeString(metadata(cache(), u), Files.readString(metadata(cache(), u))
                .replaceAll("downloaded-at=\"[^\"]+\"", "downloaded-at=\"1970-01-01T00:00:00Z\""));
        load(h, u, metadata);
        eq(4, requests.size(), "EPOCH/unknown age is never fresh online");
        System.out.println("PROBE_PASS 01 inclusive-boundary/no-renewal/future/epoch");
        passed++;
    }

    static void t02Md5BeforeDownload() throws Exception {
        responses.put("/bravo", new Resp(200, "bravo", Map.of()));
        String u = url("/bravo");
        TestClock clock = new TestClock(T0);
        List<RepositoryCacheObservation> events = new CopyOnWriteArrayList<>();
        HostRepositories.Access h = host(clock, events);
        int start = mark();
        load(h, u, RepositoryCacheRequest.model(Optional.empty()));
        eq(1, since(start).size(), "initial model download");
        load(h, u, RepositoryCacheRequest.model(Optional.of(md5("bravo").toUpperCase(Locale.ROOT))));
        eq(1, since(start).size(), "matching MD5 is case-insensitive before download");
        events.clear();
        responses.put("/bravo", new Resp(200, "charlie", Map.of()));
        eq("bravo", load(h, u, RepositoryCacheRequest.model(Optional.empty())).text(), "no MD5 relies on TTL");
        eq(1, since(start).size(), "absent MD5 stays on the fresh local file");
        eq("charlie", load(h, u, RepositoryCacheRequest.model(Optional.of(md5("charlie")))).text(), "changed MD5 refreshes");
        eq(2, since(start).size(), "changed MD5 triggers exactly one download");
        int miss = indexOfEvent(events, RepositoryCacheEvent.CACHE_MISS);
        int download = indexOfEvent(events, RepositoryCacheEvent.DOWNLOAD);
        check(miss >= 0 && download > miss, "CACHE_MISS precedes DOWNLOAD: " + events);
        eq("charlie", load(h, u, RepositoryCacheRequest.model(Optional.of(md5("zulu")))).text(), "wrong MD5 downloads");
        eq(3, since(start).size(), "wrong MD5 forces another full download");
        System.out.println("PROBE_PASS 02 md5-before-download/order");
        passed++;
    }

    static void t03GenericZeroAndNoConditionalHeaders() throws Exception {
        responses.put("/generic", new Resp(200, "gamma", Map.of("ETag", "\"g\"", "Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")));
        String u = url("/generic");
        TestClock clock = new TestClock(T0);
        HostRepositories.Access h = host(clock, new CopyOnWriteArrayList<>());
        int start = mark();
        load(h, u, RepositoryCacheRequest.metadata());
        h.loader().load(new SourceLocator(u), ONLINE, TOKEN);
        h.loader().load(new SourceLocator(u), ONLINE, TOKEN);
        load(h, u, RepositoryCacheRequest.unconditional());
        eq(4, since(start).size(), "generic/zero requests are always unconditional downloads");
        for (Req r : since(start)) {
            check(r.ifNoneMatch() == null, "no If-None-Match: " + r);
            check(r.ifModifiedSince() == null, "no If-Modified-Since: " + r);
            eq("identity", r.acceptEncoding(), "identity coding requested");
        }
        System.out.println("PROBE_PASS 03 generic/zero-always-fetch/no-conditional-headers");
        passed++;
    }

    static void t04NotFoundInvalidatesBothButKeepsContent() throws Exception {
        responses.put("/gone", new Resp(200, "gone text", Map.of()));
        String u = url("/gone");
        TestClock clock = new TestClock(T0);
        HostRepositories.Access h = host(clock, new CopyOnWriteArrayList<>());
        LoadedSource first = load(h, u, RepositoryCacheRequest.metadata());
        Path v2 = metadata(cache(), u);
        check(Files.exists(v2), "v2 published");
        writeV1(cache(), u, first.byteSha256());
        check(Files.exists(cache().resolve("metadata-v1")), "v1 fixture exists");
        responses.put("/gone", new Resp(404, null, Map.of()));
        SourceLoadException missing = fails(() -> load(h, u, RepositoryCacheRequest.unconditional()));
        eq(SourceLoadException.Reason.NOT_FOUND, missing.reason(), "404 reason");
        check(!Files.exists(v2), "404 removes metadata-v2");
        check(!Files.exists(cache().resolve("metadata-v1").resolve(v2.getFileName())), "404 cannot resurrect v1");
        check(Files.exists(cache().resolve(first.byteSha256())), "old digest content is not deleted");
        SourceLoadException offline = fails(() -> loadOffline(h, u));
        eq(SourceLoadException.Reason.OFFLINE, offline.reason(), "offline after 404");
        System.out.println("PROBE_PASS 04 404-invalidates-v1-and-v2/keeps-content");
        passed++;
    }

    static void t05Unsolicited304IsIoFailureWithoutRetry() throws Exception {
        responses.put("/always304", new Resp(304, null, Map.of()));
        String u = url("/always304");
        TestClock clock = new TestClock(T0);
        HostRepositories.Access h = host(clock, new CopyOnWriteArrayList<>());
        int start = mark();
        SourceLoadException failure = fails(() -> load(h, u, RepositoryCacheRequest.metadata()));
        eq(SourceLoadException.Reason.IO_FAILURE, failure.reason(), "304 reason");
        eq(1, since(start).size(), "304 is not retried");
        check(since(start).get(0).ifNoneMatch() == null, "unsolicited 304 sent no conditional header");
        System.out.println("PROBE_PASS 05 unsolicited-304-io-failure-no-retry");
        passed++;
    }

    static void t06ConcurrentReadersPublishAtomicEntries() throws Exception {
        responses.put("/conc", new Resp(200, "conc data", Map.of()));
        String u = url("/conc");
        TestClock clock = new TestClock(T0);
        List<RepositoryCacheObservation> events = new CopyOnWriteArrayList<>();
        HostRepositories.Access h = host(clock, events);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> load(h, u, RepositoryCacheRequest.metadata()).text()));
            }
            for (Future<String> result : results) eq("conc data", result.get(20, TimeUnit.SECONDS), "concurrent text");
        } finally {
            pool.shutdownNow();
        }
        try (var files = Files.list(cache())) {
            check(files.noneMatch(p -> p.getFileName().toString().startsWith(".content-")), "no partial content temp files");
        }
        try (var files = Files.list(cache().resolve("metadata-v2"))) {
            check(files.noneMatch(p -> p.getFileName().toString().startsWith(".metadata-")), "no partial metadata temp files");
        }
        eq("conc data", loadOffline(host(clock, events), u).text(), "concurrent result is offline reusable");
        System.out.println("PROBE_PASS 06 concurrency-atomic-metadata/content");
        passed++;
    }

    static void t07LegacyOfflineOnlyAndNoCorruptV2Fallback() throws Exception {
        responses.put("/legacy", new Resp(200, "legacy text", Map.of()));
        String u = url("/legacy");
        TestClock clock = new TestClock(T0);
        List<RepositoryCacheObservation> events = new CopyOnWriteArrayList<>();
        HostRepositories.Access h = host(clock, events);
        LoadedSource first = load(h, u, RepositoryCacheRequest.metadata());
        Path v2 = metadata(cache(), u);
        String v2xml = Files.readString(v2);
        String v1xml = v2xml.replace("version=\"2\"", "version=\"1\"")
                .replaceAll("\\s*downloaded-at=\"[^\"]*\"", "");
        Files.createDirectories(cache().resolve("metadata-v1"));
        Files.writeString(cache().resolve("metadata-v1").resolve(v2.getFileName()), v1xml);
        Files.delete(v2);
        int before = mark();
        eq("legacy text", loadOffline(h, u).text(), "v1 offline fallback");
        eq(before, mark(), "v1 offline fallback never contacts the network");
        eq("legacy text", load(h, u, RepositoryCacheRequest.metadata()).text(), "online ignores v1");
        eq(before + 1, mark(), "online performs one new download to publish v2");
        check(Files.exists(v2), "v2 republished after online download");
        check(Files.exists(cache().resolve("metadata-v1").resolve(v2.getFileName())), "old v1 association is not deleted");
        Files.writeString(v2, "corrupt");
        SourceLoadException offline = fails(() -> loadOffline(h, u));
        eq(SourceLoadException.Reason.OFFLINE, offline.reason(), "corrupt v2 never falls back to v1");
        System.out.println("PROBE_PASS 07 v1-offline-only/republish/no-corrupt-v2-fallback");
        passed++;
    }

    static void t08NoStoreCannotResurrectLegacyEntry() throws Exception {
        responses.put("/nostore", new Resp(200, "secret", Map.of("Cache-Control", "no-store")));
        String u = url("/nostore");
        String digest = ContentDigest.sha256("secret".getBytes(StandardCharsets.UTF_8)).value();
        Files.createDirectories(cache());
        Files.writeString(cache().resolve(digest), "secret");
        writeV1(cache(), u, digest);
        Path v1 = cache().resolve("metadata-v1").resolve(ContentDigest.sha256(u.getBytes(StandardCharsets.UTF_8)).value() + ".xml");
        check(Files.exists(v1), "v1 fixture exists");
        TestClock clock = new TestClock(T0);
        HostRepositories.Access h = host(clock, new CopyOnWriteArrayList<>());
        eq("secret", load(h, u, RepositoryCacheRequest.unconditional()).text(), "no-store download succeeds");
        check(!Files.exists(metadata(cache(), u)), "no-store publishes no v2");
        check(!Files.exists(v1), "no-store removes v1");
        check(Files.exists(cache().resolve(digest)), "no-store keeps old digest content");
        eq(SourceLoadException.Reason.OFFLINE, fails(() -> loadOffline(h, u)).reason(), "no-store content is not an offline hit");
        System.out.println("PROBE_PASS 08 no-store-cannot-resurrect-v1");
        passed++;
    }

    static void t09InjectedClockOwnership() throws Exception {
        responses.put("/clocks", new Resp(200, "clock text", Map.of()));
        String u = url("/clocks");
        int start = mark();
        TestClock early = new TestClock(T0);
        eq("clock text", load(host(early, new CopyOnWriteArrayList<>()), u, RepositoryCacheRequest.metadata()).text(), "download with early clock");
        eq(1, since(start).size(), "first download");
        TestClock oneHourLater = new TestClock(T0.plus(Duration.ofHours(1)));
        eq("clock text", load(host(oneHourLater, new CopyOnWriteArrayList<>()), u, RepositoryCacheRequest.metadata()).text(), "fresh under injected clock");
        eq(1, since(start).size(), "injected clock, not the real system date, decides freshness");
        TestClock beyond = new TestClock(T0.plus(Duration.ofHours(25)));
        load(host(beyond, new CopyOnWriteArrayList<>()), u, RepositoryCacheRequest.metadata());
        eq(2, since(start).size(), "another host clock can expire the same entry");
        System.out.println("PROBE_PASS 09 explicit-clock-ownership");
        passed++;
    }

    static void t10WriteFailureWarnsOnceAndLoadsStillWork() throws Exception {
        responses.put("/writefail", new Resp(200, "write text", Map.of()));
        String u = url("/writefail");
        Path blocked = dir.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        List<RepositoryCacheObservation> events = new CopyOnWriteArrayList<>();
        HostRepositories.Access h = HostRepositories.createObserved(dir, RepositoryCacheConfiguration.at(blocked),
                events::add, new RepositoryProxyConfiguration.Direct(), new TestClock(T0));
        eq("write text", load(h, u, RepositoryCacheRequest.metadata()).text(), "first load despite failed writes");
        eq("write text", load(h, u, RepositoryCacheRequest.metadata()).text(), "second load despite failed writes");
        eq(1L, events.stream().filter(e -> e.event() == RepositoryCacheEvent.WRITE_UNAVAILABLE).count(), "one write warning per host access");
        System.out.println("PROBE_PASS 10 write-failure-warns-once");
        passed++;
    }

    static void t11RequestContract() {
        check(RepositoryCacheRequest.metadata().maxAge().equals(Duration.ofDays(1)), "metadata 24h");
        check(RepositoryCacheRequest.metadata().expectedMd5().isEmpty(), "metadata has no MD5");
        check(RepositoryCacheRequest.model(Optional.of("x")).maxAge().equals(Duration.ofDays(7)), "model 7d");
        check(RepositoryCacheRequest.model(Optional.of("x")).expectedMd5().orElseThrow().equals("x"), "model MD5 kept");
        check(RepositoryCacheRequest.unconditional().maxAge().isZero(), "unconditional zero");
        check(RepositoryCacheRequest.unconditional().expectedMd5().isEmpty(), "unconditional no MD5");
        try { new RepositoryCacheRequest(null, Optional.empty()); throw new AssertionError("null age"); }
        catch (NullPointerException expected) { }
        try { new RepositoryCacheRequest(Duration.ZERO, null); throw new AssertionError("null md5"); }
        catch (NullPointerException expected) { }
        try { new RepositoryCacheRequest(Duration.ofNanos(-1), Optional.empty()); throw new AssertionError("negative age"); }
        catch (IllegalArgumentException expected) { }
        try { RepositoryCacheRequest.model(null); throw new AssertionError("null model md5"); }
        catch (NullPointerException expected) { }
        System.out.println("PROBE_PASS 11 request-immutable/null-free/factories");
        passed++;
    }

    static void t12CancellationIsTypedAndPreventsRequests() throws Exception {
        responses.put("/cancel", new Resp(200, "cancel", Map.of()));
        String u = url("/cancel");
        TestClock clock = new TestClock(T0);
        HostRepositories.Access h = host(clock, new CopyOnWriteArrayList<>());
        load(h, u, RepositoryCacheRequest.metadata());
        int start = mark();
        try {
            h.loader().load(new SourceLocator(u), ONLINE, RepositoryCacheRequest.metadata(),
                    CancellationToken.requested(guru.interlis.execution.CancellationReason.USER_REQUESTED));
            throw new AssertionError("expected cancellation");
        } catch (guru.interlis.execution.OperationCancelledException expected) {
            eq(guru.interlis.execution.CancellationReason.USER_REQUESTED, expected.reason(), "cancellation reason");
        }
        eq(start, mark(), "cancellation prevents any network request");
        System.out.println("PROBE_PASS 12 cancellation-typed/no-request");
        passed++;
    }

    static void t13CorruptLocalContentIsANetworkMiss() throws Exception {
        responses.put("/corrupt", new Resp(200, "good bytes", Map.of()));
        String u = url("/corrupt");
        TestClock clock = new TestClock(T0);
        HostRepositories.Access h = host(clock, new CopyOnWriteArrayList<>());
        LoadedSource first = load(h, u, RepositoryCacheRequest.metadata());
        Files.writeString(cache().resolve(first.byteSha256()), "tampered");
        int start = mark();
        eq("good bytes", load(h, u, RepositoryCacheRequest.metadata()).text(), "corrupt local content is refreshed");
        eq(1, since(start).size(), "corrupt local content causes exactly one download");
        System.out.println("PROBE_PASS 13 corrupt-local-content-refresh");
        passed++;
    }

    public static void main(String[] args) throws Exception {
        dir = Paths.get(args.length > 0 ? args[0] : System.getProperty("probe.dir")).toAbsolutePath();
        Files.createDirectories(dir);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        server.createContext("/", CacheProbe::handle);
        server.start();
        try {
            t01BoundaryNoRenewalFutureAndEpoch();
            t02Md5BeforeDownload();
            t03GenericZeroAndNoConditionalHeaders();
            t04NotFoundInvalidatesBothButKeepsContent();
            t05Unsolicited304IsIoFailureWithoutRetry();
            t06ConcurrentReadersPublishAtomicEntries();
            t07LegacyOfflineOnlyAndNoCorruptV2Fallback();
            t08NoStoreCannotResurrectLegacyEntry();
            t09InjectedClockOwnership();
            t10WriteFailureWarnsOnceAndLoadsStillWork();
            t11RequestContract();
            t12CancellationIsTypedAndPreventsRequests();
            t13CorruptLocalContentIsANetworkMiss();
            System.out.println("CACHE_PROBE_PASS " + passed);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("CACHE_PROBE_FAIL after " + passed);
            System.exit(1);
        } finally {
            server.stop(0);
        }
    }

    static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(new Req(path, exchange.getRequestHeaders().getFirst("If-None-Match"),
                exchange.getRequestHeaders().getFirst("If-Modified-Since"),
                exchange.getRequestHeaders().getFirst("Accept-Encoding")));
        Resp response = responses.get(path);
        if (response == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        response.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        if (response.status() == 200) {
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        } else {
            exchange.sendResponseHeaders(response.status(), -1);
        }
        exchange.close();
    }
}
