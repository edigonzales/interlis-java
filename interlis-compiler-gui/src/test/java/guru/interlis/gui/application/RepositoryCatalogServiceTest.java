package guru.interlis.gui.application;

import com.sun.net.httpserver.HttpServer;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositorySource;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryCatalogServiceTest {
    @TempDir
    Path directory;
    private final RepositoryCatalogService browser = new RepositoryCatalogService();

    @Test
    void fullIndexRetainsVersionsBrowseOnlyAndOccurrencesAlongsideIsolatedFailures() throws Exception {
        var valid = local("valid", index(entry("Same", "old.ili", "not-semver", "", false)
                + entry("Same", "new.ili", "next", "not-semver", false)
                + entry("Same", "new.ili", "next", "not-semver", false)
                + entry("Hidden", "hidden.ili", "release", "", true)));
        var empty = local("empty", "<TRANSFER><RepositoryIndex/></TRANSFER>");
        var invalid = local("bad", index(entry("Escapes", "../elsewhere.ili", "1", "", false)));
        var unavailable = browser.repository(Files.createDirectory(directory.resolve("missing")).toString(), directory,
                CancellationToken.none());
        var inputs = List.of(valid, empty, invalid, unavailable);
        var catalogs = browser.load(inputs, options(inputs, false, RepositoryCacheConfiguration.disabled()), directory, 7, CancellationToken.none());
        assertEquals(List.of(RepositoryIndexSnapshot.State.LOADED, RepositoryIndexSnapshot.State.EMPTY,
                RepositoryIndexSnapshot.State.INVALID, RepositoryIndexSnapshot.State.UNAVAILABLE), catalogs.stream().map(RepositoryIndexSnapshot::state).toList());
        var rows = catalogs.getFirst().entries();
        assertEquals(List.of("not-semver", "next", "next", "release"), rows.stream().map(row -> row.metadata().version()).toList());
        assertEquals(rows.get(1).metadata(), rows.get(2).metadata());
        assertNotEquals(rows.get(1).id(), rows.get(2).id());
        assertTrue(rows.getLast().metadata().browseOnly());
        assertEquals("not-semver", rows.get(1).metadata().precursorVersion().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> browser.repository(
                "https://secret@example.invalid/repo?token=value", directory, CancellationToken.none()));
    }

    @Test
    void exactRootUsesTheChosenLowerPrioritySourceAndPreservesIdentityFailures() throws Exception {
        var a = local("priority", index(entry("Shared", "A.ili", "same", "", false)));
        var b = local("chosen", index(entry("Shared", "B.ili", "same", "", false)
                + entry("Shared", "OtherVersion.ili", "historic", "", false)
                + entry("Browse", "Browse.ili", "1", "", true)));
        Files.writeString(directory.resolve("priority/A.ili"), model("Shared", "same", "DOMAIN WinnerA = TEXT*20;"));
        Files.writeString(directory.resolve("chosen/B.ili"), model("Shared", "same", "DOMAIN WinnerB = TEXT*20;"));
        Files.writeString(directory.resolve("chosen/OtherVersion.ili"), model("Shared", "historic", ""));
        Files.writeString(directory.resolve("chosen/Browse.ili"), model("Browse", "1", ""));
        var options = options(List.of(a, b), false, RepositoryCacheConfiguration.disabled());
        var rows = browser.load(List.of(b), options, directory, 2, CancellationToken.none()).getFirst().entries();
        var chosen = rows.getFirst();
        var preview = browser.preview(chosen, options, directory, 99, CancellationToken.none());
        assertTrue(preview.source().text().contains("WinnerB"));
        var result = compile(chosen, options);
        assertInstanceOf(CompilationOutcome.Succeeded.class, assertInstanceOf(CompileResult.Compiled.class, result.outcome()).result());
        var actual = result.capture().orElseThrow().resolution().rootLocations().getFirst();
        assertEquals(chosen.metadata().location().toString(), actual.source().value());
        assertEquals("same", actual.modelVersion().orElseThrow());
        assertNotEquals(preview.source().revision(), result.sources().get(actual.source()).source().revision());
        assertFalse(result.sources().keySet().stream().anyMatch(source -> source.value().endsWith("A.ili")));
        assertInstanceOf(CompilationOutcome.Succeeded.class, assertInstanceOf(CompileResult.Compiled.class,
                compile(rows.get(1), options).outcome()).result());
        assertInstanceOf(CompileResult.RepositoryRejected.class, compile(rows.getLast(), options).outcome());
        Files.writeString(directory.resolve("chosen/B.ili"), model("Shared", "changed", ""));
        assertInstanceOf(CompileResult.RepositoryRejected.class, compile(chosen, options).outcome());
        assertTrue(preview.source().text().contains("WinnerB"));
    }

    @Test
    void offlineLoadPreviewAndCompileUseWarmCacheWithoutAnyRequestAndColdCacheFails() throws Exception {
        var hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            String body = exchange.getRequestURI().getPath().endsWith("ilimodels.xml")
                    ? index(entry("Remote", "Remote.ili", "1", "", false)) : model("Remote", "1", "");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            var repo = browser.repository("http://127.0.0.1:" + server.getAddress().getPort() + "/", directory, CancellationToken.none());
            var active = List.of(repo);
            var cache = RepositoryCacheConfiguration.at(directory.resolve("warm"));
            var online = options(active, false, cache);
            var model = browser.load(active, online, directory, 1, CancellationToken.none()).getFirst().entries().getFirst();
            browser.preview(model, online, directory, 2, CancellationToken.none());
            int onlineRequests = hits.get();
            assertEquals(2, onlineRequests);
            var offline = options(active, true, cache);
            var cached = browser.load(active, offline, directory, 3, CancellationToken.none()).getFirst();
            assertEquals(RepositoryIndexSnapshot.State.LOADED, cached.state());
            assertTrue(browser.preview(cached.entries().getFirst(), offline, directory, 4, CancellationToken.none())
                    .source().text().contains("MODEL Remote"));
            assertInstanceOf(CompilationOutcome.Succeeded.class, assertInstanceOf(CompileResult.Compiled.class,
                    compile(cached.entries().getFirst(), offline).outcome()).result());

            var cold = options(active, true, RepositoryCacheConfiguration.at(directory.resolve("cold")));
            assertEquals(RepositoryIndexSnapshot.State.OFFLINE_UNAVAILABLE,
                    browser.load(active, cold, directory, 5, CancellationToken.none()).getFirst().state());
            assertEquals(SourceLoadException.Reason.OFFLINE, assertThrows(SourceLoadException.class,
                    () -> browser.preview(model, cold, directory, 6, CancellationToken.none())).reason());
            assertEquals(onlineRequests, hits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancellationStopsAnActualIndexRequest() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cancelled = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var repository = browser.repository("http://127.0.0.1:" + server.getAddress().getPort(), directory,
                    CancellationToken.none());
            var options = options(List.of(repository), false, RepositoryCacheConfiguration.disabled());
            var loading = worker.submit(() -> browser.load(List.of(repository), options, directory, 1, cancelled::get));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cancelled.set(true);
            assertInstanceOf(OperationCancelledException.class,
                    assertThrows(ExecutionException.class, () -> loading.get(5, TimeUnit.SECONDS)).getCause());
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void timeoutOfOneRepositoryDoesNotEraseAnotherIndex() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var delayed = browser.repository("http://127.0.0.1:" + server.getAddress().getPort(), directory,
                    CancellationToken.none());
            var healthy = local("healthy", index(entry("Good", "Good.ili", "1", "", false)));
            var sources = List.of(delayed, healthy);
            var defaults = SessionOptions.defaults();
            var limits = FetchPolicy.defaults();
            var policy = new FetchPolicy(false, limits.maxBytes(), limits.connectTimeout(),
                    java.time.Duration.ofSeconds(1), limits.requestTimeout());
            var options = new SessionOptions(new RepositoryConfig(sources, policy), defaults.cache(),
                    defaults.proxy(), defaults.warnings(), defaults.diagnosticLanguage());
            var catalogs = browser.load(sources, options, directory, 1, CancellationToken.none());
            assertEquals(0, entered.getCount());
            assertEquals(RepositoryIndexSnapshot.State.UNAVAILABLE, catalogs.getFirst().state());
            assertTrue(catalogs.getFirst().problem().orElseThrow().contains("timed out"));
            assertEquals(RepositoryIndexSnapshot.State.LOADED, catalogs.getLast().state());
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    private CompileResult compile(RepositoryIndexSnapshot.Entry chosen, SessionOptions options) {
        var entry = chosen.metadata();
        var root = new RootChoice.Repository(entry.name(), entry.version(), new SourceId(entry.location().toString()),
                chosen.id().repository());
        return new CompileService().compile(new CompileRequest(RunContextFactory.defaults().createRoot(), 1,
                new SessionInputs(List.of(), List.of(root)), options, directory), CancellationToken.none());
    }

    private RepositorySource local(String name, String contents) throws Exception {
        Path path = Files.createDirectory(directory.resolve(name));
        Files.writeString(path.resolve("ilimodels.xml"), contents);
        return browser.repository(path.toString(), directory, CancellationToken.none());
    }

    private static SessionOptions options(List<RepositorySource> sources, boolean offline, RepositoryCacheConfiguration cache) {
        var defaults = SessionOptions.defaults();
        var fetch = FetchPolicy.defaults();
        return new SessionOptions(new RepositoryConfig(sources, new FetchPolicy(offline, fetch.maxBytes(),
                fetch.connectTimeout(), fetch.readTimeout(), fetch.requestTimeout())),
                cache, defaults.proxy(), defaults.warnings(), defaults.diagnosticLanguage());
    }

    static String index(String entries) {
        return "<TRANSFER>" + entries + "</TRANSFER>";
    }

    static String entry(String name, String file, String version, String precursor, boolean browseOnly) {
        return "<ModelMetadata><Name>" + name + "</Name><SchemaLanguage>ili2_4</SchemaLanguage><File>" + file
                + "</File><Version>" + version + "</Version>" + (precursor.isEmpty() ? "" : "<precursorVersion>" + precursor + "</precursorVersion>")
                + "<browseOnly>" + browseOnly + "</browseOnly></ModelMetadata>";
    }

    static String model(String name, String version, String body) {
        return "INTERLIS 2.4; MODEL " + name + " AT \"https://example.invalid\" VERSION \"" + version
                + "\" = " + body + " END " + name + ".";
    }
}
