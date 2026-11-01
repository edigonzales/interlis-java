package guru.interlis.repository;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.repository.RepositoryCacheContractTest.*;

class HttpIdentityCacheTest {
    @TempDir Path directory;
    final RepositoryCacheContractTest fixture = new RepositoryCacheContractTest();
    @BeforeEach void setup() { fixture.directory = directory; }
    @AfterEach void stop() { fixture.stop(); }

    @Test void identityVaryRevalidatesAcrossInstancesAndOfflineWithoutMoreBodies() throws Exception {
        var encodings = new ArrayList<String>();
        String url = fixture.serve(e -> {
            encodings.add(e.getRequestHeaders().getFirst("Accept-Encoding"));
            e.getResponseHeaders().add("Vary", " aCcEpT-EnCoDiNg, Accept-Encoding ");
            e.getResponseHeaders().add("Vary", "Accept-Encoding");
            e.getResponseHeaders().set("Content-Encoding", "identity");
            fixture.send(e, "original", "\"one\"");
        }) + "model.ili";
        fixture.load(fixture.host(), url, ONLINE);
        var events = new ArrayList<RepositoryCacheEvent>();
        assertEquals("original", fixture.load(fixture.host(events), url, ONLINE).text());
        assertEquals(List.of(RepositoryCacheEvent.FRESH_HIT), events);
        assertEquals(List.of("identity"), encodings);
        assertEquals(1, fixture.bodies.get());
        int requests = fixture.requests.get();
        assertEquals("original", fixture.load(fixture.host(), url, OFFLINE).text());
        assertEquals(requests, fixture.requests.get());
    }

    @Test void variantsDoNotBlockApplicationCacheAndNoStoreRemainsUncached() throws Exception {
        String base = fixture.serve(e -> {
            e.getResponseHeaders().set("Vary", switch (e.getRequestURI().getPath()) {
                case "/mixed" -> "Accept-Encoding, Accept-Language";
                case "/wildcard" -> "*";
                default -> "Accept-Encoding";
            });
            if (e.getRequestURI().getPath().equals("/private")) e.getResponseHeaders().set("Cache-Control", "no-store");
            fixture.send(e, "variant", "\"one\"");
        });
        for (String path : List.of("mixed", "wildcard", "private")) {
            fixture.load(fixture.host(), base + path, ONLINE);
            if (path.equals("private")) {
                assertFalse(Files.exists(fixture.metadata(base + path)));
                assertThrows(SourceLoadException.class, () -> fixture.load(fixture.host(), base + path, OFFLINE));
            } else assertEquals("variant", fixture.load(fixture.host(), base + path, OFFLINE).text());
        }
    }

    @Test void unexpectedCodingsOnUnconditional200FailBeforePublishingBytes() throws Exception {
        var coding = new AtomicReference<String>();
        String url = fixture.serve(e -> {
            if (coding.get() != null) e.getResponseHeaders().set("Content-Encoding", coding.get());
            fixture.send(e, "still UTF-8 but not actually encoded", "\"one\"");
        }) + "model.ili";
        for (String value : List.of("gzip", "br", "identity, gzip")) {
            coding.set(value);
            assertEquals(SourceLoadException.Reason.IO_FAILURE,
                    assertThrows(SourceLoadException.class, () -> fixture.load(fixture.host(), url, ONLINE)).reason());
            assertFalse(Files.exists(fixture.metadata(url)));
        }
        coding.set(null); var original = fixture.load(fixture.host(), url, ONLINE);
        byte[] metadata = Files.readAllBytes(fixture.metadata(url));
        coding.set("gzip");
        assertEquals(SourceLoadException.Reason.IO_FAILURE,
                assertThrows(SourceLoadException.class, () -> fixture.host().loader().load(new SourceLocator(url), ONLINE, TOKEN)).reason());
        assertArrayEquals(metadata, Files.readAllBytes(fixture.metadata(url)));
        assertEquals(original.byteSha256(), fixture.load(fixture.host(), url, OFFLINE).byteSha256());
    }

    @Test void redirectsAndUnconditionalLoadsAlwaysRequestIdentity() throws Exception {
        var encodings = new ArrayList<String>();
        var invalid304 = new AtomicBoolean();
        String base = fixture.serve(e -> {
            encodings.add(e.getRequestHeaders().getFirst("Accept-Encoding"));
            if (e.getRequestURI().getPath().equals("/redirect")) {
                e.getResponseHeaders().set("Location", "target"); e.sendResponseHeaders(302, -1); e.close();
            } else if (invalid304.get() && e.getRequestHeaders().containsKey("If-None-Match")) {
                e.sendResponseHeaders(304, -1); e.close();
            } else {
                e.getResponseHeaders().set("Vary", "Accept-Encoding"); fixture.send(e, "content", "\"one\"");
            }
        });
        fixture.load(fixture.host(), base + "redirect", ONLINE);
        invalid304.set(true);
        fixture.host().loader().load(new SourceLocator(base + "redirect"), ONLINE, TOKEN);
        assertEquals(4, encodings.size());
        assertTrue(encodings.stream().allMatch("identity"::equals), encodings.toString());
        assertEquals(2, fixture.bodies.get());
    }
}
