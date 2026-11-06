package guru.interlis.cli.conformance;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OriginalViewFixtureContractTest {
    @Test void portableOriginalsMatchPinnedBytesAndAllModelRoots() throws Exception {
        Path root = Path.of(System.getProperty("repo.root"));
        Path fixtures = root.resolve("testdata/conformance/views");
        var pins = new LinkedHashMap<String, String[]>();
        for (String line : Files.readAllLines(fixtures.resolve("sources.properties"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] pair = line.split("=", 2); assertNull(pins.put(pair[0], pair[1].split("\\|", 3)));
        }
        var cases = root.resolve("interlis-wasm-teavm/contracts/cases/original-views.json");
        var entries = JsonParser.parseString(Files.readString(cases)).getAsJsonArray();
        assertEquals(19, pins.size()); assertEquals(19, entries.size()); var seen = new HashSet<String>();
        for (var value : entries) {
            var entry = value.getAsJsonObject(); String id = entry.get("testId").getAsString(); assertTrue(seen.add(id));
            var pin = pins.get(id); assertNotNull(pin); Path file = fixtures.resolve(pin[0]);
            assertArrayEquals(Files.readAllBytes(file), entry.get("text").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(pin[1], NormativeConformance.hash(file)); assertEquals(pin[1], entry.get("sha256").getAsString());
            var matcher = Pattern.compile("^\\s*(?:CONTRACTED\\s+)?MODEL\\s+([A-Za-z_]\\w*)\\b", Pattern.MULTILINE).matcher(Files.readString(file));
            var roots = new ArrayList<String>(); while (matcher.find()) roots.add(matcher.group(1)); assertFalse(roots.isEmpty());
            assertEquals(roots, NormativeConformance.strings(entry.getAsJsonArray("roots")));
        }
        assertEquals(pins.keySet(), seen);
    }
}
