package guru.interlis.repository;

import static org.junit.jupiter.api.Assertions.*;

import guru.interlis.source.SourceId;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryCachePathTest {
    @Test void ordinaryPathRetainsItsStructureAndSeparatesCaseAndPorts() {
        assertEquals("https/models.example.ch/transport/~roads.ili",
                key("https://models.example.ch/transport/Roads.ili"));
        assertEquals("https/models.example.ch/transport/roads.ili",
                key("https://models.example.ch/transport/roads.ili"));
        assertEquals("http/models.example.ch~p8443/transport/ilimodels.xml",
                key("http://models.example.ch:8443/transport/ilimodels.xml"));
        assertEquals("http/models.example.ch/transport/ilimodels.xml",
                key("http://models.example.ch/transport/ilimodels.xml"));
    }

    @Test void uncommonUrlsUseSeparateOpaqueKeysWithoutExposingTheirContents() {
        String host = "https://models.example.ch/";
        var keys = new HashSet<String>();
        for (String url : List.of(
                host + "Roads.ili?key=secret",
                host + "Roads.ili?key=other",
                "https://user:secret@models.example.ch/Roads.ili",
                "https://Models.example.ch/Roads.ili",
                "https://models.example.ch:0443/Roads.ili",
                host + "Roads.ili#test",
                host + "Roads.ili/",
                host + "Roads%2Eili",
                host + "a//Roads.ili",
                host + "a/../Roads.ili",
                host + "aux.ili",
                host + ".current.xml",
                host + "trailing.",
                host + "a".repeat(200) + ".ili")) {
            String key = key(url);
            assertTrue(key.matches("_opaque/[0-9a-f]{64}"), url);
            assertTrue(keys.add(key), url);
        }
    }

    private static String key(String url) {
        return RepositoryCachePath.key(new SourceId(url));
    }
}
