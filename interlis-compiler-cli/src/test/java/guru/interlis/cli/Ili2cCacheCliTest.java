package guru.interlis.cli;

import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Ili2cCacheCliTest {
    @TempDir Path directory;
    final DefaultRepositoryCacheTest fixture = new DefaultRepositoryCacheTest();
    @BeforeEach void setup() { fixture.directory=directory; }
    @AfterEach void stop() { fixture.stop(); }

    @Test void coldLoadsEachInputOnceAndWarmRunUsesNoNetwork() throws Exception {
        String base=fixture.repository();
        var cold=fixture.run("check","Projekt.ili","--repository",base,"--verbose");
        fixture.success(cold);
        assertEquals(2,fixture.requests.get(),cold.err());
        int before=fixture.requests.get();
        var warm=fixture.run("check","Projekt.ili","--repository",base,"--verbose");
        fixture.success(warm); assertEquals(before,fixture.requests.get(),warm.err());
        assertFalse(warm.err().contains("304") || warm.err().contains("ILI-RES-0013"),warm.err());
        assertTrue(warm.err().contains("fresh cache"),warm.err());
    }
}
