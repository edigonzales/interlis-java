package guru.interlis.repository;

import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResolutionLockContractTest {
    @TempDir Path directory;
    @Test void codecRoundTripIsCanonicalAndRejectsDuplicateOrUnknownInputs() {
        var codec=new ResolutionLockCodec();var digest=ContentDigest.sha256(new byte[0]);
        var a=new ModelPin("A","version<&\"\n1",new SourceId("https://example.org/a?x=1&y=2"),digest);
        var b=new ModelPin("B","1",new SourceId("file:///tmp/b.ili"),digest);
        var lock=new ResolutionLock(Map.of("B",List.of(b),"A",List.of(a)),Map.of(new SourceId("https://example.org/ilimodels.xml"),digest));
        String encoded=codec.encode(lock);assertEquals(lock,codec.decode(encoded));
        assertEquals(encoded,codec.encode(new ResolutionLock(new TreeMap<>(lock.models()),new LinkedHashMap<>(lock.indexes()))));
        assertThrows(IllegalArgumentException.class,()->codec.decode(encoded.replace("version=\"1\"", "version=\"99\"")));
        assertThrows(IllegalArgumentException.class,()->codec.decode(encoded.replace("</interlis-resolution-lock>","<unknown/></interlis-resolution-lock>")));
        String duplicate="<interlis-resolution-lock version=\"1\"><model name=\"A\" version=\"1\" source=\"file:/a\" sha256=\""+digest.value()+"\"/><model name=\"A\" version=\"1\" source=\"file:/a\" sha256=\""+digest.value()+"\"/></interlis-resolution-lock>";
        assertThrows(IllegalArgumentException.class,()->codec.decode(duplicate));
        assertThrows(IllegalArgumentException.class,()->codec.decode("<!DOCTYPE x [<!ENTITY remote SYSTEM 'file:///private/unknown'>]><interlis-resolution-lock version=\"1\">&remote;</interlis-resolution-lock>"));
    }
    @Test void persistentCacheVerifiesExactBytesAcrossInstancesAndTreatsCorruptionAsMiss() throws Exception {
        byte[] bytes="INTERLIS 2.4;\r\n!! ä\n".getBytes(StandardCharsets.UTF_8);var digest=ContentDigest.sha256(bytes);
        var cache=new DirectoryContentStore(directory);cache.put(new VerifiedContent(new SourceId("https://example.org/a.ili"),digest,bytes));
        assertArrayEquals(bytes,new DirectoryContentStore(directory).lookup(digest).orElseThrow().bytes());
        Files.writeString(directory.resolve(digest.value()),"tampered");assertTrue(cache.lookup(digest).isEmpty());
        cache.put(new VerifiedContent(new SourceId("file:///a.ili"),digest,bytes));assertTrue(new DirectoryContentStore(directory,1).lookup(digest).isEmpty());
        var file=directory.resolve("not-a-directory");Files.writeString(file,"x");
        var unavailable=new DirectoryContentStore(file);assertDoesNotThrow(()->unavailable.put(new VerifiedContent(new SourceId("file:///a.ili"),digest,bytes)));
        assertTrue(unavailable.lookup(digest).isEmpty());
    }
}
