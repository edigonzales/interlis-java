package guru.interlis.verification;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VerificationLayoutTest {
    @TempDir Path temporary;
    @Test void explicitRootAndLaunchersPreserveUnicodeAndSpaces() throws Exception {
        var root = Files.createDirectories(temporary.resolve("project 😀 space"));
        var layout = new VerificationLayout(root.resolve("x/.."));
        assertEquals(root, layout.repositoryRoot());
        assertEquals(root.resolve("interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler.bat"), layout.compilerLauncher(true));
        assertEquals(root.resolve("interlis-verification/build/test-results/test"), layout.verificationUnitReports());
        assertEquals(root.resolve("interlis-verification/build/test-results/contractTest"), layout.verificationContractReports());
        assertEquals(root.resolve("build/reports/sdk/result.json"), layout.rootReport("sdk/result.json"));
        for (String bad : new String[]{"..", "/tmp", "a/b", "C:\\x", ""}) {
            assertThrows(IllegalArgumentException.class, () -> layout.junitDirectory(bad, "test"));
            assertThrows(IllegalArgumentException.class, () -> layout.junitDirectory("module", bad));
        }
        for (String bad : new String[]{"../x", "/tmp/x", "C:\\x", ""})
            assertThrows(IllegalArgumentException.class, () -> layout.rootReport(bad));
        var reports = Files.createDirectories(root.resolve("build/reports"));
        Files.createSymbolicLink(reports.resolve("escape"), temporary);
        assertThrows(IllegalArgumentException.class, () -> layout.rootReport("escape/result.json"));
    }
    @Test void allEntrypointsAndChildMainsResolveOnTheNewRuntimeClasspath() throws Exception {
        for (String name : new String[]{"HostChecks", "ProfileFingerprint", "CompilerCliProcesses", "SdkProcesses",
                "SdkArchiveCheck", "PlatformProcesses", "ConformanceEvidence", "CorpusExpectations", "GeoCorpus",
                "FullCorpus", "FullCorpusRun", "FullCorpusCold", "JvmBenchmark", "EditorPressure", "ObservationPressure",
                "ReferenceProcesses", "RepositoryReferenceCheck", "SemanticComparison", "PythonFreeCheck"}) {
            var type = Class.forName("guru.interlis.verification." + name);
            if (!java.util.Set.of("HostChecks", "ProfileFingerprint", "CorpusExpectations").contains(name))
                assertEquals(void.class, type.getMethod("main", String[].class).getReturnType());
        }
        var child = HostChecks.run(temporary, java.util.List.of(HostChecks.java().toString(), "-cp",
                System.getProperty("java.class.path"), "guru.interlis.verification.FullCorpusCold"), java.time.Duration.ofSeconds(10));
        assertNotEquals(0, child.exitCode());
        assertFalse(child.stderr().contains("ClassNotFoundException"), child.stderr());
        assertFalse(child.stderr().contains("NoClassDefFoundError"), child.stderr());
    }
}
