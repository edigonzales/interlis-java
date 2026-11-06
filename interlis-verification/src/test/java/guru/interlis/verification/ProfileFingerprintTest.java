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
        String script=Files.readString(root.resolve("tools/verification/source-fingerprint.cjs"));
        Path tools=Files.createDirectories(temporary.resolve("tools/verification"));Files.writeString(tools.resolve("source-fingerprint.cjs"),script);
        assertEquals(0,HostChecks.run(temporary,List.of("git","init"),Duration.ofSeconds(10)).exitCode());
        Path src=Files.createDirectories(temporary.resolve("interlis-verification/src/main/java"));
        for(int i=0;i<501;i++)Files.writeString(src.resolve("Input"+i+".java"),"source"+i);
        Path frontend=Files.createDirectories(temporary.resolve("frontend/diagram-ui"));Files.writeString(frontend.resolve("fixture.js"),"frontend");
        Path metadata=Files.createDirectories(temporary.resolve("testdata/verification"));
        Files.copy(root.resolve("testdata/verification/source-inputs.json"), metadata.resolve("source-inputs.json"));
        Path input=metadata.resolve("expectation.json");Files.writeString(input,"{}");
        String before=ProfileFingerprint.current(temporary);assertEquals(node(temporary),before);
        Path fixtures=Files.createDirectories(temporary.resolve("testdata/compiler"));
        Path added=fixtures.resolve("new-fixture.ili");
        var mutations=Map.of(added,"INTERLIS 2.4;", input,"{\"expected\":1}",
                src.resolve("NewSource.java"),"new source", tools.resolve("runner.cjs"),"new runner");
        for(var mutation:mutations.entrySet()) {
            Files.writeString(mutation.getKey(),mutation.getValue());
            String after=ProfileFingerprint.current(temporary);
            assertNotEquals(before,after,mutation.getKey().toString());
            assertEquals(node(temporary),after);
            before=after;
        }
        Path report=Files.createDirectories(temporary.resolve("target/reports")).resolve("run.json");
        Files.writeString(report,"{}");
        Files.writeString(temporary.resolve("README.md"),"Usage prose");
        assertEquals(before,ProfileFingerprint.current(temporary));
        assertEquals(node(temporary),before);
        assertThrows(Exception.class,()->ProfileFingerprint.current(src));
    }
    private String node(Path root) throws Exception {
        var result=HostChecks.run(root,List.of("node","-e","process.stdout.write(require('./tools/verification/source-fingerprint.cjs').fingerprint())"),Duration.ofSeconds(20));
        assertEquals(0,result.exitCode(),result.stderr());return result.stdout();
    }
}
