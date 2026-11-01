package guru.interlis.cli;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryLogOrderTest {
    @TempDir Path directory;
    final DefaultRepositoryCacheTest fixture = new DefaultRepositoryCacheTest();
    @BeforeEach void setup() { fixture.directory = directory; }
    @AfterEach void stop() { fixture.stop(); }

    @Test void allCommandsStreamCurrentLookupSelectionAndVerificationInActualAccessOrder() throws Exception {
        String base = fixture.repository();
        for (String command : List.of("check", "compile", "analyze", "diagram")) {
            var run = fixture.run(command, "Projekt.ili", "--repository", base, "--verbose");
            fixture.success(run);
            var lines = run.err().lines().filter(s -> s.startsWith("INFO ")).toList();
            assertEquals(7, lines.size(), run.err());
            assertTrue(lines.get(0).startsWith("INFO [ILI-RES-0011] Model lookup: Basis @ " + base), run.err());
            assertTrue(lines.get(1).contains("[ILI-RES-0010]") && lines.get(1).contains(base + "ilimodels.xml"), run.err());
            assertTrue(lines.get(2).startsWith("INFO [ILI-RES-0012] Model selected: Basis "), run.err());
            assertTrue(lines.get(3).contains("[ILI-RES-0010]") && lines.get(3).contains(base + "Basis.ili"), run.err());
            assertTrue(lines.get(4).startsWith("INFO [ILI-RES-0013] "), run.err());
            assertTrue(lines.subList(5, 7).stream().allMatch(s -> s.contains("[ILI-RES-0010]") && s.contains("HTTP 304")), run.err());
            assertEquals(2, fixture.bodies.get(), "Vary: Accept-Encoding must not cause further body transfers");
        }
    }

    @Test void lockHistoryIsNotReplayedOfflineAndFailuresRetainEarlierProgress() throws Exception {
        String base = fixture.repository();
        fixture.success(fixture.run("check", "Projekt.ili", "--repository", base, "--write-lock", "project.lock"));
        int requests = fixture.requests.get();
        for (boolean lock : List.of(false, true)) {
            var args = new ArrayList<>(List.of("check", "Projekt.ili", "--repository", base, "--offline", "--verbose"));
            if (lock) args.addAll(List.of("--read-lock", "project.lock"));
            var run = fixture.run(args.toArray(String[]::new)); fixture.success(run);
            assertFalse(run.err().contains("ILI-RES-0013"), run.err());
            if (lock) assertFalse(run.err().contains("ILI-RES-0011") || run.err().contains("ILI-RES-0012"), run.err());
        }
        assertEquals(requests, fixture.requests.get());
        var missing = fixture.run("check", "--root", "Absent", "--repository", base, "--verbose");
        assertEquals(CliTermination.INPUT_UNRESOLVED, missing.result().termination());
        assertTrue(missing.err().indexOf("INFO [ILI-RES-0011]") >= 0, missing.err());
        assertTrue(missing.err().indexOf("INFO [ILI-RES-0011]") < missing.err().indexOf("ERROR ["), missing.err());
        Files.writeString(directory.resolve("Projekt.ili"), "INTERLIS 2.4; MODEL Projekt AT \"x\" VERSION \"1\" = IMPORTS Basis; DOMAIN D = Missing; END Projekt.");
        var bad = fixture.run("check", "Projekt.ili", "--repository", base, "--verbose");
        assertEquals(CliTermination.SOURCE_REJECTED, bad.result().termination());
        assertTrue(bad.err().contains("INFO [ILI-RES-0011]"), bad.err());
        assertTrue(bad.err().indexOf("ILI-RES-0013") < bad.err().indexOf("ERROR ["), bad.err());
    }
}
