package guru.interlis.verification;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ProfileFingerprintTest {
    @TempDir Path temporary;
    @Test void javaAndNodeBindExactlyTheSameInputsIncludingNewSources() throws Exception {
        Path root=Path.of(System.getProperty("repo.root"));
        assertEquals(node(root),ProfileFingerprint.current(root));
        String script=Files.readString(root.resolve("tools/performance/profile.cjs"));
        Path tools=Files.createDirectories(temporary.resolve("tools/performance"));Files.writeString(tools.resolve("profile.cjs"),script);
        assertEquals(0,HostChecks.run(temporary,List.of("git","init"),Duration.ofSeconds(10)).exitCode());
        Path src=Files.createDirectories(temporary.resolve("interlis-verification/src/main/java"));
        for(int i=0;i<501;i++)Files.writeString(src.resolve("Input"+i+".java"),"source"+i);
        Path frontend=Files.createDirectories(temporary.resolve("frontend/diagram-ui"));Files.writeString(frontend.resolve("fixture.js"),"frontend");
        Path input=Files.createDirectories(temporary.resolve("testdata/verification")).resolve("input.json");Files.writeString(input,"{}");
        String before=ProfileFingerprint.current(temporary);assertEquals(node(temporary),before);
        Files.writeString(src.resolve("Input0.java"),"changed");
        assertNotEquals(before,ProfileFingerprint.current(temporary));assertEquals(node(temporary),ProfileFingerprint.current(temporary));
        assertThrows(Exception.class,()->ProfileFingerprint.current(src));
    }
    private String node(Path root) throws Exception {
        var result=HostChecks.run(root,List.of("node","-e","process.stdout.write(require('./tools/performance/profile.cjs').fingerprint())"),Duration.ofSeconds(20));
        assertEquals(0,result.exitCode(),result.stderr());return result.stdout();
    }
}
